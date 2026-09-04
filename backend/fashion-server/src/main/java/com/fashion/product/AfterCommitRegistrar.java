package com.fashion.product;

public interface AfterCommitRegistrar {
    void register(long productId, long catalogVersion);
}
