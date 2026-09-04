package com.fashion.product;

public enum ProductMutationKind {
    NO_OP,
    STOCK_ONLY,
    CATALOG_ONLY,
    MIXED;

    public boolean changesCatalog() {
        return this == CATALOG_ONLY || this == MIXED;
    }

    public boolean changesAnything() {
        return this != NO_OP;
    }
}
