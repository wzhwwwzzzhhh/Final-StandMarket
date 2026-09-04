package com.fashion.product;

public interface ProductProjectionInventory {
    IndexedProductProjection read(long productId);
    ProjectionScanPage scanAfter(String cursor, int limit);
}
