package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;
import com.fashion.mapper.ProductCatalogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class MybatisProductProjectionTaskRepository implements ProductProjectionTaskRepository {

    private final ProductCatalogMapper mapper;
    private final ProductProjectionProperties properties;
    private final ProductProjectionMetrics metrics;

    public MybatisProductProjectionTaskRepository(ProductCatalogMapper mapper,
                                                   ProductProjectionProperties properties) {
        this(mapper, properties, new ProductProjectionMetrics());
    }

    @Autowired
    public MybatisProductProjectionTaskRepository(ProductCatalogMapper mapper,
                                                   ProductProjectionProperties properties,
                                                   ProductProjectionMetrics metrics) {
        this.mapper = mapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean ownsDeliveryLease(ProductProjectionTask task) {
        if (task == null || task.getId() == null || task.getLockedBy() == null) {
            return false;
        }
        LocalDateTime requiredUntil = requiredUntil();
        return mapper.ownsProjectionDeliveryLease(
                task.getId(), task.getLockedBy(), requiredUntil) == 1;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean ownsCurrentDelivery(ProductProjectionTask task) {
        if (task == null || task.getId() == null || task.getLockedBy() == null) {
            return false;
        }
        LocalDateTime requiredUntil = requiredUntil();
        return mapper.ownsCurrentProjectionDelivery(
                task.getId(), task.getLockedBy(), requiredUntil) == 1;
    }

    private LocalDateTime requiredUntil() {
        return LocalDateTime.now()
                .plus(properties.requestTimeoutBudget())
                .plus(properties.getLeaseMargin());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductProjectionTask claim(String target) {
        mapper.terminalizeExhausted(target, properties.getMaxAttempts());
        ProductProjectionTask candidate = mapper.lockNextClaimable(target, properties.getMaxAttempts());
        if (candidate == null) {
            return null;
        }
        if ("PROCESSING".equals(candidate.getStatus())) {
            metrics.increment("projection.task.lease_recovered");
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime lockedUntil = LocalDateTime.now().plus(properties.getLease());
        if ("ES".equals(target)
                && mapper.claimEsRevisionLease(candidate.getProductId(), token, lockedUntil) != 1) {
            return null;
        }
        if (mapper.markTaskProcessing(candidate.getId(), token, lockedUntil,
                properties.getMaxAttempts()) != 1) {
            if ("ES".equals(target)) {
                mapper.releaseEsRevisionLease(candidate.getProductId(), token);
            }
            return null;
        }
        ProductProjectionTask claimed = mapper.readProjectionTask(candidate.getId());
        if (claimed == null || !token.equals(claimed.getLockedBy())) {
            if ("ES".equals(target)) {
                mapper.releaseEsRevisionLease(candidate.getProductId(), token);
            }
            throw new IllegalStateException("claimed product projection task could not be re-read");
        }
        return claimed;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(ProductProjectionTask task) {
        complete(task, "SUCCEEDED", null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(ProductProjectionTask task, LocalDateTime nextRetryAt, String safeSummary) {
        complete(task, "RETRY_WAIT", nextRetryAt, safeSummary);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminal(ProductProjectionTask task, String safeSummary) {
        complete(task, "FAILED_TERMINAL", null, safeSummary);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void supersede(ProductProjectionTask task) {
        complete(task, "SUPERSEDED", null, null);
    }

    private void complete(ProductProjectionTask task, String status,
                          LocalDateTime nextRetryAt, String summary) {
        if (task == null || task.getId() == null || task.getLockedBy() == null) {
            throw new IllegalArgumentException("claimed task ownership is required");
        }
        int changed = mapper.completeProjectionTask(task.getId(), task.getLockedBy(), status,
                nextRetryAt, summary);
        if ("ES".equals(task.getTarget())) {
            mapper.releaseEsRevisionLease(task.getProductId(), task.getLockedBy());
        }
        if (changed == 0) {
            log.warn("B8 ignored stale product projection completion taskId={}, status={}",
                    task.getId(), status);
            return;
        }
        String target = task.getTarget().toLowerCase(java.util.Locale.ROOT);
        if ("SUCCEEDED".equals(status)) {
            metrics.increment("projection.task." + target + ".success");
        } else if ("RETRY_WAIT".equals(status)) {
            metrics.increment("projection.task." + target + ".retry");
        } else if ("FAILED_TERMINAL".equals(status)) {
            metrics.increment("projection.task." + target + ".terminal");
        } else if ("SUPERSEDED".equals(status)) {
            metrics.increment("projection.task." + target + ".superseded");
        }
    }
}
