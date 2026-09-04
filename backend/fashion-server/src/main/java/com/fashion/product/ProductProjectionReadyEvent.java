package com.fashion.product;

public final class ProductProjectionReadyEvent {
    private final long productId;
    private final long catalogVersion;

    public ProductProjectionReadyEvent(long productId, long catalogVersion) {
        this.productId = productId;
        this.catalogVersion = catalogVersion;
    }

    public long getProductId() { return productId; }
    public long getCatalogVersion() { return catalogVersion; }
}
