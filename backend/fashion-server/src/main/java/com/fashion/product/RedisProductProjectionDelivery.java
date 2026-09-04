package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;
import com.fashion.utils.CacheClient;
import org.springframework.stereotype.Component;

@Component
public class RedisProductProjectionDelivery implements ProductProjectionDelivery {
    private final CacheClient cacheClient;

    public RedisProductProjectionDelivery(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Override
    public String target() {
        return "REDIS";
    }

    @Override
    public void deliver(ProductProjectionTask task) {
        if (task == null || task.getProductId() == null || task.getCatalogVersion() == null
                || !"PUBLISH".equals(task.getOperation())) {
            throw ProjectionDeliveryException.permanent("invalid_redis_projection_task");
        }
        try {
            Long list = cacheClient.publishMaxVersion(
                    ProductCacheKeys.LIST_PUBLISHED_VERSION, task.getCatalogVersion());
            if (!Long.valueOf(1L).equals(list)) {
                throw ProjectionDeliveryException.retryable("redis_list_publish_rejected");
            }
            Long detail = cacheClient.publishMaxVersion(
                    ProductCacheKeys.detailPublishedVersion(task.getProductId()),
                    task.getCatalogVersion());
            if (!Long.valueOf(1L).equals(detail)) {
                throw ProjectionDeliveryException.retryable("redis_detail_publish_rejected");
            }
        } catch (ProjectionDeliveryException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw ProjectionDeliveryException.retryable(
                    "redis_" + failure.getClass().getSimpleName(), failure);
        }
    }
}
