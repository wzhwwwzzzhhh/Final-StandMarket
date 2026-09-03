package com.fashion.seckill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("B6 after-commit 发布隔离")
class B6AfterCommitDispatcherTest {
    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("提交后 publisher 异常只留恢复事实，不反向污染已提交消费结果")
    void afterCommitFailureDoesNotEscapeSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        new SeckillAfterCommitDispatcher().run(() -> {
            throw new IllegalStateException("broker unavailable");
        });
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

        assertDoesNotThrow(synchronization::afterCommit);
    }
}
