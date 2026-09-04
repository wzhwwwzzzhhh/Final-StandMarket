package com.fashion.product;

public interface ProductOrphanProjectionRepairer {
    boolean createDeleteForOrphan(long productId);
}
