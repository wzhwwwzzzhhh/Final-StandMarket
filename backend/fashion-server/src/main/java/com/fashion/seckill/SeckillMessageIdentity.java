package com.fashion.seckill;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SeckillMessageIdentity {

    private SeckillMessageIdentity() {
    }

    public static String quarantineKey(String exchange,
                                       String routingKey,
                                       String contentType,
                                       byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, exchange);
            update(digest, routingKey);
            update(digest, contentType);
            update(digest, body == null ? new byte[0] : body);
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return "INVALID:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        update(digest, value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(4).putInt(value.length).array());
        digest.update(value);
    }
}
