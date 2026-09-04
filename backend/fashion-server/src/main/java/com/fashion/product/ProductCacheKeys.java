package com.fashion.product;

public final class ProductCacheKeys {

    public static final String LIST_PUBLISHED_VERSION = "cache:product:list:v2:published-version";

    private ProductCacheKeys() {
    }

    public static String list(long version, NormalizedProductQuery query) {
        requireVersion(version);
        if (query == null) {
            throw new IllegalArgumentException("normalized query is required");
        }
        return "cache:product:list:v2:" + version + ":" + query.querySha256();
    }

    public static String detail(long productId, long version) {
        requireProductId(productId);
        requireVersion(version);
        return "cache:product:detail:v2:" + productId + ":" + version;
    }

    public static String detailPublishedVersion(long productId) {
        requireProductId(productId);
        return "cache:product:detail:v2:" + productId + ":published-version";
    }

    public static String detailLock(long productId, long version) {
        requireProductId(productId);
        requireVersion(version);
        return "lock:product:detail:v2:" + productId + ":" + version;
    }

    private static void requireProductId(long productId) {
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
    }

    private static void requireVersion(long version) {
        if (version <= 0 || version > 9_007_199_254_740_991L) {
            throw new IllegalArgumentException("catalog version is outside the safe integer domain");
        }
    }
}
