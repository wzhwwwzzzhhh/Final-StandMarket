package com.fashion.product;

import com.fashion.entity.ProductCatalogRevision;

/** MySQL-backed authority used by cache gates. */
public interface ProductCatalogAuthority {
    long readListVersion();
    ProductCatalogRevision readRevision(long productId);
}
