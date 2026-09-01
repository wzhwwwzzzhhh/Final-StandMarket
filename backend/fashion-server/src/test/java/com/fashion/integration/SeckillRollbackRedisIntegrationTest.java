package com.fashion.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b5.integration", matches = "true")
@DisplayName("B5 真实 Redis 秒杀回补 Lua 门禁")
class SeckillRollbackRedisIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static DefaultRedisScript<Long> script;
    private String prefix;
    private String stockKey;
    private String usersKey;

    @BeforeAll
    static void connect() throws Exception {
        Map<String, Object> redisSettings = redisSettings();
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(value(redisSettings, "host"));
        configuration.setPort(Integer.parseInt(value(redisSettings, "port")));
        Object database = redisSettings.get("database");
        if (database != null) {
            configuration.setDatabase(Integer.parseInt(String.valueOf(database)));
        }
        Object password = redisSettings.get("password");
        if (password != null && !String.valueOf(password).isEmpty()) {
            configuration.setPassword(RedisPassword.of(String.valueOf(password)));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_rollback.lua")));
        script.setResultType(Long.class);
        try (RedisConnection connection = connectionFactory.getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void createKeys() {
        prefix = "fsm:b5:it:" + UUID.randomUUID().toString().replace("-", "");
        stockKey = prefix + ":stock";
        usersKey = prefix + ":users";
        redis.opsForValue().set(stockKey, "4");
        redis.opsForZSet().add(usersKey, "7", 1D);
    }

    @AfterEach
    void removeKeys() {
        if (prefix != null) {
            redis.delete(Arrays.asList(stockKey, usersKey));
        }
    }

    @Test
    @DisplayName("成功同时恢复库存并移除用户且重放不重复增加")
    void successAndReplayAreIdempotent() {
        assertEquals(1L, execute("1", "7"));
        assertEquals("5", redis.opsForValue().get(stockKey));
        assertNull(redis.opsForZSet().score(usersKey, "7"));

        assertEquals(0L, execute("1", "7"));
        assertEquals("5", redis.opsForValue().get(stockKey));
    }

    @Test
    @DisplayName("并发回补只有一个脚本执行者")
    void concurrentExecutionHasOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.ArrayList<Future<Long>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Callable<Long> task = () -> { ready.countDown(); start.await(); return execute("1", "7"); };
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();
            long winners = 0;
            for (Future<Long> future : futures) {
                if (future.get() == 1L) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals("5", redis.opsForValue().get(stockKey));
            assertNull(redis.opsForZSet().score(usersKey, "7"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("缺 key 与 wrong-type 在任何写入前退出")
    void missingAndWrongTypeLeaveBothKeysUnchanged() {
        redis.delete(stockKey);
        assertEquals(-1L, execute("1", "7"));
        assertNull(redis.opsForValue().get(stockKey));
        assertEquals(1D, redis.opsForZSet().score(usersKey, "7"));

        redis.opsForHash().put(stockKey, "stock", "4");
        assertEquals(-2L, execute("1", "7"));
        assertEquals("4", redis.opsForHash().get(stockKey, "stock"));
        assertEquals(1D, redis.opsForZSet().score(usersKey, "7"));

        redis.delete(stockKey);
        redis.opsForValue().set(stockKey, "4");
        redis.delete(usersKey);
        redis.opsForValue().set(usersKey, "not-a-zset");
        assertEquals(-2L, execute("1", "7"));
        assertEquals("4", redis.opsForValue().get(stockKey));
        assertEquals("not-a-zset", redis.opsForValue().get(usersKey));
    }

    @Test
    @DisplayName("非整数、负值和上溢库存均保持库存与用户令牌不变")
    void invalidStockValuesLeaveBothKeysUnchanged() {
        assertInvalidStock("4.5");
        assertInvalidStock("-1");
        assertInvalidStock("2147483647");
        assertInvalidStock("01");
    }

    @Test
    @DisplayName("非法参数和已缺失用户令牌不会增加库存")
    void invalidArgumentsAndMissingMembershipDoNotWrite() {
        assertEquals(-3L, execute("2", "7"));
        assertUnchanged("4", true);
        assertEquals(-3L, execute("1", "user-7"));
        assertUnchanged("4", true);

        redis.opsForZSet().remove(usersKey, "7");
        assertEquals(0L, execute("1", "7"));
        assertUnchanged("4", false);
    }

    private void assertInvalidStock(String value) {
        redis.opsForValue().set(stockKey, value);
        assertEquals(-3L, execute("1", "7"));
        assertUnchanged(value, true);
    }

    private void assertUnchanged(String stock, boolean memberPresent) {
        assertEquals(stock, redis.opsForValue().get(stockKey));
        assertEquals(memberPresent, redis.opsForZSet().score(usersKey, "7") != null);
    }

    private Long execute(String quantity, String userId) {
        return redis.execute(script, Arrays.asList(stockKey, usersKey), quantity, userId);
    }

    private static Map<String, Object> redisSettings() throws Exception {
        Path config = configPath();
        try (InputStream input = Files.newInputStream(config)) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "redis");
        }
    }

    private static Path configPath() {
        String configured = System.getProperty("b5.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b5.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B5 config is missing");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) {
            throw new IllegalStateException("missing config section " + key);
        }
        return (Map<String, Object>) child;
    }

    private static String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) {
            throw new IllegalStateException("missing config value " + key);
        }
        return String.valueOf(result);
    }
}
