package com.fashion.product;

import com.fashion.entity.Product;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.mapper.ProductCatalogMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ProductCatalogMutationCoordinator {

    private static final long MAX_SAFE_VERSION = 9_007_199_254_740_991L;

    private final ProductCatalogMapper mapper;
    private final CanonicalProductProjectionCodec codec;
    private final AfterCommitRegistrar afterCommitRegistrar;

    public ProductCatalogMutationCoordinator(ProductCatalogMapper mapper,
                                             AfterCommitRegistrar afterCommitRegistrar) {
        this(mapper, new CanonicalProductProjectionCodec(), afterCommitRegistrar);
    }

    ProductCatalogMutationCoordinator(ProductCatalogMapper mapper,
                                      CanonicalProductProjectionCodec codec,
                                      AfterCommitRegistrar afterCommitRegistrar) {
        this.mapper = mapper;
        this.codec = codec;
        this.afterCommitRegistrar = afterCommitRegistrar;
    }

    public long record(Product snapshot, ProductItemState state) {
        if (snapshot == null || snapshot.getId() == null || snapshot.getId() <= 0 || state == null) {
            throw new IllegalArgumentException("product snapshot, id and state are required");
        }
        Long current = mapper.lockListVersion();
        if (current == null || current <= 0 || current >= MAX_SAFE_VERSION) {
            throw new IllegalStateException("catalog version singleton is missing or exhausted");
        }
        if (mapper.advanceListVersion(current) != 1) {
            throw new IllegalStateException("catalog version compare-and-set failed");
        }
        long next = current + 1;
        if (!upsertSucceeded(mapper.upsertRevision(snapshot.getId(), next, state.name()))) {
            throw new IllegalStateException("product revision write failed");
        }
        insert(redisTask(snapshot.getId(), next, state));
        insert(esTask(snapshot, next, state));
        afterCommitRegistrar.register(snapshot.getId(), next);
        return next;
    }

    public boolean recordOrphanDelete(long productId) {
        if (productId <= 0) {
            throw new IllegalArgumentException("product id is required");
        }
        Long current = mapper.lockListVersion();
        if (current == null || current <= 0 || current >= MAX_SAFE_VERSION) {
            throw new IllegalStateException("catalog version singleton is missing or exhausted");
        }
        if (mapper.readRevision(productId) != null) {
            return false;
        }
        Product tombstone = new Product();
        tombstone.setId(productId);
        if (mapper.advanceListVersion(current) != 1) {
            throw new IllegalStateException("catalog version compare-and-set failed");
        }
        long next = current + 1;
        if (!upsertSucceeded(mapper.upsertRevision(productId, next, ProductItemState.DELETED.name()))) {
            throw new IllegalStateException("product orphan revision write failed");
        }
        insert(redisTask(productId, next, ProductItemState.DELETED));
        insert(esTask(tombstone, next, ProductItemState.DELETED));
        afterCommitRegistrar.register(productId, next);
        return true;
    }

    public void ensureTasksForRevision(Product snapshot, long version, ProductItemState state) {
        if (snapshot == null || snapshot.getId() == null || version <= 0 || state == null) {
            throw new IllegalArgumentException("current product revision is required");
        }
        int inserted = mapper.insertProjectionTaskIfAbsent(redisTask(snapshot.getId(), version, state));
        inserted += mapper.insertProjectionTaskIfAbsent(esTask(snapshot, version, state));
        if (inserted > 0) {
            afterCommitRegistrar.register(snapshot.getId(), version);
        }
    }

    private void insert(ProductProjectionTask task) {
        if (mapper.insertProjectionTask(task) != 1) {
            throw new IllegalStateException("product projection task write failed");
        }
    }

    private boolean upsertSucceeded(int affectedRows) {
        // MySQL reports 1 for insert and 2 for a changed ON DUPLICATE KEY UPDATE.
        return affectedRows == 1 || affectedRows == 2;
    }

    private ProductProjectionTask redisTask(long productId, long version, ProductItemState state) {
        byte[] payload = tombstone(productId, version, state);
        return task("REDIS", productId, version, "PUBLISH", payload);
    }

    private ProductProjectionTask esTask(Product snapshot, long version, ProductItemState state) {
        if (state == ProductItemState.ACTIVE) {
            CanonicalProductProjection projection = codec.encode(snapshot, version);
            return task("ES", snapshot.getId(), version, "UPSERT", projection.getPayload());
        }
        return task("ES", snapshot.getId(), version, "DELETE",
                tombstone(snapshot.getId(), version, state));
    }

    private ProductProjectionTask task(String target, long productId, long version,
                                       String operation, byte[] payload) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setTarget(target);
        task.setProductId(productId);
        task.setCatalogVersion(version);
        task.setOperation(operation);
        task.setPayload(new String(payload, StandardCharsets.UTF_8));
        task.setPayloadSha256(CanonicalProductProjectionCodec.sha256(payload));
        task.setStatus("PENDING");
        task.setAttemptCount(0);
        task.setClaimCount(0);
        task.setRepairCount(0);
        return task;
    }

    private byte[] tombstone(long productId, long version, ProductItemState state) {
        return ("{\"productId\":" + productId + ",\"catalogVersion\":" + version
                + ",\"itemState\":\"" + state.name() + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
