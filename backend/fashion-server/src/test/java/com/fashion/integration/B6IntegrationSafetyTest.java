package com.fashion.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class B6IntegrationSafetyTest {

    @Test
    void acceptsOnlyRedisSevenZeroPatchVersions() {
        assertDoesNotThrow(() -> B6IntegrationSafety.requireRedisVersion("7.0.15"));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireRedisVersion("7.2.14"));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireRedisVersion("6.2.14"));
    }

    @Test
    void requiresExplicitExclusiveRedisConfiguration() {
        assertDoesNotThrow(() -> B6IntegrationSafety.requireExclusiveRedisDatabase("true"));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireExclusiveRedisDatabase("false"));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireExclusiveRedisDatabase(""));
    }

    @Test
    void refusesNonEmptyOrUnknownRedisDatabase() {
        assertDoesNotThrow(() -> B6IntegrationSafety.requireEmptyRedisDatabase(0L));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireEmptyRedisDatabase(1L));
        assertThrows(IllegalStateException.class,
                () -> B6IntegrationSafety.requireEmptyRedisDatabase(null));
    }
}
