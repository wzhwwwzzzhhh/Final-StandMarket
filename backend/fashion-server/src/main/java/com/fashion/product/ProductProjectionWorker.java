package com.fashion.product;

import com.fashion.entity.ProductCatalogRevision;
import com.fashion.entity.ProductProjectionTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class ProductProjectionWorker {

    private final ProductProjectionTaskRepository repository;
    private final Map<String, ProductProjectionDelivery> deliveries = new HashMap<>();
    private final ProductCatalogAuthority authority;
    private final ProductProjectionProperties properties;
    private final Executor wakeupExecutor;

    @Autowired
    public ProductProjectionWorker(ProductProjectionTaskRepository repository,
                                   List<ProductProjectionDelivery> deliveries,
                                   ProductCatalogAuthority authority,
                                   ProductProjectionProperties properties,
                                   @Qualifier("productProjectionWakeupExecutor") Executor wakeupExecutor) {
        this.repository = repository;
        for (ProductProjectionDelivery delivery : deliveries) {
            this.deliveries.put(delivery.target(), delivery);
        }
        this.authority = authority;
        this.properties = properties;
        this.wakeupExecutor = wakeupExecutor;
    }

    ProductProjectionWorker(ProductProjectionTaskRepository repository,
                            List<ProductProjectionDelivery> deliveries,
                            ProductCatalogAuthority authority,
                            ProductProjectionProperties properties) {
        this(repository, deliveries, authority, properties, Runnable::run);
    }

    @Scheduled(fixedDelayString = "${fashion.product-projection.poll-delay:1000}")
    public void poll() {
        drain("REDIS");
        drain("ES");
    }

    @EventListener
    public void onReady(ProductProjectionReadyEvent ignored) {
        // The transaction afterCommit thread only enqueues work. Polling remains the durable fallback.
        wakeupExecutor.execute(() -> {
            processOneSafely("REDIS");
            processOneSafely("ES");
        });
    }

    private void processOneSafely(String target) {
        try {
            processOne(target);
        } catch (RuntimeException failure) {
            log.warn("B8 projection wakeup failed for target={}, type={}; scheduled polling remains active",
                    target, failure.getClass().getSimpleName());
        }
    }

    void drain(String target) {
        for (int i = 0; i < properties.getBatchSize(); i++) {
            if (!processOne(target)) {
                return;
            }
        }
    }

    public boolean processOne(String target) {
        ProductProjectionDelivery delivery = deliveries.get(target);
        if (delivery == null) {
            throw new IllegalArgumentException("unknown product projection target");
        }
        ProductProjectionTask task = repository.claim(target);
        if (task == null) {
            return false;
        }
        try {
            if ("ES".equals(target) && !repository.ownsDeliveryLease(task)) {
                log.warn("B8 skipped ES delivery after initial ownership fence taskId={}",
                        task.getId());
                return true;
            }
            if ("ES".equals(target) && shouldSupersede(task)) {
                repository.supersede(task);
                return true;
            }
            if ("ES".equals(target) && !repository.ownsCurrentDelivery(task)) {
                log.warn("B8 skipped ES delivery after ownership/current-revision fence taskId={}", task.getId());
                return true;
            }
            delivery.deliver(task);
            repository.succeed(task);
        } catch (ProjectionDeliveryException failure) {
            fail(task, failure.isRetryable(), failure.getSafeSummary());
        } catch (RuntimeException failure) {
            fail(task, true, "runtime_" + failure.getClass().getSimpleName());
        }
        return true;
    }

    private boolean shouldSupersede(ProductProjectionTask task) {
        ProductCatalogRevision revision = authority.readRevision(task.getProductId());
        if (revision == null || revision.getItemVersion() == null) {
            throw ProjectionDeliveryException.permanent("missing_product_revision");
        }
        if (revision.getItemVersion() > task.getCatalogVersion()) {
            return true;
        }
        if (revision.getItemVersion() < task.getCatalogVersion()) {
            throw ProjectionDeliveryException.retryable("revision_behind_task");
        }
        boolean active = "ACTIVE".equals(revision.getItemState());
        if ((active && !"UPSERT".equals(task.getOperation()))
                || (!active && !"DELETE".equals(task.getOperation()))) {
            throw ProjectionDeliveryException.permanent("revision_operation_mismatch");
        }
        return false;
    }

    private void fail(ProductProjectionTask task, boolean retryable, String summary) {
        int attempts = task.getAttemptCount() == null ? properties.getMaxAttempts() : task.getAttemptCount();
        if (!retryable || attempts >= properties.getMaxAttempts()) {
            repository.terminal(task, summary);
            return;
        }
        repository.retry(task, LocalDateTime.now().plus(backoff(attempts)), summary);
    }

    private Duration backoff(int attempt) {
        long base = properties.getRetryBase().toMillis();
        long maximum = properties.getRetryMax().toMillis();
        int shift = Math.max(0, Math.min(20, attempt - 1));
        long exponential = base > (Long.MAX_VALUE >> shift) ? maximum : base << shift;
        long bounded = Math.min(exponential, maximum);
        long jitterMax = properties.getRetryJitter().toMillis();
        long jitter = jitterMax == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterMax + 1);
        return Duration.ofMillis(bounded + jitter);
    }
}
