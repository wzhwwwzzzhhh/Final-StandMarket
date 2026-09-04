package com.fashion.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.dto.ProductQueryDTO;
import com.fashion.entity.PageResult;
import com.fashion.entity.Product;
import com.fashion.entity.ProductCatalogRevision;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductService;
import com.fashion.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductCatalogCacheServiceTest {

    @Test
    void listMissWritesVersionedProjectionWithoutStockAndHydratesResponseFromMysql() {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        when(gate.listVersion()).thenReturn(new VersionDecision(12L, true));
        when(cache.getRaw(anyString())).thenReturn(null);
        Product source = product(5, 1);
        when(products.pageProducts(any())).thenReturn(new PageResult<>(1, Collections.singletonList(source)));
        when(mapper.selectStocksByIds(Collections.singletonList(7L)))
                .thenReturn(Collections.singletonList(product(9, 1)));
        ProductCatalogCacheService service = service(products, mapper, gate, cache);

        PageResult<Product> result = service.page(new ProductQueryDTO());

        assertThat(result.getRecords()).singleElement().extracting(Product::getStock).isEqualTo(9);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(cache).setRaw(key.capture(), json.capture(), any(Duration.class));
        assertThat(key.getValue()).startsWith("cache:product:list:v2:12:");
        assertThat(json.getValue()).doesNotContain("\"stock\"");
    }

    @Test
    void cacheBypassNeverTouchesRedisAndStillUsesNormalizedUserQuery() {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        when(gate.listVersion()).thenReturn(new VersionDecision(13L, false));
        when(products.pageProducts(any())).thenReturn(new PageResult<>(0, Collections.emptyList()));

        PageResult<Product> result = service(products, mapper, gate, cache).page(new ProductQueryDTO());

        assertThat(result.getTotal()).isZero();
        verify(cache, never()).getRaw(anyString());
        verify(cache, never()).setRaw(anyString(), anyString(), any());
        ArgumentCaptor<ProductQueryDTO> normalized = ArgumentCaptor.forClass(ProductQueryDTO.class);
        verify(products).pageProducts(normalized.capture());
        assertThat(normalized.getValue().getIsSale()).isTrue();
    }

    @Test
    void inactiveRevisionCachesShortEmptyWhileNeverExistedIdIsNotCached() {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        when(gate.detailVersion(7L)).thenReturn(new DetailVersionDecision(
                revision("INACTIVE", 21L), true));
        ProductCatalogCacheService service = service(products, mapper, gate, cache);

        assertThat(service.detail(7L)).isNull();
        verify(cache).setRaw(eq(ProductCacheKeys.detail(7L, 21L)), eq(""), any(Duration.class));

        reset(gate, cache, mapper);
        when(gate.detailVersion(8L)).thenReturn(new DetailVersionDecision(null, false));
        when(mapper.getByIdIncludingInactive(8L)).thenReturn(null);
        assertThat(service.detail(8L)).isNull();
        verify(cache, never()).setRaw(anyString(), anyString(), any());
    }

    @Test
    void revisionProductContradictionFailsClosedAndDoesNotPopulateCache() {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        when(gate.detailVersion(7L)).thenReturn(new DetailVersionDecision(
                revision("ACTIVE", 22L), true));
        when(mapper.getByIdIncludingInactive(7L)).thenReturn(product(4, 0));

        assertThatThrownBy(() -> service(products, mapper, gate, cache).detail(7L))
                .isInstanceOf(ProductCatalogSourceUnavailableException.class);
        verify(cache, never()).setRaw(anyString(), anyString(), any());
    }

    @Test
    void mysqlDetailStillReturnsWhenRedisReleaseFailsAfterFill() {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        when(gate.detailVersion(7L)).thenReturn(new DetailVersionDecision(
                revision("ACTIVE", 23L), true));
        when(cache.getRaw(anyString())).thenReturn(null);
        when(cache.tryLockToken(anyString(), any(Duration.class))).thenReturn("owner-token");
        when(mapper.getByIdIncludingInactive(7L)).thenReturn(product(4, 1));
        when(mapper.selectStocksByIds(Collections.singletonList(7L)))
                .thenReturn(Collections.singletonList(product(4, 1)));
        doThrow(new IllegalStateException("redis unavailable during release"))
                .when(cache).releaseLock(anyString(), eq("owner-token"));

        Product result = service(products, mapper, gate, cache).detail(7L);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getStock()).isEqualTo(4);
    }

    @Test
    void rejectedAsyncRebuildSafelyReleasesOwnedLock() throws Exception {
        ProductService products = mock(ProductService.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogVersionGate gate = mock(ProductCatalogVersionGate.class);
        CacheClient cache = mock(CacheClient.class);
        ProductCatalogRevision revision = revision("ACTIVE", 24L);
        when(gate.detailVersion(7L)).thenReturn(new DetailVersionDecision(revision, true));
        ProductCatalogCacheService.DetailEnvelope stale = new ProductCatalogCacheService.DetailEnvelope();
        stale.setData(ProductCatalogCacheService.CachedProduct.from(product(1, 1)));
        stale.setLogicalExpireAtEpochMilli(1L);
        when(cache.getRaw(anyString())).thenReturn(new ObjectMapper().writeValueAsString(stale));
        when(cache.tryLockToken(anyString(), any(Duration.class))).thenReturn("owner-token");
        when(mapper.getByIdIncludingInactive(7L)).thenReturn(product(5, 1));
        when(mapper.selectStocksByIds(Collections.singletonList(7L)))
                .thenReturn(Collections.singletonList(product(5, 1)));
        ProductCacheProperties properties = new ProductCacheProperties();
        ProductCatalogCacheService service = new ProductCatalogCacheService(
                products, mapper, gate, cache, properties,
                new ProductCacheTtlPolicy(() -> 0L), new ObjectMapper(),
                command -> { throw new RejectedExecutionException("full"); });

        Product result = service.detail(7L);

        assertThat(result.getStock()).isEqualTo(5);
        verify(cache).releaseLock(anyString(), eq("owner-token"));
    }

    private ProductCatalogCacheService service(ProductService products, ProductMapper mapper,
                                               ProductCatalogVersionGate gate, CacheClient cache) {
        ProductCacheProperties properties = new ProductCacheProperties();
        return new ProductCatalogCacheService(products, mapper, gate, cache, properties,
                new ProductCacheTtlPolicy(() -> 0L), new ObjectMapper());
    }

    private Product product(int stock, int status) {
        Product product = new Product();
        product.setId(7L);
        product.setName("coat");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(stock);
        product.setStatus(status);
        product.setSales(0);
        return product;
    }

    private ProductCatalogRevision revision(String state, long version) {
        ProductCatalogRevision revision = new ProductCatalogRevision();
        revision.setProductId(7L);
        revision.setItemState(state);
        revision.setItemVersion(version);
        return revision;
    }
}
