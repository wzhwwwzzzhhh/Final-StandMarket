package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.mapper.ProductCatalogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class MybatisProductReconcileRunRepository implements ProductReconcileRunRepository {
    private final ProductCatalogMapper mapper;
    private final ProductProjectionProperties properties;
    private final ProductProjectionMetrics metrics;

    public MybatisProductReconcileRunRepository(ProductCatalogMapper mapper, ProductProjectionProperties properties) {
        this(mapper, properties, new ProductProjectionMetrics());
    }

    @Autowired
    public MybatisProductReconcileRunRepository(ProductCatalogMapper mapper,
                                                ProductProjectionProperties properties,
                                                ProductProjectionMetrics metrics) {
        this.mapper = mapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductProjectionReconcileRun claim() {
        mapper.terminalizeExhaustedReconcileRuns(properties.getMaxAttempts());
        String token = UUID.randomUUID().toString();
        if (mapper.claimReconcileRun(token, LocalDateTime.now().plus(properties.getLease()),
                properties.getMaxAttempts()) != 1) return null;
        ProductProjectionReconcileRun run = mapper.readClaimedReconcileRun(token);
        if (run == null) throw new IllegalStateException("claimed reconciliation run is missing");
        return run;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(ProductProjectionReconcileRun run) {
        requireOwner(run);
        if ("RUNNING".equals(run.getStatus())) {
            // A completed batch yields ownership; the durable run remains immediately claimable.
            run.setStatus("PENDING");
        }
        if (mapper.saveClaimedReconcileRun(run) != 1) throw new IllegalStateException("stale reconciliation owner");
        countOutcome(run.getStatus());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(ProductProjectionReconcileRun run, LocalDateTime nextRetryAt, String safeSummary) {
        complete(run, "RETRY_WAIT", nextRetryAt, safeSummary);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminal(ProductProjectionReconcileRun run, String safeSummary) {
        complete(run, "FAILED_TERMINAL", null, safeSummary);
    }

    private void complete(ProductProjectionReconcileRun run, String status, LocalDateTime nextRetryAt, String summary) {
        requireOwner(run);
        if (mapper.failClaimedReconcileRun(run.getId(), run.getLockedBy(), status, nextRetryAt, summary) != 1) {
            throw new IllegalStateException("stale reconciliation owner");
        }
        countOutcome(status);
    }

    private void countOutcome(String status) {
        if ("SUCCEEDED".equals(status)) {
            metrics.increment("reconcile.success");
        } else if ("RETRY_WAIT".equals(status)) {
            metrics.increment("reconcile.retry");
        } else if ("FAILED_TERMINAL".equals(status)) {
            metrics.increment("reconcile.terminal");
        }
    }

    private void requireOwner(ProductProjectionReconcileRun run) {
        if (run == null || run.getId() == null || run.getLockedBy() == null) {
            throw new IllegalArgumentException("claimed reconciliation ownership is required");
        }
    }
}
