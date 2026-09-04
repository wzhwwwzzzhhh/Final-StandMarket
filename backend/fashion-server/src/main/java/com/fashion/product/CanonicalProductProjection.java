package com.fashion.product;

public final class CanonicalProductProjection {
    private final byte[] payload;
    private final String sha256;

    CanonicalProductProjection(byte[] payload, String sha256) {
        this.payload = payload.clone();
        this.sha256 = sha256;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public String getSha256() {
        return sha256;
    }
}
