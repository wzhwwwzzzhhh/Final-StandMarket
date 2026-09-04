package com.fashion.product;

import com.fashion.entity.ProductCatalogRevision;
import com.fashion.entity.ProductProjectionTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductProjectionWorkerTest {

    @Test
    void retryableFailureUsesFiniteBackoffAndEighthAttemptBecomesTerminal() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        ProductProjectionTask first = task("REDIS", 1);
        when(repository.claim("REDIS")).thenReturn(first);
        doThrow(ProjectionDeliveryException.retryable("http_503"))
                .when(redis).deliver(first);
        ProductProjectionWorker worker = worker(repository, redis, es, mock(ProductCatalogAuthority.class));

        assertThat(worker.processOne("REDIS")).isTrue();
        verify(repository).retry(eq(first), any(LocalDateTime.class), eq("http_503"));
        verify(repository, never()).terminal(any(), anyString());

        reset(repository, redis);
        ProductProjectionTask exhausted = task("REDIS", 8);
        when(repository.claim("REDIS")).thenReturn(exhausted);
        doThrow(ProjectionDeliveryException.retryable("timeout"))
                .when(redis).deliver(exhausted);
        assertThat(worker.processOne("REDIS")).isTrue();
        verify(repository).terminal(exhausted, "timeout");
        verify(repository, never()).retry(any(), any(), anyString());
    }

    @Test
    void nonRetryableFailureIsTerminalImmediatelyAndSummaryIsAlreadySafe() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        ProductProjectionTask task = task("ES", 1);
        when(repository.claim("ES")).thenReturn(task);
        ProductCatalogAuthority authority = mock(ProductCatalogAuthority.class);
        when(authority.readRevision(7L)).thenReturn(revision(10L, "ACTIVE"));
        doThrow(ProjectionDeliveryException.permanent("mapping_http_400"))
                .when(es).deliver(task);

        worker(repository, redis, es, authority).processOne("ES");

        verify(repository).terminal(task, "mapping_http_400");
        verify(repository, never()).retry(any(), any(), anyString());
    }

    @Test
    void olderEsTaskIsSupersededBeforeExternalCall() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        ProductProjectionTask task = task("ES", 1);
        task.setCatalogVersion(9L);
        when(repository.claim("ES")).thenReturn(task);
        when(repository.ownsDeliveryLease(task)).thenReturn(true);
        when(repository.ownsCurrentDelivery(task)).thenReturn(false);
        ProductCatalogAuthority authority = mock(ProductCatalogAuthority.class);
        when(authority.readRevision(7L)).thenReturn(revision(10L, "ACTIVE"));

        worker(repository, redis, es, authority).processOne("ES");

        verify(repository).supersede(task);
        verify(es, never()).deliver(any());
    }

    @Test
    void successfulDuplicateDeliveryCompletesThroughOwnerCas() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        ProductProjectionTask task = task("REDIS", 2);
        when(repository.claim("REDIS")).thenReturn(task);

        worker(repository, redis, es, mock(ProductCatalogAuthority.class)).processOne("REDIS");

        verify(redis).deliver(task);
        verify(repository).succeed(task);
    }

    @Test
    void wakeupFailureForOneTargetDoesNotSuppressTheOtherTarget() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        when(repository.claim("REDIS")).thenThrow(new IllegalStateException("redis repository unavailable"));
        when(repository.claim("ES")).thenReturn(null);
        ProductProjectionWorker worker = worker(repository, redis, es, mock(ProductCatalogAuthority.class));

        assertThatCode(() -> worker.onReady(new ProductProjectionReadyEvent(7L, 10L)))
                .doesNotThrowAnyException();
        verify(repository).claim("ES");
    }

    @Test
    void lostOwnerOrNonCurrentRevisionIsRecheckedImmediatelyBeforeEsCall() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = delivery("REDIS");
        ProductProjectionDelivery es = delivery("ES");
        ProductProjectionTask task = task("ES", 1);
        when(repository.claim("ES")).thenReturn(task);
        ProductCatalogAuthority authority = mock(ProductCatalogAuthority.class);
        when(authority.readRevision(7L)).thenReturn(revision(10L, "ACTIVE"));
        ProductProjectionWorker worker = worker(repository, redis, es, authority);
        when(repository.ownsCurrentDelivery(task)).thenReturn(false);

        assertThat(worker.processOne("ES")).isTrue();

        verify(es, never()).deliver(any());
        verify(repository, never()).succeed(any());
        verify(repository, never()).retry(any(), any(), anyString());
        verify(repository, never()).terminal(any(), anyString());
    }

    private ProductProjectionWorker worker(ProductProjectionTaskRepository repository,
                                           ProductProjectionDelivery redis,
                                           ProductProjectionDelivery es,
                                           ProductCatalogAuthority authority) {
        ProductProjectionProperties properties = new ProductProjectionProperties();
        when(repository.ownsDeliveryLease(any())).thenReturn(true);
        when(repository.ownsCurrentDelivery(any())).thenReturn(true);
        return new ProductProjectionWorker(repository, Arrays.asList(redis, es), authority, properties);
    }

    private ProductProjectionDelivery delivery(String target) {
        ProductProjectionDelivery delivery = mock(ProductProjectionDelivery.class);
        when(delivery.target()).thenReturn(target);
        return delivery;
    }

    private ProductProjectionTask task(String target, int attempts) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setId(1L);
        task.setTarget(target);
        task.setProductId(7L);
        task.setCatalogVersion(10L);
        task.setOperation("ES".equals(target) ? "UPSERT" : "PUBLISH");
        task.setAttemptCount(attempts);
        task.setLockedBy("worker-token");
        return task;
    }

    private ProductCatalogRevision revision(long version, String state) {
        ProductCatalogRevision revision = new ProductCatalogRevision();
        revision.setProductId(7L);
        revision.setItemVersion(version);
        revision.setItemState(state);
        return revision;
    }
}
