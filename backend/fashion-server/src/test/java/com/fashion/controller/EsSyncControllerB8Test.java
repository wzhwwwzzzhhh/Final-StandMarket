package com.fashion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.controller.admin.EsSyncController;
import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.entity.ProductReconciliationStatusView;
import com.fashion.product.ProductReconciliationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EsSyncControllerB8Test {
    @Test
    void legacySyncSurfaceCreatesNonDestructiveCutoverRun() {
        ProductReconciliationService reconciliation = mock(ProductReconciliationService.class);
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setId(23L);
        when(reconciliation.start("CUTOVER")).thenReturn(run);
        EsSyncController controller = new EsSyncController(reconciliation);

        assertThat(controller.syncAll().getData()).contains("23");
        verify(reconciliation).start("CUTOVER");
    }

    @Test
    void reconciliationFailureDoesNotExposeInfrastructureMessage() {
        ProductReconciliationService reconciliation = mock(ProductReconciliationService.class);
        when(reconciliation.start("CUTOVER"))
                .thenThrow(new IllegalStateException("infrastructure-detail-MUST-NOT-LEAK"));
        EsSyncController controller = new EsSyncController(reconciliation);

        assertThat(controller.syncAll().getMsg()).isEqualTo("创建商品对账任务失败");
    }

    @Test
    void terminalStatusRemainsObservableWithoutCursorOrLeaseOwner() throws Exception {
        ProductReconciliationService reconciliation = mock(ProductReconciliationService.class);
        ProductReconciliationStatusView view = new ProductReconciliationStatusView();
        view.setId(31L);
        view.setStatus("FAILED_TERMINAL");
        view.setCursorValid(false);
        view.setLastErrorSummary("es_version_ahead_7");
        when(reconciliation.status()).thenReturn(view);

        Object data = new EsSyncController(reconciliation).status().getData();

        assertThat(data).isSameAs(view);
        assertThat(data).hasNoNullFieldsOrPropertiesExcept(
                "mode", "phase", "scanCount", "driftCount", "repairCount",
                "cleanVerifyCount", "attemptCount", "nextRetryAt", "lockedUntil",
                "startedAt", "completedAt", "updatedAt");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(data))
                .doesNotContain("cursorPayload", "lockedBy", "pit-and-search-after", "lease-owner-token");
    }
}
