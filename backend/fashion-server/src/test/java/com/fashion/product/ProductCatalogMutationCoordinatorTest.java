package com.fashion.product;

import com.fashion.entity.Product;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.mapper.ProductCatalogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductCatalogMutationCoordinatorTest {

    @Test
    void oneCatalogMutationAdvancesOnceAndCreatesRedisAndEsFacts() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        AfterCommitRegistrar registrar = mock(AfterCommitRegistrar.class);
        when(mapper.lockListVersion()).thenReturn(41L);
        when(mapper.advanceListVersion(41L)).thenReturn(1);
        when(mapper.upsertRevision(7L, 42L, "ACTIVE")).thenReturn(1);
        when(mapper.insertProjectionTask(any())).thenReturn(1);
        ProductCatalogMutationCoordinator coordinator = new ProductCatalogMutationCoordinator(
                mapper, new CanonicalProductProjectionCodec(), registrar);

        long version = coordinator.record(product(1), ProductItemState.ACTIVE);

        assertThat(version).isEqualTo(42L);
        verify(mapper).upsertRevision(7L, 42L, "ACTIVE");
        ArgumentCaptor<ProductProjectionTask> tasks = ArgumentCaptor.forClass(ProductProjectionTask.class);
        verify(mapper, times(2)).insertProjectionTask(tasks.capture());
        assertThat(tasks.getAllValues()).extracting(ProductProjectionTask::getTarget)
                .containsExactly("REDIS", "ES");
        assertThat(tasks.getAllValues()).extracting(ProductProjectionTask::getOperation)
                .containsExactly("PUBLISH", "UPSERT");
        assertThat(tasks.getAllValues().get(1).getPayload()).doesNotContain("stock");
        verify(registrar).register(7L, 42L);
    }

    @Test
    void deletedProductKeepsTombstonePayloadForReplay() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        AfterCommitRegistrar registrar = mock(AfterCommitRegistrar.class);
        when(mapper.lockListVersion()).thenReturn(9L);
        when(mapper.advanceListVersion(9L)).thenReturn(1);
        when(mapper.upsertRevision(7L, 10L, "DELETED")).thenReturn(1);
        when(mapper.insertProjectionTask(any())).thenReturn(1);
        ProductCatalogMutationCoordinator coordinator = new ProductCatalogMutationCoordinator(
                mapper, new CanonicalProductProjectionCodec(), registrar);

        coordinator.record(product(0), ProductItemState.DELETED);

        ArgumentCaptor<ProductProjectionTask> tasks = ArgumentCaptor.forClass(ProductProjectionTask.class);
        verify(mapper, times(2)).insertProjectionTask(tasks.capture());
        ProductProjectionTask es = tasks.getAllValues().get(1);
        assertThat(es.getOperation()).isEqualTo("DELETE");
        assertThat(es.getPayload()).contains("\"productId\":7").contains("\"catalogVersion\":10");
    }

    @Test
    void failedCompareAndSetLeavesNoRevisionOrTask() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        when(mapper.lockListVersion()).thenReturn(4L);
        when(mapper.advanceListVersion(4L)).thenReturn(0);
        ProductCatalogMutationCoordinator coordinator = new ProductCatalogMutationCoordinator(
                mapper, new CanonicalProductProjectionCodec(), mock(AfterCommitRegistrar.class));

        assertThatThrownBy(() -> coordinator.record(product(1), ProductItemState.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).upsertRevision(anyLong(), anyLong(), anyString());
        verify(mapper, never()).insertProjectionTask(any());
    }

    private Product product(int status) {
        Product product = new Product();
        product.setId(7L);
        product.setName("coat");
        product.setDescription("warm");
        product.setCategoryId(3L);
        product.setPrice(new BigDecimal("10"));
        product.setImage("coat.jpg");
        product.setTag("new");
        product.setStatus(status);
        product.setStock(5);
        product.setSales(null);
        return product;
    }
}
