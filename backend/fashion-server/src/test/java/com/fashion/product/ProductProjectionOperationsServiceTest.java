package com.fashion.product;

import com.fashion.entity.ProductProjectionStatusSummary;
import com.fashion.entity.ProductProjectionTaskView;
import com.fashion.mapper.ProductCatalogMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductProjectionOperationsServiceTest {

    @Test
    void statusAndTaskViewsNeverExposePayload() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionStatusSummary summary = new ProductProjectionStatusSummary();
        summary.setTarget("ES");
        summary.setStatus("RETRY_WAIT");
        summary.setTaskCount(2L);
        ProductProjectionTaskView view = new ProductProjectionTaskView();
        view.setId(1L);
        view.setLastErrorSummary("es_http_503");
        when(mapper.summarizeProjectionTasks()).thenReturn(Collections.singletonList(summary));
        when(mapper.listProjectionTaskViews("ES", "RETRY_WAIT", 50))
                .thenReturn(Collections.singletonList(view));
        ProductProjectionOperationsService service = new ProductProjectionOperationsService(mapper);

        assertThat(service.status()).containsExactly(summary);
        assertThat(service.tasks("ES", "RETRY_WAIT", 50)).containsExactly(view);
        assertThat(ProductProjectionTaskView.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("payload", "payloadSha256");
    }

    @Test
    void manualReplayOnlyReopensOneTerminalTaskAndLeavesAuditCountersToMysql() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        when(mapper.replayTerminalProjectionTask(7L)).thenReturn(1, 0);
        ProductProjectionOperationsService service = new ProductProjectionOperationsService(mapper);

        service.replay(7L);
        assertThatThrownBy(() -> service.replay(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("projection task is not replayable");
        verify(mapper, times(2)).replayTerminalProjectionTask(7L);
    }

    @Test
    void taskFiltersRejectUnknownStateBeforeQueryingMysql() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionOperationsService service = new ProductProjectionOperationsService(mapper);

        assertThatThrownBy(() -> service.tasks("ES", "DROP TABLE", 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid projection status");
        verifyNoInteractions(mapper);
    }

    @Test
    void operationalCountersAreQueryableWithoutMutableAccess() {
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        metrics.increment("reconcile.drift_detected");
        ProductProjectionOperationsService service = new ProductProjectionOperationsService(
                mock(ProductCatalogMapper.class), metrics);

        assertThat(service.metrics()).containsEntry("reconcile.drift_detected", 1L);
        assertThatThrownBy(() -> service.metrics().put("tamper", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
