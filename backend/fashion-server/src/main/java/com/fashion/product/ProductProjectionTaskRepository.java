package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;

import java.time.LocalDateTime;

public interface ProductProjectionTaskRepository {
    ProductProjectionTask claim(String target);
    boolean ownsDeliveryLease(ProductProjectionTask task);
    boolean ownsCurrentDelivery(ProductProjectionTask task);
    void succeed(ProductProjectionTask task);
    void retry(ProductProjectionTask task, LocalDateTime nextRetryAt, String safeSummary);
    void terminal(ProductProjectionTask task, String safeSummary);
    void supersede(ProductProjectionTask task);
}
