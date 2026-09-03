package com.fashion.integration;

import com.fashion.seckill.SeckillReservationService;
import com.fashion.seckill.SeckillRedisScanPageReader;
import com.fashion.seckill.SeckillReservationScanner;
import com.fashion.seckill.SeckillReservationSnapshot;
import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@DisplayName("B6 Redis 7 reservation token 真实行为")
class B6ReservationRedisIntegrationTest {
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static SeckillReservationService reservationService;
    private static Long couponId;
    private static final Long USER_ID = 7000001L;

    @BeforeAll
    static void connect() throws Exception {
        Map<String, Object> settings = redisSettings();
        B6IntegrationSafety.requireLoopback(value(settings, "host"), "Redis");
        B6IntegrationSafety.requireDedicatedRedisDatabase(value(settings, "database"));
        B6IntegrationSafety.requireExclusiveRedisDatabase(value(settings, "exclusive"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(value(settings, "host"));
        configuration.setPort(Integer.parseInt(value(settings, "port")));
        configuration.setDatabase(Integer.parseInt(value(settings, "database")));
        String password = value(settings, "password");
        if (!password.isEmpty()) configuration.setPassword(RedisPassword.of(password));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        Properties server = connectionFactory.getConnection().serverCommands().info("server");
        String version = server == null ? null : server.getProperty("redis_version");
        B6IntegrationSafety.requireRedisVersion(version);
        B6IntegrationSafety.requireEmptyRedisDatabase(
                connectionFactory.getConnection().serverCommands().dbSize());
        reservationService = new SeckillReservationService(redis);
        couponId = 800000000000L + ThreadLocalRandom.current().nextInt(1, 1000000);
        cleanup();
    }

    @AfterAll
    static void disconnect() {
        try {
            if (redis != null && couponId != null) cleanup();
        } finally {
            if (connectionFactory != null) connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("预扣写入 token/registry，错误 token 不回补，正确 token 只回补一次")
    void tokenAwareRollbackIsIdempotent() {
        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(stock(), "1");
        redis.opsForValue().set(start(), String.valueOf(now - 10));
        redis.opsForValue().set(end(), String.valueOf(now + 600));

        assertEquals(SeckillReservationService.ReserveResult.RESERVED,
                reservationService.reserve(couponId, USER_ID, "9001001", now));
        assertEquals("0", redis.opsForValue().get(stock()));
        assertEquals("9001001", redis.opsForHash().get(reservations(), String.valueOf(USER_ID)));
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(registry(), String.valueOf(couponId))));

        assertEquals(SeckillReservationService.RollbackResult.TOKEN_MISMATCH,
                reservationService.rollback(couponId, USER_ID, "9001002"));
        assertEquals("0", redis.opsForValue().get(stock()));

        assertEquals(SeckillReservationService.RollbackResult.APPLIED,
                reservationService.rollback(couponId, USER_ID, "9001001"));
        assertEquals("1", redis.opsForValue().get(stock()));
        assertFalse(Boolean.TRUE.equals(redis.opsForHash().hasKey(reservations(), String.valueOf(USER_ID))));
        assertEquals(SeckillReservationService.RollbackResult.ALREADY_APPLIED,
                reservationService.rollback(couponId, USER_ID, "9001001"));
        assertEquals("1", redis.opsForValue().get(stock()));
    }

    @Test
    @DisplayName("真实 SSCAN/HSCAN/ZSCAN page reader 发现 reservation 且不物化整库")
    void cursorScannerReadsRealRedisPages() {
        cleanup();
        long now = Instant.now().getEpochSecond();
        redis.opsForHash().put(reservations(), String.valueOf(USER_ID), "9002001");
        redis.opsForZSet().add(users(), String.valueOf(USER_ID), now - 600);
        redis.opsForSet().add(registry(), String.valueOf(couponId));
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.singletonList(couponId));

        SeckillRedisScanPageReader reader = new SeckillRedisScanPageReader(redis);
        assertEquals(Collections.singletonList(String.valueOf(couponId)),
                reader.scanRegistry("0", 1).getEntries());
        assertEquals(1, reader.scanReservations(couponId, "0", 1).getEntries().size());
        assertEquals(1, reader.scanUsers(couponId, "0", 1).getEntries().size());
        List<SeckillReservationSnapshot> snapshots = new SeckillReservationScanner(
                reader, candidates).scan(1);

        assertEquals(1, snapshots.size());
        assertEquals("9002001", snapshots.get(0).getOrderNumber());
        assertTrue(snapshots.get(0).isHashTokenPresent());
        assertTrue(snapshots.get(0).isZsetMemberPresent());
    }

    private static void cleanup() {
        redis.opsForSet().remove(registry(), String.valueOf(couponId));
        redis.delete(Arrays.asList(stock(), start(), end(), users(), reservations()));
    }

    private static String stock() { return "seckill:coupon:stock:" + couponId; }
    private static String start() { return "seckill:coupon:startTime:" + couponId; }
    private static String end() { return "seckill:coupon:endTime:" + couponId; }
    private static String users() { return "seckill:coupon:users:" + couponId; }
    private static String reservations() { return "seckill:coupon:reservations:" + couponId; }
    private static String registry() { return "seckill:coupon:reservation:index"; }

    private static Map<String, Object> redisSettings() throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "redis");
        }
    }

    private static Path configPath() {
        String configured = System.getProperty("b6.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b6.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("B6 config is missing");
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) throw new IllegalStateException("missing config section " + key);
        return (Map<String, Object>) child;
    }

    private static String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        return result == null ? "" : String.valueOf(result);
    }
}
