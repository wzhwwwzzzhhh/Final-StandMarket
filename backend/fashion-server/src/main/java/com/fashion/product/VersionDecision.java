package com.fashion.product;

public final class VersionDecision {
    private final long version;
    private final boolean cacheAllowed;

    VersionDecision(long version, boolean cacheAllowed) {
        this.version = version;
        this.cacheAllowed = cacheAllowed;
    }

    public long getVersion() {
        return version;
    }

    public boolean isCacheAllowed() {
        return cacheAllowed;
    }
}
