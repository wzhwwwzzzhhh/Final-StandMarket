package com.fashion.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.Product;

import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CanonicalProductProjectionCodec {

    private final ObjectMapper objectMapper;

    public CanonicalProductProjectionCodec() {
        this(new ObjectMapper());
    }

    public CanonicalProductProjectionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalProductProjection encode(Product product, long catalogVersion) {
        if (product == null || product.getId() == null || catalogVersion <= 0) {
            throw new IllegalArgumentException("product id and catalog version are required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", product.getId());
        payload.put("name", product.getName());
        payload.put("description", product.getDescription());
        payload.put("categoryId", product.getCategoryId());
        payload.put("price", product.getPrice() == null ? null
                : product.getPrice().setScale(2, RoundingMode.UNNECESSARY));
        payload.put("image", product.getImage());
        payload.put("tag", product.getTag());
        payload.put("status", product.getStatus());
        payload.put("sales", product.getSales() == null ? 0 : product.getSales());
        payload.put("catalogVersion", catalogVersion);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return new CanonicalProductProjection(bytes, sha256(bytes));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("canonical product projection encoding failed", failure);
        }
    }

    public static String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
