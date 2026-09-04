package com.fashion.product;

public final class IndexedProductProjection {
    private final long productId;
    private final long catalogVersion;
    private final String projectionHash;

    public IndexedProductProjection(long productId, long catalogVersion, String projectionHash) {
        this.productId = productId;
        this.catalogVersion = catalogVersion;
        this.projectionHash = projectionHash;
    }

    public long getProductId() { return productId; }
    public long getCatalogVersion() { return catalogVersion; }
    public String getProjectionHash() { return projectionHash; }
}
