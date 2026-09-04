package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;
import com.fashion.mapper.ProductCatalogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductProjectionTaskRepositoryTest {

    @Test
    void esClaimConsumesAttemptAndAcquiresIndependentRevisionLease() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionProperties properties = new ProductProjectionProperties();
        ProductProjectionTask candidate = task("ES");
        AtomicReference<String> claimedToken = new AtomicReference<>();
        when(mapper.lockNextClaimable("ES", 8)).thenReturn(candidate);
        when(mapper.claimEsRevisionLease(eq(7L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.markTaskProcessing(eq(1L), anyString(), any(LocalDateTime.class), eq(8)))
                .thenAnswer(invocation -> {
                    claimedToken.set(invocation.getArgument(1));
                    return 1;
                });
        when(mapper.readProjectionTask(1L)).thenAnswer(invocation -> {
            ProductProjectionTask claimed = task("ES");
            claimed.setLockedBy(claimedToken.get());
            claimed.setAttemptCount(1);
            return claimed;
        });
        MybatisProductProjectionTaskRepository repository =
                new MybatisProductProjectionTaskRepository(mapper, properties);

        ProductProjectionTask result = repository.claim("ES");

        assertThat(result.getAttemptCount()).isEqualTo(1);
        verify(mapper).terminalizeExhausted("ES", 8);
        verify(mapper).claimEsRevisionLease(eq(7L), anyString(), any(LocalDateTime.class));
        verify(mapper).markTaskProcessing(eq(1L), anyString(), any(LocalDateTime.class), eq(8));
    }

    @Test
    void completionAndRetryUseTaskOwnerCasAndReleaseOnlyMatchingEsLease() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        when(mapper.completeProjectionTask(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        MybatisProductProjectionTaskRepository repository = new MybatisProductProjectionTaskRepository(
                mapper, new ProductProjectionProperties(), metrics);
        ProductProjectionTask task = task("ES");
        task.setLockedBy("owner-token");

        repository.succeed(task);
        verify(mapper).completeProjectionTask(1L, "owner-token", "SUCCEEDED", null, null);
        verify(mapper).releaseEsRevisionLease(7L, "owner-token");

        reset(mapper);
        when(mapper.completeProjectionTask(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        LocalDateTime next = LocalDateTime.now().plusSeconds(1);
        repository.retry(task, next, "http_503");
        verify(mapper).completeProjectionTask(1L, "owner-token", "RETRY_WAIT", next, "http_503");
        verify(mapper).releaseEsRevisionLease(7L, "owner-token");
        assertThat(metrics.count("projection.task.es.success")).isEqualTo(1);
        assertThat(metrics.count("projection.task.es.retry")).isEqualTo(1);
    }

    @Test
    void failedEsRevisionLeaseDoesNotConsumeExternalAttempt() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionTask candidate = task("ES");
        when(mapper.lockNextClaimable("ES", 8)).thenReturn(candidate);
        when(mapper.claimEsRevisionLease(eq(7L), anyString(), any(LocalDateTime.class))).thenReturn(0);
        MybatisProductProjectionTaskRepository repository = new MybatisProductProjectionTaskRepository(
                mapper, new ProductProjectionProperties());

        assertThat(repository.claim("ES")).isNull();

        verify(mapper, never()).markTaskProcessing(anyLong(), anyString(), any(), anyInt());
    }

    @Test
    void expiredLeaseRecoveryAndTerminalOutcomeIncrementQueryableCounters() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        ProductProjectionTask candidate = task("ES");
        candidate.setStatus("PROCESSING");
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        java.util.concurrent.atomic.AtomicReference<String> token = new java.util.concurrent.atomic.AtomicReference<>();
        when(mapper.lockNextClaimable("ES", 8)).thenReturn(candidate);
        when(mapper.claimEsRevisionLease(eq(7L), anyString(), any())).thenReturn(1);
        when(mapper.markTaskProcessing(eq(1L), anyString(), any(), eq(8))).thenAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            return 1;
        });
        when(mapper.readProjectionTask(1L)).thenAnswer(invocation -> {
            ProductProjectionTask claimed = task("ES");
            claimed.setLockedBy(token.get());
            claimed.setAttemptCount(8);
            return claimed;
        });
        when(mapper.completeProjectionTask(anyLong(), anyString(), eq("FAILED_TERMINAL"),
                isNull(), anyString())).thenReturn(1);
        MybatisProductProjectionTaskRepository repository =
                new MybatisProductProjectionTaskRepository(mapper,
                        new ProductProjectionProperties(), metrics);

        ProductProjectionTask claimed = repository.claim("ES");
        repository.terminal(claimed, "attempt_budget_exhausted");

        assertThat(metrics.count("projection.task.lease_recovered")).isEqualTo(1);
        assertThat(metrics.count("projection.task.es.terminal")).isEqualTo(1);
    }

    @Test
    void staleOwnerCasDoesNotIncrementOutcomeCounter() {
        ProductCatalogMapper mapper = mock(ProductCatalogMapper.class);
        when(mapper.completeProjectionTask(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(0);
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        MybatisProductProjectionTaskRepository repository =
                new MybatisProductProjectionTaskRepository(mapper,
                        new ProductProjectionProperties(), metrics);
        ProductProjectionTask task = task("REDIS");
        task.setLockedBy("stale-owner");

        repository.succeed(task);

        assertThat(metrics.snapshot()).doesNotContainKey("projection.task.redis.success");
    }

    private ProductProjectionTask task(String target) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setId(1L);
        task.setTarget(target);
        task.setProductId(7L);
        task.setCatalogVersion(42L);
        task.setOperation("ES".equals(target) ? "UPSERT" : "PUBLISH");
        task.setAttemptCount(0);
        task.setStatus("PENDING");
        return task;
    }
}
