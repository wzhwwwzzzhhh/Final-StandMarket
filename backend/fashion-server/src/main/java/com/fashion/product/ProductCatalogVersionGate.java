package com.fashion.product;

import com.fashion.utils.CacheClient;
import com.fashion.entity.ProductCatalogRevision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Slf4j
public class ProductCatalogVersionGate {

    private static final long MAX_SAFE_VERSION = 9_007_199_254_740_991L;

    private final ProductCatalogAuthority authority;
    private final CacheClient cacheClient;
    private final ProductProjectionMetrics metrics;

    public ProductCatalogVersionGate(ProductCatalogAuthority authority, CacheClient cacheClient) {
        this(authority, cacheClient, new ProductProjectionMetrics());
    }

    @Autowired
    public ProductCatalogVersionGate(ProductCatalogAuthority authority, CacheClient cacheClient,
                                     ProductProjectionMetrics metrics) {
        this.authority = authority;
        this.cacheClient = cacheClient;
        this.metrics = metrics;
    }

    public VersionDecision listVersion() {
        long databaseVersion = readAuthorityVersion();
        try {
            String raw = cacheClient.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION);
            if (raw == null) {
                metrics.increment("cache.list.mirror_missing");
                return publish(databaseVersion);
            }
            Long redisVersion = parseVersion(raw);
            if (redisVersion == null) {
                log.warn("B8 product list version mirror is corrupt; bypassing Redis");
                metrics.increment("cache.list.bypass_corrupt");
                return new VersionDecision(databaseVersion, false);
            }
            if (redisVersion == databaseVersion) {
                return new VersionDecision(databaseVersion, true);
            }
            if (redisVersion < databaseVersion) {
                metrics.increment("cache.list.mirror_behind");
                return publish(databaseVersion);
            }

            long secondRead = readAuthorityVersion();
            if (secondRead == redisVersion) {
                return new VersionDecision(secondRead, true);
            }
            if (secondRead > redisVersion) {
                return publish(secondRead);
            }
            log.warn("B8 product list version mirror is ahead of MySQL; bypassing Redis");
            metrics.increment("cache.list.bypass_ahead");
            return new VersionDecision(secondRead, false);
        } catch (ProductCatalogSourceUnavailableException failure) {
            throw failure;
        } catch (RuntimeException redisFailure) {
            log.warn("B8 product list cache gate degraded to MySQL: {}",
                    redisFailure.getClass().getSimpleName());
            metrics.increment("cache.list.bypass_redis_error");
            return new VersionDecision(databaseVersion, false);
        }
    }

    public DetailVersionDecision detailVersion(long productId) {
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
        ProductCatalogRevision revision = readRevision(productId);
        if (revision == null) {
            return new DetailVersionDecision(null, false);
        }
        long databaseVersion = revision.getItemVersion();
        String mirrorKey = ProductCacheKeys.detailPublishedVersion(productId);
        try {
            String raw = cacheClient.getRaw(mirrorKey);
            if (raw == null) {
                metrics.increment("cache.detail.mirror_missing");
                return publishDetail(revision, mirrorKey);
            }
            Long redisVersion = parseVersion(raw);
            if (redisVersion == null) {
                log.warn("B8 product detail version mirror is corrupt; bypassing Redis");
                metrics.increment("cache.detail.bypass_corrupt");
                return new DetailVersionDecision(revision, false);
            }
            if (redisVersion == databaseVersion) {
                return new DetailVersionDecision(revision, true);
            }
            if (redisVersion < databaseVersion) {
                metrics.increment("cache.detail.mirror_behind");
                return publishDetail(revision, mirrorKey);
            }
            ProductCatalogRevision second = readRevision(productId);
            if (second == null) {
                throw new ProductCatalogSourceUnavailableException("product revision disappeared during gate read");
            }
            if (second.getItemVersion() == redisVersion) {
                return new DetailVersionDecision(second, true);
            }
            if (second.getItemVersion() > redisVersion) {
                return publishDetail(second, mirrorKey);
            }
            log.warn("B8 product detail version mirror is ahead of MySQL; bypassing Redis");
            metrics.increment("cache.detail.bypass_ahead");
            return new DetailVersionDecision(second, false);
        } catch (ProductCatalogSourceUnavailableException failure) {
            throw failure;
        } catch (RuntimeException redisFailure) {
            log.warn("B8 product detail cache gate degraded to MySQL: {}",
                    redisFailure.getClass().getSimpleName());
            metrics.increment("cache.detail.bypass_redis_error");
            return new DetailVersionDecision(revision, false);
        }
    }

    private DetailVersionDecision publishDetail(ProductCatalogRevision revision, String mirrorKey) {
        Long result = cacheClient.publishMaxVersion(mirrorKey, revision.getItemVersion());
        metrics.increment(Long.valueOf(1L).equals(result)
                ? "cache.detail.publish_success" : "cache.detail.publish_rejected");
        return new DetailVersionDecision(revision, Long.valueOf(1L).equals(result));
    }

    private ProductCatalogRevision readRevision(long productId) {
        try {
            ProductCatalogRevision revision = authority.readRevision(productId);
            if (revision == null) {
                return null;
            }
            if (revision.getProductId() == null || revision.getProductId() != productId
                    || revision.getItemVersion() == null || !valid(revision.getItemVersion())
                    || !("ACTIVE".equals(revision.getItemState())
                    || "INACTIVE".equals(revision.getItemState())
                    || "DELETED".equals(revision.getItemState()))) {
                throw new ProductCatalogSourceUnavailableException("product revision violates catalog invariants");
            }
            return revision;
        } catch (ProductCatalogSourceUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProductCatalogSourceUnavailableException("product revision source is unavailable", failure);
        }
    }

    private VersionDecision publish(long version) {
        Long result = cacheClient.publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, version);
        metrics.increment(Long.valueOf(1L).equals(result)
                ? "cache.list.publish_success" : "cache.list.publish_rejected");
        return new VersionDecision(version, Long.valueOf(1L).equals(result));
    }

    private long readAuthorityVersion() {
        try {
            long version = authority.readListVersion();
            if (!valid(version)) {
                throw new ProductCatalogSourceUnavailableException("catalog version is outside the safe domain");
            }
            return version;
        } catch (ProductCatalogSourceUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProductCatalogSourceUnavailableException("catalog version source is unavailable", failure);
        }
    }

    private static Long parseVersion(String raw) {
        try {
            long value = Long.parseLong(raw);
            return valid(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean valid(long version) {
        return version > 0 && version <= MAX_SAFE_VERSION;
    }
}
