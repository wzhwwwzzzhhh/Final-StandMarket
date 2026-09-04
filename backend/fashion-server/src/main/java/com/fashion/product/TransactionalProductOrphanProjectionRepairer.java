package com.fashion.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalProductOrphanProjectionRepairer implements ProductOrphanProjectionRepairer {
    private final ProductCatalogMutationCoordinator coordinator;

    public TransactionalProductOrphanProjectionRepairer(ProductCatalogMutationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createDeleteForOrphan(long productId) {
        return coordinator.recordOrphanDelete(productId);
    }
}
