package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.entity.ProductReconciliationStatusView;
import com.fashion.mapper.ProductCatalogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductReconciliationServiceTest {

    @Test
    void concurrentStartReturnsTheSingleActiveRun() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        doThrow(new DuplicateKeyException("active_slot"))
                .when(mapper).insertReconcileRun(any(ProductProjectionReconcileRun.class));
        ProductProjectionReconcileRun active = run("MYSQL_SCAN");
        active.setId(9L);
        when(mapper.readActiveReconcileRun()).thenReturn(active);

        ProductProjectionReconcileRun result = service(mapper, mock(ProductProjectionInventory.class),
                mock(ProductOrphanProjectionRepairer.class)).start("PERIODIC");

        assertThat(result.getId()).isEqualTo(9L);
        verify(mapper).readActiveReconcileRun();
    }

    @Test
    void statusFallsBackToLatestTerminalRunAndRedactsOwnerAndCursor() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionReconcileRun terminal = run("VERIFY");
        terminal.setId(19L);
        terminal.setStatus("FAILED_TERMINAL");
        terminal.setCursorPayload("pit-and-search-after");
        terminal.setLockedBy("lease-owner-token");
        terminal.setLastErrorSummary("es_version_ahead_7");
        when(mapper.readActiveReconcileRun()).thenReturn(null);
        when(mapper.readLatestReconcileRun()).thenReturn(terminal);

        ProductReconciliationStatusView result = service(mapper,
                mock(ProductProjectionInventory.class),
                mock(ProductOrphanProjectionRepairer.class)).status();

        assertThat(result.getId()).isEqualTo(19L);
        assertThat(result.getStatus()).isEqualTo("FAILED_TERMINAL");
        assertThat(result.isCursorValid()).isTrue();
        assertThat(result.getLastErrorSummary()).isEqualTo("es_version_ahead_7");
    }

    @Test
    void mysqlDriftReopensTheImmutableCurrentTaskAndAdvancesOnlyAfterPageEnd() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionInventory inventory = mock(ProductProjectionInventory.class);
        ProductProjectionTask expected = task(7L, 12L, "UPSERT", "expected", "SUCCEEDED");
        when(mapper.listCurrentEsTasksAfter(0L, 100)).thenReturn(Collections.singletonList(expected));
        when(mapper.reopenProjectionTaskForRepair(1L, 3)).thenReturn(1);
        when(inventory.read(7L)).thenReturn(new IndexedProductProjection(7L, 11L, "old"));
        ProductProjectionReconcileRun run = run("MYSQL_SCAN");

        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        new ProductReconciliationService(mapper, inventory,
                mock(ProductOrphanProjectionRepairer.class), productId -> null,
                100, 3, metrics).processBatch(run);

        verify(mapper).reopenProjectionTaskForRepair(1L, 3);
        assertThat(run.getDriftCount()).isEqualTo(1L);
        assertThat(run.getRepairCount()).isEqualTo(1L);
        assertThat(run.getPhase()).isEqualTo("ES_SCAN");
        assertThat(run.getStatus()).isEqualTo("RUNNING");
        assertThat(metrics.count("reconcile.drift_detected")).isEqualTo(1);
    }

    @Test
    void successRequiresTwoCompleteZeroDriftPasses() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionInventory inventory = mock(ProductProjectionInventory.class);
        when(mapper.listCurrentEsTasksAfter(anyLong(), eq(100))).thenReturn(Collections.emptyList());
        when(inventory.scanAfter(any(), eq(100))).thenReturn(ProjectionScanPage.end());
        ProductReconciliationService service = service(mapper, inventory,
                mock(ProductOrphanProjectionRepairer.class));
        ProductProjectionReconcileRun run = run("MYSQL_SCAN");

        service.processBatch(run); // first MySQL pass
        service.processBatch(run); // first ES pass
        assertThat(run.getPhase()).isEqualTo("VERIFY");
        assertThat(run.getStatus()).isEqualTo("RUNNING");
        service.processBatch(run); // verify schedules second full pass
        assertThat(run.getCleanVerifyCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo("RUNNING");
        service.processBatch(run);
        service.processBatch(run);
        service.processBatch(run);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getCleanVerifyCount()).isEqualTo(2);
    }

    @Test
    void esOnlyDocumentCreatesDurableMysqlTombstoneInsteadOfDirectDelete() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionInventory inventory = mock(ProductProjectionInventory.class);
        ProductOrphanProjectionRepairer repairer = mock(ProductOrphanProjectionRepairer.class);
        when(inventory.scanAfter(null, 100)).thenReturn(ProjectionScanPage.of(
                Collections.singletonList(new IndexedProductProjection(55L, 4L, "hash")), null));
        when(mapper.readCurrentEsTask(55L)).thenReturn(null);
        when(repairer.createDeleteForOrphan(55L)).thenReturn(true);
        ProductProjectionReconcileRun run = run("ES_SCAN");

        service(mapper, inventory, repairer).processBatch(run);

        verify(repairer).createDeleteForOrphan(55L);
        assertThat(run.getRepairCount()).isEqualTo(1L);
    }

    @Test
    void mysqlRevisionWithoutCurrentTaskCreatesImmutableBaselineTaskBeforeComparingEs() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionInventory inventory = mock(ProductProjectionInventory.class);
        ProductBaselineProjectionRepairer baseline = mock(ProductBaselineProjectionRepairer.class);
        ProductProjectionTask missing = task(7L, 12L, "UPSERT", null, null);
        missing.setId(null);
        ProductProjectionTask repaired = task(7L, 12L, "UPSERT", "expected", "PENDING");
        when(mapper.listCurrentEsTasksAfter(0L, 100)).thenReturn(Collections.singletonList(missing));
        when(baseline.ensureCurrentEsTask(7L)).thenReturn(repaired);
        when(inventory.read(7L)).thenReturn(null);
        ProductProjectionReconcileRun run = run("MYSQL_SCAN");
        ProductReconciliationService service = new ProductReconciliationService(
                mapper, inventory, mock(ProductOrphanProjectionRepairer.class), baseline, 100, 3);

        service.processBatch(run);

        verify(baseline).ensureCurrentEsTask(7L);
        assertThat(run.getRepairCount()).isEqualTo(1L);
        assertThat(run.getDriftCount()).isEqualTo(1L);
    }

    private ProductReconciliationService service(ProductCatalogMapper mapper,
                                                 ProductProjectionInventory inventory,
                                                 ProductOrphanProjectionRepairer repairer) {
        return new ProductReconciliationService(mapper, inventory, repairer, 100, 3);
    }

    private ProductProjectionReconcileRun run(String phase) {
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setId(1L);
        run.setMode("PERIODIC");
        run.setPhase(phase);
        run.setStatus("RUNNING");
        run.setScanCount(0L);
        run.setDriftCount(0L);
        run.setRepairCount(0L);
        run.setCleanVerifyCount(0);
        run.setCursorPayload(null);
        return run;
    }

    private ProductProjectionTask task(long productId, long version, String operation,
                                       String hash, String status) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setId(1L);
        task.setProductId(productId);
        task.setCatalogVersion(version);
        task.setOperation(operation);
        task.setPayloadSha256(hash);
        task.setStatus(status);
        task.setRepairCount(0);
        return task;
    }
}
