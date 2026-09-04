package com.fashion.product;

import com.fashion.entity.Product;
import com.fashion.entity.ProductCatalogRevision;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.mapper.ProductCatalogMapper;
import com.fashion.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalProductBaselineProjectionRepairer implements ProductBaselineProjectionRepairer {
    private final ProductCatalogMapper catalogMapper;
    private final ProductMapper productMapper;
    private final ProductCatalogMutationCoordinator coordinator;

    public TransactionalProductBaselineProjectionRepairer(ProductCatalogMapper catalogMapper,
                                                           ProductMapper productMapper,
                                                           ProductCatalogMutationCoordinator coordinator) {
        this.catalogMapper = catalogMapper;
        this.productMapper = productMapper;
        this.coordinator = coordinator;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductProjectionTask ensureCurrentEsTask(long productId) {
        ProductCatalogRevision revision = catalogMapper.readRevisionForUpdate(productId);
        if (revision == null) throw new IllegalStateException("product revision is missing");
        ProductItemState state = ProductItemState.valueOf(revision.getItemState());
        Product product = productMapper.getByIdIncludingInactive(productId);
        if (state == ProductItemState.ACTIVE
                && (product == null || !Integer.valueOf(1).equals(product.getStatus()))) {
            throw new IllegalStateException("ACTIVE product snapshot is unavailable");
        }
        if (state == ProductItemState.INACTIVE
                && (product == null || !Integer.valueOf(0).equals(product.getStatus()))) {
            throw new IllegalStateException("INACTIVE product snapshot is unavailable");
        }
        if (state == ProductItemState.DELETED && product != null) {
            throw new IllegalStateException("DELETED product still exists");
        }
        if (product == null) {
            product = new Product();
            product.setId(productId);
        }
        coordinator.ensureTasksForRevision(product, revision.getItemVersion(), state);
        ProductProjectionTask result = catalogMapper.readCurrentEsTask(productId);
        if (result == null) throw new IllegalStateException("baseline ES task was not materialized");
        return result;
    }
}
