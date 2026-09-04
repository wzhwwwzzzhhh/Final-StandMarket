package com.fashion.product;

public final class ProductCatalogSourceUnavailableException extends RuntimeException {
    public static final String CODE = "PRODUCT_CATALOG_SOURCE_UNAVAILABLE";

    public ProductCatalogSourceUnavailableException(String message) {
        super(message);
    }

    public ProductCatalogSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
