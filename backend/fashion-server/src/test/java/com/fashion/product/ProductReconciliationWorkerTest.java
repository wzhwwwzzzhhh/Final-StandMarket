package com.fashion.product;

import com.fashion.entity.ProductProjectionReconcileRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductReconciliationWorkerTest {

    @Test
    void claimedBatchIsPersistedThroughOwnerCas() {
        ProductReconcileRunRepository repository = mock(ProductReconcileRunRepository.class);
        ProductReconciliationService service = mock(ProductReconciliationService.class);
        ProductProjectionReconcileRun run = run(1);
        when(repository.claim()).thenReturn(run);

        new ProductReconciliationWorker(repository, service,
                new ProductProjectionProperties()).poll();

        verify(service).processBatch(run);
        verify(repository).save(run);
    }

    @Test
    void eighthFailureIsTerminalAndEarlierFailureUsesFiniteRetry() {
        ProductReconcileRunRepository repository = mock(ProductReconcileRunRepository.class);
        ProductReconciliationService service = mock(ProductReconciliationService.class);
        ProductProjectionReconcileRun retry = run(7);
        when(repository.claim()).thenReturn(retry);
        doThrow(new IllegalStateException("url and secret must not leak"))
                .when(service).processBatch(retry);

        new ProductReconciliationWorker(repository, service,
                new ProductProjectionProperties()).poll();

        verify(repository).retry(eq(retry), any(LocalDateTime.class), eq("reconcile_IllegalStateException"));

        reset(repository, service);
        ProductProjectionReconcileRun exhausted = run(8);
        when(repository.claim()).thenReturn(exhausted);
        doThrow(new IllegalStateException()).when(service).processBatch(exhausted);
        new ProductReconciliationWorker(repository, service,
                new ProductProjectionProperties()).poll();
        verify(repository).terminal(exhausted, "reconcile_IllegalStateException");
    }

    private ProductProjectionReconcileRun run(int attempts) {
        ProductProjectionReconcileRun run = new ProductProjectionReconcileRun();
        run.setId(1L);
        run.setMode("PERIODIC");
        run.setPhase("MYSQL_SCAN");
        run.setStatus("RUNNING");
        run.setAttemptCount(attempts);
        run.setLockedBy("owner-token");
        return run;
    }
}
