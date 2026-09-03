package com.fashion.seckill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Component
@Slf4j
public class SeckillAfterCommitDispatcher {
    public void run(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("after-commit action requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    log.error("SECKILL_AFTER_COMMIT_PUBLISH_DEFERRED result=RECOVERY_PENDING");
                }
            }
        });
    }
}
