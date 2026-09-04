package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import java.time.LocalDateTime;

public interface ProductReconcileRunRepository {
    ProductProjectionReconcileRun claim();
    void save(ProductProjectionReconcileRun run);
    void retry(ProductProjectionReconcileRun run, LocalDateTime nextRetryAt, String safeSummary);
    void terminal(ProductProjectionReconcileRun run, String safeSummary);
}
