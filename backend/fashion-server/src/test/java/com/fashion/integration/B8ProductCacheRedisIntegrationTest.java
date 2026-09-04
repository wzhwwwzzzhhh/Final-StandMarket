package com.fashion.integration;

import com.fashion.product.ProductCacheKeys;
import com.fashion.utils.CacheClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class B8ProductCacheRedisIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private CacheClient cache;
    private final List<String> keys = new ArrayList<>();

    @BeforeAll
    void connect() throws Exception {
        Map<String, Object> settings = B8IntegrationSettings.section("redis");
        String host = B8IntegrationSettings.value(settings, "host");
        B8IntegrationSettings.requireLoopback(host, "Redis");
        assertEquals("true", B8IntegrationSettings.exclusive("redis", settings),
                "B8 requires an explicitly exclusive Redis database");
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(Integer.parseInt(B8IntegrationSettings.value(settings, "port")));
        configuration.setDatabase(Integer.parseInt(B8IntegrationSettings.value(settings, "database")));
        String password = B8IntegrationSettings.value(settings, "password");
        if (!password.isEmpty()) configuration.setPassword(RedisPassword.of(password));
        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        Properties info = factory.getConnection().serverCommands().info("server");
        assertNotNull(info);
        assertTrue(info.getProperty("redis_version").startsWith("7.0."));
        assertEquals(0L, factory.getConnection().serverCommands().dbSize(),
                "exclusive B8 Redis database must start empty");
        cache = new CacheClient(redis);
    }

    @AfterEach
    void cleanExactKeys() {
        if (!keys.isEmpty()) redis.delete(new ArrayList<>(keys));
        keys.clear();
    }

    @AfterAll
    void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void maxPublishNeverMovesBackwardsAndFencedValueHasMillisecondTtl() {
        String run = UUID.randomUUID().toString().replace("-", "");
        String published = "fsm:b8:it:" + run + ":published";
        String lock = "fsm:b8:it:" + run + ":lock";
        String value = "fsm:b8:it:" + run + ":value";
        keys.addAll(Arrays.asList(published, lock, value));

        assertEquals(1L, cache.publishMaxVersion(published, 42L));
        assertEquals(0L, cache.publishMaxVersion(published, 41L));
        assertEquals("42", redis.opsForValue().get(published));
        String token = cache.tryLockToken(lock, Duration.ofSeconds(2));
        assertNotNull(token);
        assertEquals(1L, cache.fencedSet(lock, published, value, token, 42L,
                "payload", Duration.ofMillis(750)));
        assertEquals("payload", redis.opsForValue().get(value));
        Long ttl = redis.getExpire(value, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 750);
    }

    @Test
    void expiredOwnerCannotDeleteSuccessorLease() throws Exception {
        String lock = "fsm:b8:it:" + UUID.randomUUID().toString().replace("-", "") + ":lock";
        keys.add(lock);
        String first = cache.tryLockToken(lock, Duration.ofMillis(60));
        assertNotNull(first);
        Thread.sleep(120L);
        String second = cache.tryLockToken(lock, Duration.ofSeconds(2));
        assertNotNull(second);

        assertEquals(0L, cache.releaseLock(lock, first));
        assertEquals(second, redis.opsForValue().get(lock));
        assertEquals(1L, cache.releaseLock(lock, second));
    }
}
