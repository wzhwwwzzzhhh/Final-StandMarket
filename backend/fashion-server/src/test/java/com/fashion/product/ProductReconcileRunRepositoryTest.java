package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.mapper.ProductCatalogMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductReconcileRunRepositoryTest {

    @Test
    void successfulNonTerminalBatchYieldsLeaseAsImmediatelyClaimablePending() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        when(mapper.saveClaimedReconcileRun(any())).thenReturn(1);
        MybatisProductReconcileRunRepository repository =
                new MybatisProductReconcileRunRepository(mapper, new ProductProjectionProperties());
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setId(7L);
        run.setStatus("RUNNING");
        run.setLockedBy("owner-token");

        repository.save(run);

        assertThat(run.getStatus()).isEqualTo("PENDING");
        verify(mapper).saveClaimedReconcileRun(run);
    }

    @Test
    void successfulOwnerCasCountsReconciliationOutcomes() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        when(mapper.saveClaimedReconcileRun(any())).thenReturn(1);
        when(mapper.failClaimedReconcileRun(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(1);
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        MybatisProductReconcileRunRepository repository =
                new MybatisProductReconcileRunRepository(
                        mapper, new ProductProjectionProperties(), metrics);
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setId(7L);
        run.setLockedBy("owner-token");

        run.setStatus("SUCCEEDED");
        repository.save(run);
        repository.retry(run, java.time.LocalDateTime.now().plusSeconds(1), "retry");
        repository.terminal(run, "terminal");

        assertThat(metrics.count("reconcile.success")).isEqualTo(1);
        assertThat(metrics.count("reconcile.retry")).isEqualTo(1);
        assertThat(metrics.count("reconcile.terminal")).isEqualTo(1);
    }
}
