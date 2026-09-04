package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.entity.ProductReconciliationStatusView;
import com.fashion.mapper.ProductCatalogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class ProductReconciliationService {
    private final ProductCatalogMapper mapper;
    private final ProductProjectionInventory inventory;
    private final ProductOrphanProjectionRepairer orphanRepairer;
    private final ProductBaselineProjectionRepairer baselineRepairer;
    private final int batchSize;
    private final int maxAutomaticRepairs;
    private final ProductProjectionMetrics metrics;

    @Autowired
    public ProductReconciliationService(ProductCatalogMapper mapper,
                                        ProductProjectionInventory inventory,
                                        ProductOrphanProjectionRepairer orphanRepairer,
                                        ProductBaselineProjectionRepairer baselineRepairer,
                                        ProductProjectionProperties properties,
                                        ProductProjectionMetrics metrics) {
        this(mapper, inventory, orphanRepairer, baselineRepairer,
                properties.getReconcileBatchSize(), 3, metrics);
    }

    ProductReconciliationService(ProductCatalogMapper mapper,
                                 ProductProjectionInventory inventory,
                                 ProductOrphanProjectionRepairer orphanRepairer,
                                 int batchSize, int maxAutomaticRepairs) {
        this(mapper, inventory, orphanRepairer, productId -> null, batchSize,
                maxAutomaticRepairs, new ProductProjectionMetrics());
    }

    ProductReconciliationService(ProductCatalogMapper mapper,
                                 ProductProjectionInventory inventory,
                                 ProductOrphanProjectionRepairer orphanRepairer,
                                 ProductBaselineProjectionRepairer baselineRepairer,
                                 int batchSize, int maxAutomaticRepairs) {
        this(mapper, inventory, orphanRepairer, baselineRepairer, batchSize,
                maxAutomaticRepairs, new ProductProjectionMetrics());
    }

    ProductReconciliationService(ProductCatalogMapper mapper,
                                 ProductProjectionInventory inventory,
                                 ProductOrphanProjectionRepairer orphanRepairer,
                                 ProductBaselineProjectionRepairer baselineRepairer,
                                 int batchSize, int maxAutomaticRepairs,
                                 ProductProjectionMetrics metrics) {
        this.mapper = mapper;
        this.inventory = inventory;
        this.orphanRepairer = orphanRepairer;
        this.baselineRepairer = baselineRepairer;
        this.batchSize = batchSize;
        this.maxAutomaticRepairs = maxAutomaticRepairs;
        this.metrics = metrics;
    }

    public ProductProjectionReconcileRun start(String mode) {
        if (!("CUTOVER".equals(mode) || "PERIODIC".equals(mode))) {
            throw new IllegalArgumentException("invalid reconciliation mode");
        }
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setMode(mode);
        run.setPhase("MYSQL_SCAN");
        run.setStatus("PENDING");
        run.setCursorPayload(encodeCursor(0, null));
        run.setScanCount(0L);
        run.setDriftCount(0L);
        run.setRepairCount(0L);
        run.setCleanVerifyCount(0);
        run.setAttemptCount(0);
        try {
            mapper.insertReconcileRun(run);
            return run;
        } catch (DuplicateKeyException duplicateActiveRun) {
            ProductProjectionReconcileRun active = mapper.readActiveReconcileRun();
            if (active == null) {
                throw duplicateActiveRun;
            }
            return active;
        }
    }

    public ProductProjectionReconcileRun active() {
        return mapper.readActiveReconcileRun();
    }

    public ProductReconciliationStatusView status() {
        ProductProjectionReconcileRun run = mapper.readActiveReconcileRun();
        if (run == null) {
            run = mapper.readLatestReconcileRun();
        }
        if (run == null) {
            return null;
        }
        ProductReconciliationStatusView view = new ProductReconciliationStatusView();
        view.setId(run.getId());
        view.setMode(run.getMode());
        view.setPhase(run.getPhase());
        view.setStatus(run.getStatus());
        view.setCursorValid(run.getCursorPayload() != null && !run.getCursorPayload().isEmpty());
        view.setScanCount(run.getScanCount());
        view.setDriftCount(run.getDriftCount());
        view.setRepairCount(run.getRepairCount());
        view.setCleanVerifyCount(run.getCleanVerifyCount());
        view.setAttemptCount(run.getAttemptCount());
        view.setNextRetryAt(run.getNextRetryAt());
        view.setLockedUntil(run.getLockedUntil());
        view.setLastErrorSummary(run.getLastErrorSummary());
        view.setStartedAt(run.getStartedAt());
        view.setCompletedAt(run.getCompletedAt());
        view.setUpdatedAt(run.getUpdatedAt());
        return view;
    }

    void processBatch(ProductProjectionReconcileRun run) {
        requireRunning(run);
        Cursor cursor = decodeCursor(run.getCursorPayload());
        if ("MYSQL_SCAN".equals(run.getPhase())) {
            processMysql(run, cursor);
        } else if ("ES_SCAN".equals(run.getPhase())) {
            processEs(run, cursor);
        } else if ("VERIFY".equals(run.getPhase())) {
            verifyPass(run, cursor);
        } else {
            terminal(run, "invalid_reconciliation_phase");
        }
    }

    private void processMysql(ProductProjectionReconcileRun run, Cursor cursor) {
        long after = cursor.cursor == null || cursor.cursor.isEmpty()
                ? 0L : Long.parseLong(cursor.cursor);
        List<ProductProjectionTask> expected = mapper.listCurrentEsTasksAfter(after, batchSize);
        long next = after;
        for (ProductProjectionTask task : expected) {
            next = task.getProductId();
            incrementScan(run);
            boolean repairAlreadyScheduled = false;
            if (task.getId() == null) {
                task = baselineRepairer.ensureCurrentEsTask(task.getProductId());
                cursor.passDrift++;
                incrementDrift(run);
                incrementRepair(run);
                repairAlreadyScheduled = true;
            }
            IndexedProductProjection indexed = inventory.read(task.getProductId());
            if (!matches(task, indexed)) {
                if (!repairAlreadyScheduled) {
                    cursor.passDrift++;
                    incrementDrift(run);
                    if (!ensureRepair(task)) {
                        terminal(run, "unrepairable_projection_task_" + task.getId());
                        return;
                    }
                    incrementRepair(run);
                }
            }
        }
        if (expected.size() < batchSize) {
            run.setPhase("ES_SCAN");
            run.setCursorPayload(encodeCursor(cursor.passDrift, null));
        } else {
            run.setCursorPayload(encodeCursor(cursor.passDrift, Long.toString(next)));
        }
    }

    private void processEs(ProductProjectionReconcileRun run, Cursor cursor) {
        ProjectionScanPage page;
        try {
            page = inventory.scanAfter(cursor.cursor, batchSize);
        } catch (ProductProjectionScanResetException expiredPit) {
            run.setCursorPayload(encodeCursor(cursor.passDrift, null));
            return;
        }
        for (IndexedProductProjection indexed : page.getItems()) {
            incrementScan(run);
            ProductProjectionTask expected = mapper.readCurrentEsTask(indexed.getProductId());
            if (expected == null) {
                cursor.passDrift++;
                incrementDrift(run);
                if (!orphanRepairer.createDeleteForOrphan(indexed.getProductId())) {
                    terminal(run, "orphan_projection_race_" + indexed.getProductId());
                    return;
                }
                incrementRepair(run);
            } else if (!matches(expected, indexed)) {
                if (indexed.getCatalogVersion() > expected.getCatalogVersion()) {
                    terminal(run, "es_version_ahead_" + indexed.getProductId());
                    return;
                }
                cursor.passDrift++;
                incrementDrift(run);
                if (!ensureRepair(expected)) {
                    terminal(run, "unrepairable_projection_task_" + expected.getId());
                    return;
                }
                incrementRepair(run);
            }
        }
        if (page.getNextCursor() == null) {
            run.setPhase("VERIFY");
            run.setCursorPayload(encodeCursor(cursor.passDrift, null));
        } else {
            run.setCursorPayload(encodeCursor(cursor.passDrift, page.getNextCursor()));
        }
    }

    private void verifyPass(ProductProjectionReconcileRun run, Cursor cursor) {
        if (cursor.passDrift == 0) {
            int clean = value(run.getCleanVerifyCount()) + 1;
            run.setCleanVerifyCount(clean);
            if (clean >= 2) {
                run.setStatus("SUCCEEDED");
                run.setCursorPayload(null);
                return;
            }
        } else {
            run.setCleanVerifyCount(0);
        }
        run.setPhase("MYSQL_SCAN");
        run.setCursorPayload(encodeCursor(0, null));
    }

    private boolean matches(ProductProjectionTask expected, IndexedProductProjection indexed) {
        if ("DELETE".equals(expected.getOperation())) {
            return indexed == null;
        }
        return indexed != null
                && expected.getCatalogVersion() == indexed.getCatalogVersion()
                && expected.getPayloadSha256().equals(indexed.getProjectionHash());
    }

    private boolean ensureRepair(ProductProjectionTask task) {
        String status = task.getStatus();
        if ("PENDING".equals(status) || "PROCESSING".equals(status) || "RETRY_WAIT".equals(status)) {
            return true;
        }
        if (("SUCCEEDED".equals(status) || "SUPERSEDED".equals(status))
                && value(task.getRepairCount()) < maxAutomaticRepairs) {
            return mapper.reopenProjectionTaskForRepair(task.getId(), maxAutomaticRepairs) == 1;
        }
        return false;
    }

    private void requireRunning(ProductProjectionReconcileRun run) {
        if (run == null || run.getId() == null
                || !("RUNNING".equals(run.getStatus()) || "PENDING".equals(run.getStatus()))) {
            throw new IllegalArgumentException("active reconciliation run is required");
        }
        if ("PENDING".equals(run.getStatus())) {
            run.setStatus("RUNNING");
        }
    }

    private void terminal(ProductProjectionReconcileRun run, String summary) {
        run.setStatus("FAILED_TERMINAL");
        run.setLastErrorSummary(summary);
        run.setCursorPayload(null);
    }

    private void incrementScan(ProductProjectionReconcileRun run) { run.setScanCount(value(run.getScanCount()) + 1); }
    private void incrementDrift(ProductProjectionReconcileRun run) {
        run.setDriftCount(value(run.getDriftCount()) + 1);
        metrics.increment("reconcile.drift_detected");
    }
    private void incrementRepair(ProductProjectionReconcileRun run) { run.setRepairCount(value(run.getRepairCount()) + 1); }
    private long value(Long value) { return value == null ? 0L : value; }
    private int value(Integer value) { return value == null ? 0 : value; }

    private String encodeCursor(long passDrift, String cursor) {
        return passDrift + "|" + (cursor == null ? "" : cursor);
    }

    private Cursor decodeCursor(String payload) {
        if (payload == null || payload.isEmpty()) return new Cursor(0, null);
        int split = payload.indexOf('|');
        if (split < 0) throw new IllegalArgumentException("invalid reconciliation cursor");
        long drift = Long.parseLong(payload.substring(0, split));
        String cursor = payload.substring(split + 1);
        return new Cursor(drift, cursor.isEmpty() ? null : cursor);
    }

    private static final class Cursor {
        private long passDrift;
        private final String cursor;
        private Cursor(long passDrift, String cursor) { this.passDrift = passDrift; this.cursor = cursor; }
    }
}
