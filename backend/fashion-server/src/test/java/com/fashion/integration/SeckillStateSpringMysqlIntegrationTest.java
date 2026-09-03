package com.fashion.integration;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.dto.SeckillCancelResponse;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.service.SeckillOrderService;
import com.fashion.service.impl.SeckillCancellationTransaction;
import com.fashion.service.impl.SeckillOrderServiceImpl;
import com.fashion.seckill.SeckillCompensationService;
import com.fashion.seckill.SeckillCompensationExecutor;
import com.fashion.seckill.SeckillReservationService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b5.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B5 Spring/MyBatis/MySQL 秒杀状态事务门禁")
class SeckillStateSpringMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b5_state_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private SeckillCancellationTransaction cancellation;
    private SeckillOrderService seckillOrderService;
    private SeckillOrderMapper orderMapper;
    private StringRedisTemplate redisTemplate;
    private DataSource dataSource;

    @BeforeAll
    void createSchemaAndContext() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b5_state_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b5.test.jdbc-url", schemaUrl);
        properties.put("b5.test.username", username);
        properties.put("b5.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b5Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();
        cancellation = context.getBean(SeckillCancellationTransaction.class);
        seckillOrderService = context.getBean(SeckillOrderService.class);
        orderMapper = context.getBean(SeckillOrderMapper.class);
        redisTemplate = context.getBean(StringRedisTemplate.class);
        dataSource = context.getBean(DataSource.class);
        assertTrue(AopUtils.isAopProxy(cancellation));
        assertTrue(AopUtils.isAopProxy(seckillOrderService));
    }

    @AfterAll
    void closeContextAndDropSchema() throws Exception {
        if (context != null) {
            context.close();
        }
        if (schema != null) {
            validateSchema(schema);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void resetTables() throws Exception {
        reset(redisTemplate);
        execute("DROP TABLE IF EXISTS seckill_order");
        execute("DROP TABLE IF EXISTS seckill_coupon");
        execute("CREATE TABLE seckill_coupon (id BIGINT PRIMARY KEY, stock INT NOT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, "
                + "coupon_id BIGINT NOT NULL, order_number VARCHAR(64) NOT NULL, status INT NOT NULL DEFAULT 1, "
                + "create_time DATETIME, pay_time DATETIME, UNIQUE KEY uk_order_number(order_number)) ENGINE=InnoDB");
        execute("INSERT INTO seckill_coupon VALUES (19,4)");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (7,19,'SEC-A',1,NOW()),(8,19,'SEC-B',1,NOW())");
    }

    @Test
    @DisplayName("支付与取消并发只有一个 CAS 终态且库存与终态一致")
    void paymentAndCancellationHaveOneWinner() throws Exception {
        List<Object> results = race(
                () -> orderMapper.markPaid("SEC-A", LocalDateTime.now()),
                () -> cancellation.cancelTrusted("SEC-A"));

        int status = scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'");
        int stock = scalarInt("SELECT stock FROM seckill_coupon WHERE id=19");
        if (status == 2) {
            assertEquals(1, results.get(0));
            assertNull(results.get(1));
            assertEquals(4, stock);
        } else {
            assertEquals(3, status);
            assertEquals(0, results.get(0));
            assertNotNull(results.get(1));
            assertEquals(5, stock);
        }
    }

    @Test
    @DisplayName("主动与超时重复取消只回补一次 MySQL 库存")
    void duplicateCancellationRestoresOnce() throws Exception {
        SeckillCancelCommand first = cancellation.cancelTrusted("SEC-A");
        SeckillCancelCommand replay = cancellation.cancelTimeout(1L);

        assertNotNull(first);
        assertNull(replay);
        assertEquals(3, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'"));
        assertEquals(5, scalarInt("SELECT stock FROM seckill_coupon WHERE id=19"));
    }

    @Test
    @DisplayName("库存回补失败使订单状态 CAS 在真实事务中回滚")
    void stockRestoreFailureRollsBackCancellation() throws Exception {
        execute("DELETE FROM seckill_coupon WHERE id=19");

        assertThrows(RuntimeException.class, () -> cancellation.cancelTrusted("SEC-A"));

        assertEquals(1, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'"));
    }

    @Test
    @DisplayName("用户归属条件阻止越权取消与库存写入")
    void userOwnershipIsPartOfCancellationCas() throws Exception {
        assertNull(cancellation.cancelForUser("SEC-B", 7L));
        assertEquals(1, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-B'"));
        assertEquals(4, scalarInt("SELECT stock FROM seckill_coupon WHERE id=19"));
    }

    @Test
    @DisplayName("REQUIRES_NEW 在外层事务回滚后仍保留已提交取消事实")
    void requiresNewCommitSurvivesOuterRollback() throws Exception {
        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(IllegalStateException.class, () -> outer.execute(status -> {
            assertNotNull(cancellation.cancelTrusted("SEC-A"));
            throw new IllegalStateException("fail after inner commit");
        }));

        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive());
        assertEquals(3, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'"));
        assertEquals(5, scalarInt("SELECT stock FROM seckill_coupon WHERE id=19"));
    }

    @Test
    @DisplayName("Redis Lua 只在 MySQL 提交可见且调用线程无活动事务后执行")
    void redisRollbackRunsAfterMysqlCommitAndOutsideTransaction() throws Exception {
        AtomicBoolean committedStateObserved = new AtomicBoolean(false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"), eq("7")))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(3, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'"));
                    assertEquals(5, scalarInt("SELECT stock FROM seckill_coupon WHERE id=19"));
                    committedStateObserved.set(true);
                    return 1L;
                });

        SeckillCancelResponse response = seckillOrderService.cancelOrder("SEC-A");

        assertEquals(SeckillCancelResponse.CANCELLED, response.getOutcome());
        assertTrue(committedStateObserved.get());
    }

    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> wrappedFirst = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<Object> wrappedSecond = () -> { ready.countDown(); start.await(); return second.call(); };
            Future<Object> one = pool.submit(wrappedFirst);
            Future<Object> two = pool.submit(wrappedSecond);
            ready.await();
            start.countDown();
            return Arrays.asList(one.get(), two.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Map<String, Object> datasourceSettings() throws Exception {
        Path config = configPath();
        try (InputStream input = Files.newInputStream(config)) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "datasource");
        }
    }

    private Path configPath() {
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
    private Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) {
            throw new IllegalStateException("missing config section " + key);
        }
        return (Map<String, Object>) child;
    }

    private String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) {
            throw new IllegalStateException("missing config value " + key);
        }
        return String.valueOf(result);
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B5 schema name");
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {SeckillOrderMapper.class, SeckillCouponMapper.class})
    static class SpringMysqlConfig {
        @Bean
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(environment.getRequiredProperty("b5.test.jdbc-url"));
            source.setUsername(environment.getRequiredProperty("b5.test.username"));
            source.setPassword(environment.getRequiredProperty("b5.test.password"));
            return source;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath:mapper/*.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SeckillCancellationTransaction cancellationTransaction() {
            return new SeckillCancellationTransaction();
        }

        @Bean
        SeckillCompensationService seckillCompensationService() {
            return mock(SeckillCompensationService.class);
        }

        @Bean
        SeckillCompensationExecutor seckillCompensationExecutor(StringRedisTemplate redisTemplate) {
            SeckillCompensationExecutor executor = mock(SeckillCompensationExecutor.class);
            when(executor.execute(any(String.class))).thenAnswer(invocation -> {
                redisTemplate.execute(RedisScript.of("return 1", Long.class),
                        Arrays.asList("b5:stock", "b5:users", "b5:reservations", "b5:index"),
                        "1", "7");
                return SeckillReservationService.RollbackResult.APPLIED;
            });
            return executor;
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        SeckillOrderServiceImpl seckillOrderService() {
            return new SeckillOrderServiceImpl();
        }
    }
}
