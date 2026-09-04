package com.fashion.product;

import com.fashion.entity.ProductCatalogRevision;

public final class DetailVersionDecision {
    private final ProductCatalogRevision revision;
    private final boolean cacheAllowed;

    DetailVersionDecision(ProductCatalogRevision revision, boolean cacheAllowed) {
        this.revision = revision;
        this.cacheAllowed = cacheAllowed;
    }

    public ProductCatalogRevision getRevision() { return revision; }
    public boolean isCacheAllowed() { return cacheAllowed; }
}
