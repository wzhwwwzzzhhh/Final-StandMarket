package com.fashion.integration;

final class B6IntegrationSafety {
    private B6IntegrationSafety() { }

    static void requireLoopback(String host, String dependency) {
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host))) {
            throw new IllegalStateException("B6 integration refuses non-loopback " + dependency);
        }
    }

    static void requireDedicatedRedisDatabase(String database) {
        if (!"15".equals(database)) {
            throw new IllegalStateException("B6 integration requires dedicated Redis database 15");
        }
    }

    static void requireExclusiveRedisDatabase(String exclusive) {
        if (!Boolean.parseBoolean(exclusive)) {
            throw new IllegalStateException("B6 integration requires redis.exclusive=true");
        }
    }

    static void requireEmptyRedisDatabase(Long databaseSize) {
        if (databaseSize == null || databaseSize.longValue() != 0L) {
            throw new IllegalStateException("B6 integration requires an empty dedicated Redis database");
        }
    }

    static void requireRedisVersion(String version) {
        if (version == null || !version.matches("^7\\.0\\.\\d+(?:[-+].*)?$")) {
            throw new IllegalStateException("B6 integration requires Redis 7.0.x");
        }
    }
}
