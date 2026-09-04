package com.fashion.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class SpringAfterCommitRegistrar implements AfterCommitRegistrar {

    private final ApplicationEventPublisher publisher;

    public SpringAfterCommitRegistrar(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void register(long productId, long catalogVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("product projection must be registered inside a Spring transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.publishEvent(new ProductProjectionReadyEvent(productId, catalogVersion));
                } catch (RuntimeException failure) {
                    log.warn("B8 after-commit wakeup failed for productId={}, version={}, type={}",
                            productId, catalogVersion, failure.getClass().getSimpleName());
                }
            }
        });
    }
}
