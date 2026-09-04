package com.fashion.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.dto.ProductQueryDTO;
import com.fashion.entity.PageResult;
import com.fashion.entity.Product;
import com.fashion.entity.ProductCatalogRevision;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductService;
import com.fashion.utils.CacheClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ProductCatalogCacheService {

    private final ProductService productService;
    private final ProductMapper productMapper;
    private final ProductCatalogVersionGate versionGate;
    private final CacheClient cacheClient;
    private final ProductCacheProperties properties;
    private final ProductCacheTtlPolicy ttlPolicy;
    private final ObjectMapper objectMapper;
    private final Executor detailRebuildExecutor;
    private final ProductProjectionMetrics metrics;

    @Autowired
    public ProductCatalogCacheService(ProductService productService, ProductMapper productMapper,
                                      ProductCatalogVersionGate versionGate, CacheClient cacheClient,
                                      ProductCacheProperties properties, ProductCacheTtlPolicy ttlPolicy,
                                      ObjectMapper objectMapper,
                                      @Qualifier("productCacheRebuildExecutor") Executor detailRebuildExecutor,
                                      ProductProjectionMetrics metrics) {
        this.productService = productService;
        this.productMapper = productMapper;
        this.versionGate = versionGate;
        this.cacheClient = cacheClient;
        this.properties = properties;
        this.ttlPolicy = ttlPolicy;
        this.objectMapper = objectMapper;
        this.detailRebuildExecutor = detailRebuildExecutor;
        this.metrics = metrics;
    }

    ProductCatalogCacheService(ProductService productService, ProductMapper productMapper,
                               ProductCatalogVersionGate versionGate, CacheClient cacheClient,
                               ProductCacheProperties properties, ProductCacheTtlPolicy ttlPolicy,
                               ObjectMapper objectMapper) {
        this(productService, productMapper, versionGate, cacheClient, properties, ttlPolicy,
                objectMapper, Runnable::run, new ProductProjectionMetrics());
    }

    public ProductCatalogCacheService(ProductService productService, ProductMapper productMapper,
                                      ProductCatalogVersionGate versionGate, CacheClient cacheClient,
                                      ProductCacheProperties properties, ProductCacheTtlPolicy ttlPolicy,
                                      ObjectMapper objectMapper, Executor detailRebuildExecutor) {
        this(productService, productMapper, versionGate, cacheClient, properties, ttlPolicy,
                objectMapper, detailRebuildExecutor, new ProductProjectionMetrics());
    }

    public PageResult<Product> page(ProductQueryDTO source) {
        NormalizedProductQuery query = NormalizedProductQuery.forUser(source);
        VersionDecision decision = versionGate.listVersion();
        CachedProductPage cached = null;
        String key = ProductCacheKeys.list(decision.getVersion(), query);
        if (decision.isCacheAllowed()) {
            try {
                String raw = cacheClient.getRaw(key);
                if (raw != null) {
                    cached = objectMapper.readValue(raw, CachedProductPage.class);
                }
            } catch (RuntimeException | JsonProcessingException failure) {
                log.warn("B8 list cache read bypassed: {}", failure.getClass().getSimpleName());
            }
        }
        if (cached == null) {
            PageResult<Product> database = productService.pageProducts(query.toQueryDto());
            cached = CachedProductPage.from(database);
            if (decision.isCacheAllowed()) {
                try {
                    cacheClient.setRaw(key, objectMapper.writeValueAsString(cached),
                            ttlPolicy.withJitter(properties.getListPhysicalTtl(), properties.getActualJitter()));
                } catch (RuntimeException | JsonProcessingException failure) {
                    log.warn("B8 list cache fill failed: {}", failure.getClass().getSimpleName());
                }
            }
        }
        PageResult<Product> result = cached.toPageResult();
        hydrateStock(result.getRecords());
        return result;
    }

    public Product detail(long productId) {
        DetailVersionDecision decision = versionGate.detailVersion(productId);
        ProductCatalogRevision revision = decision.getRevision();
        if (revision == null) {
            Product unexpected = readProduct(productId);
            if (unexpected == null) {
                return null;
            }
            throw new ProductCatalogSourceUnavailableException("product exists without catalog revision");
        }
        long version = revision.getItemVersion();
        String key = ProductCacheKeys.detail(productId, version);
        if (!"ACTIVE".equals(revision.getItemState())) {
            if (decision.isCacheAllowed()) {
                try {
                    cacheClient.setRaw(key, "",
                            ttlPolicy.withJitter(properties.getEmptyPhysicalTtl(), properties.getEmptyJitter()));
                } catch (RuntimeException failure) {
                    log.warn("B8 empty detail cache fill failed: {}", failure.getClass().getSimpleName());
                }
            }
            return null;
        }

        if (decision.isCacheAllowed()) {
            try {
                String raw = cacheClient.getRaw(key);
                if (raw != null && !raw.isEmpty()) {
                    DetailEnvelope envelope = objectMapper.readValue(raw, DetailEnvelope.class);
                    Product result = envelope.getData().toProduct();
                    hydrateStock(Collections.singletonList(result));
                    if (envelope.getLogicalExpireAtEpochMilli() <= System.currentTimeMillis()) {
                        rebuildDetailAsync(productId, revision, key);
                    }
                    return result;
                }
            } catch (RuntimeException | JsonProcessingException failure) {
                log.warn("B8 detail cache read bypassed: {}", failure.getClass().getSimpleName());
            }
        }

        Product database = requireActiveProduct(productId);
        if (decision.isCacheAllowed()) {
            fillDetailWithFence(database, revision, key);
        }
        return database;
    }

    private void rebuildDetailAsync(long productId, ProductCatalogRevision revision, String valueKey) {
        String lockKey = ProductCacheKeys.detailLock(productId, revision.getItemVersion());
        String token;
        try {
            token = cacheClient.tryLockToken(lockKey, properties.getLockTtl());
        } catch (RuntimeException failure) {
            return;
        }
        if (token == null) {
            metrics.increment("cache.detail.rebuild_lock_contended");
            return;
        }
        try {
            detailRebuildExecutor.execute(() -> {
                try {
                    Product fresh = requireActiveProduct(productId);
                    if (writeFenced(fresh, revision, valueKey, lockKey, token)) {
                        metrics.increment("cache.detail.rebuild_success");
                    } else {
                        metrics.increment("cache.detail.rebuild_fence_rejected");
                    }
                } catch (RuntimeException failure) {
                    metrics.increment("cache.detail.rebuild_failure");
                    log.warn("B8 detail rebuild failed for productId={}, type={}",
                            productId, failure.getClass().getSimpleName());
                } finally {
                    safeRelease(lockKey, token);
                }
            });
        } catch (RuntimeException rejected) {
            metrics.increment("cache.detail.rebuild_rejected");
            log.warn("B8 detail rebuild submission failed for productId={}, type={}",
                    productId, rejected.getClass().getSimpleName());
            safeRelease(lockKey, token);
        }
    }

    private void fillDetailWithFence(Product product, ProductCatalogRevision revision, String valueKey) {
        String lockKey = ProductCacheKeys.detailLock(product.getId(), revision.getItemVersion());
        String token = null;
        try {
            token = cacheClient.tryLockToken(lockKey, properties.getLockTtl());
            if (token != null) {
                writeFenced(product, revision, valueKey, lockKey, token);
            }
        } catch (RuntimeException failure) {
            log.warn("B8 detail cache fill failed: {}", failure.getClass().getSimpleName());
        } finally {
            if (token != null) {
                safeRelease(lockKey, token);
            }
        }
    }

    private void safeRelease(String lockKey, String token) {
        try {
            cacheClient.releaseLock(lockKey, token);
        } catch (RuntimeException failure) {
            metrics.increment("cache.detail.lock_release_failure");
            log.warn("B8 detail lock release failed: {}", failure.getClass().getSimpleName());
        }
    }

    private boolean writeFenced(Product product, ProductCatalogRevision revision,
                                String valueKey, String lockKey, String token) {
        try {
            DetailEnvelope envelope = new DetailEnvelope();
            envelope.setData(CachedProduct.from(product));
            envelope.setLogicalExpireAtEpochMilli(System.currentTimeMillis()
                    + properties.getDetailLogicalTtl().toMillis());
            Duration physical = ttlPolicy.withJitter(
                    properties.getDetailPhysicalTtl(), properties.getActualJitter());
            Long result = cacheClient.fencedSet(lockKey,
                    ProductCacheKeys.detailPublishedVersion(product.getId()), valueKey,
                    token, revision.getItemVersion(), objectMapper.writeValueAsString(envelope), physical);
            return Long.valueOf(1L).equals(result);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("product detail cache encoding failed", failure);
        }
    }

    private Product requireActiveProduct(long productId) {
        Product product = readProduct(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new ProductCatalogSourceUnavailableException("ACTIVE revision contradicts product row");
        }
        product.setSales(product.getSales() == null ? 0 : product.getSales());
        return product;
    }

    private Product readProduct(long productId) {
        try {
            return productMapper.getByIdIncludingInactive(productId);
        } catch (RuntimeException failure) {
            throw new ProductCatalogSourceUnavailableException("product source is unavailable", failure);
        }
    }

    private void hydrateStock(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Product product : products) {
            ids.add(product.getId());
        }
        List<Product> stocks;
        try {
            stocks = productMapper.selectStocksByIds(ids);
        } catch (RuntimeException failure) {
            throw new ProductCatalogSourceUnavailableException("product stock source is unavailable", failure);
        }
        Map<Long, Integer> byId = new HashMap<>();
        if (stocks != null) {
            for (Product stock : stocks) {
                byId.put(stock.getId(), stock.getStock());
            }
        }
        for (Product product : products) {
            if (!byId.containsKey(product.getId())) {
                throw new ProductCatalogSourceUnavailableException("product stock row is missing");
            }
            product.setStock(byId.get(product.getId()));
        }
    }

    @Data
    public static class CachedProductPage {
        private long total;
        private List<CachedProduct> records = new ArrayList<>();

        static CachedProductPage from(PageResult<Product> source) {
            CachedProductPage result = new CachedProductPage();
            result.total = source == null ? 0 : source.getTotal();
            if (source != null && source.getRecords() != null) {
                for (Product product : source.getRecords()) {
                    result.records.add(CachedProduct.from(product));
                }
            }
            return result;
        }

        PageResult<Product> toPageResult() {
            List<Product> products = new ArrayList<>();
            for (CachedProduct record : records) {
                products.add(record.toProduct());
            }
            return new PageResult<>(total, products);
        }
    }

    @Data
    public static class DetailEnvelope {
        private CachedProduct data;
        private long logicalExpireAtEpochMilli;
    }

    @Data
    public static class CachedProduct {
        private Long id;
        private String name;
        private Long categoryId;
        private BigDecimal price;
        private String image;
        private String description;
        private Integer status;
        private Integer sales;
        private String tag;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long createUser;
        private Long updateUser;

        static CachedProduct from(Product source) {
            CachedProduct result = new CachedProduct();
            result.id = source.getId();
            result.name = source.getName();
            result.categoryId = source.getCategoryId();
            result.price = source.getPrice();
            result.image = source.getImage();
            result.description = source.getDescription();
            result.status = source.getStatus();
            result.sales = source.getSales() == null ? 0 : source.getSales();
            result.tag = source.getTag();
            result.createTime = source.getCreateTime();
            result.updateTime = source.getUpdateTime();
            result.createUser = source.getCreateUser();
            result.updateUser = source.getUpdateUser();
            return result;
        }

        Product toProduct() {
            Product result = new Product();
            result.setId(id);
            result.setName(name);
            result.setCategoryId(categoryId);
            result.setPrice(price);
            result.setImage(image);
            result.setDescription(description);
            result.setStatus(status);
            result.setSales(sales == null ? 0 : sales);
            result.setTag(tag);
            result.setCreateTime(createTime);
            result.setUpdateTime(updateTime);
            result.setCreateUser(createUser);
            result.setUpdateUser(updateUser);
            return result;
        }
    }
}
