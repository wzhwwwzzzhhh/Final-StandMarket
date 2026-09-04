package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class ProductReconciliationWorker {
    private final ProductReconcileRunRepository repository;
    private final ProductReconciliationService service;
    private final ProductProjectionProperties properties;

    public ProductReconciliationWorker(ProductReconcileRunRepository repository,
                                       ProductReconciliationService service,
                                       ProductProjectionProperties properties) {
        this.repository = repository;
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fashion.product-projection.reconcile-poll-delay:5000}")
    public void poll() {
        ProductProjectionReconcileRun run = repository.claim();
        if (run == null) return;
        try {
            service.processBatch(run);
            repository.save(run);
        } catch (RuntimeException failure) {
            String summary = "reconcile_" + failure.getClass().getSimpleName();
            int attempts = run.getAttemptCount() == null ? properties.getMaxAttempts() : run.getAttemptCount();
            if (attempts >= properties.getMaxAttempts()) {
                repository.terminal(run, summary);
            } else {
                int shift = Math.max(0, Math.min(20, attempts));
                long base = properties.getRetryBase().toMillis();
                long exponential = base > (Long.MAX_VALUE >> shift) ? properties.getRetryMax().toMillis() : base << shift;
                long delay = Math.min(exponential, properties.getRetryMax().toMillis());
                repository.retry(run, LocalDateTime.now().plusNanos(delay * 1_000_000L), summary);
            }
        }
    }
}
