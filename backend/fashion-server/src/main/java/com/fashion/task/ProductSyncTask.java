package com.fashion.task;

import com.fashion.product.ProductReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Starts a durable non-destructive reconciliation run. */
@Component
public class ProductSyncTask {
    private final ProductReconciliationService reconciliationService;

    public ProductSyncTask(ProductReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** 每 5 分钟创建或复用一个非破坏性、可恢复的对账任务。 */
    @Scheduled(fixedDelayString = "${fashion.product-projection.reconcile-interval:300000}")
    public void syncProducts() {
        reconciliationService.start("PERIODIC");
    }
}
