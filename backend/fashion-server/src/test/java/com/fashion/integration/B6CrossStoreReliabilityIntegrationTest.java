package com.fashion.integration;

import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import com.fashion.seckill.SeckillCompensationExecutor;
import com.fashion.seckill.SeckillCompensationService;
import com.fashion.seckill.SeckillMessagePrepareTransaction;
import com.fashion.seckill.SeckillOrphanClaimTransaction;
import com.fashion.seckill.SeckillReconciliationService;
import com.fashion.seckill.SeckillReliablePublisher;
import com.fashion.seckill.SeckillReservationService;
import com.fashion.seckill.SeckillReservationSnapshot;
import com.fashion.seckill.SeckillReservationScanner;
import com.fashion.seckill.SeckillRedisScanPageReader;
import com.fashion.seckill.SeckillSubmitOrchestrator;
import com.fashion.service.impl.SeckillCancellationTransaction;
import com.fashion.service.impl.SeckillOrderServiceImpl;
import com.fashion.utils.UniqueID;
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
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.util.ReflectionTestUtils;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B6 MySQL/Redis/RabbitMQ 跨存储故障注入")
class B6CrossStoreReliabilityIntegrationTest {
    private static final String SCHEMA_PATTERN = "fsm_b6_cross_it_[0-9a-f]{32}";
    private static final Long USER_ID = 7000031L;
    private static final Long COUPON_ID = 800000000031L;
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private LettuceConnectionFactory redisConnection;
    private StringRedisTemplate redis;
    private CachingConnectionFactory unreachableRabbit;
    private AnnotationConfigApplicationContext context;

    @BeforeAll
    void startIsolatedStores() throws Exception {
        Map<String, Object> redisSettings = settings("redis");
        B6IntegrationSafety.requireLoopback(value(redisSettings, "host"), "Redis");
        B6IntegrationSafety.requireDedicatedRedisDatabase(value(redisSettings, "database"));
        B6IntegrationSafety.requireExclusiveRedisDatabase(value(redisSettings, "exclusive"));
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration();
        redisConfiguration.setHostName(value(redisSettings, "host"));
        redisConfiguration.setPort(Integer.parseInt(value(redisSettings, "port")));
        redisConfiguration.setDatabase(Integer.parseInt(value(redisSettings, "database")));
        String redisPassword = value(redisSettings, "password");
        if (!redisPassword.isEmpty()) redisConfiguration.setPassword(RedisPassword.of(redisPassword));
        redisConnection = new LettuceConnectionFactory(redisConfiguration);
        redisConnection.afterPropertiesSet();
        redis = new StringRedisTemplate(redisConnection);
        redis.afterPropertiesSet();
        Properties server = redisConnection.getConnection().serverCommands().info("server");
        String version = server == null ? null : server.getProperty("redis_version");
        B6IntegrationSafety.requireRedisVersion(version);
        B6IntegrationSafety.requireEmptyRedisDatabase(
                redisConnection.getConnection().serverCommands().dbSize());

        Map<String, Object> datasource = settings("datasource");
        B6IntegrationSafety.requireLoopback(value(datasource, "host"), "MySQL");
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b6_cross_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,"
                + "coupon_id BIGINT NOT NULL,order_number VARCHAR(50) NOT NULL,status INT NOT NULL DEFAULT 1,"
                + "create_time DATETIME,pay_time DATETIME,UNIQUE KEY idx_seckill_order_number(order_number)) ENGINE=InnoDB");
        B6ReliabilityMigrationMysqlIntegrationTestRunner.run(schemaUrl, username, password);
        execute("CREATE TABLE seckill_coupon(id BIGINT PRIMARY KEY,stock INT NOT NULL,status INT DEFAULT 1,"
                + "start_time DATETIME NULL,end_time DATETIME NULL) ENGINE=InnoDB");

        unreachableRabbit = new CachingConnectionFactory("127.0.0.1", 1);
        unreachableRabbit.getRabbitConnectionFactory().setConnectionTimeout(500);
        unreachableRabbit.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        unreachableRabbit.setPublisherReturns(true);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(unreachableRabbit);
        rabbitTemplate.setMandatory(true);

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b6.test.jdbc-url", schemaUrl);
        properties.put("b6.test.username", username);
        properties.put("b6.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b6Cross", properties));
        context.getBeanFactory().registerSingleton("b6RedisTemplate", redis);
        context.getBeanFactory().registerSingleton("b6UnreachableRabbitTemplate", rabbitTemplate);
        context.register(CrossStoreConfig.class);
        context.refresh();
    }

    @BeforeEach
    void resetFacts() throws Exception {
        execute("DELETE FROM seckill_reconciliation_anomaly");
        execute("DELETE FROM seckill_compensation_record");
        execute("DELETE FROM seckill_message_log");
        execute("DELETE FROM seckill_order");
        execute("DELETE FROM seckill_coupon");
        cleanupRedis();
    }

    @AfterAll
    void stopAndDrop() throws Exception {
        try {
            if (context != null) context.close();
            if (redis != null) cleanupRedis();
        } finally {
            if (unreachableRabbit != null) unreachableRabbit.destroy();
            if (redisConnection != null) redisConnection.destroy();
            if (schema != null) {
                validateSchema(schema);
                try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                     Statement statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE `" + schema + "`");
                }
            }
        }
    }

    @Test
    @DisplayName("RabbitMQ 同步不可达后 MySQL 留痕且 Redis stock/ZSET/HASH 原子恢复")
    void unreachableRabbitImmediatelyCompensatesReservation() throws Exception {
        prepareCoupon();
        UniqueID uniqueID = mock(UniqueID.class);
        when(uniqueID.nextId("seckill:order")).thenReturn(9003001L);
        SeckillSubmitOrchestrator orchestrator = new SeckillSubmitOrchestrator(uniqueID,
                context.getBean(SeckillReservationService.class),
                context.getBean(SeckillMessagePrepareTransaction.class),
                context.getBean(SeckillReliablePublisher.class),
                context.getBean(SeckillCompensationService.class),
                context.getBean(SeckillCompensationExecutor.class));

        SeckillSubmitOrchestrator.Submission result = orchestrator.submit(
                USER_ID, COUPON_ID, Instant.now().getEpochSecond());

        assertEquals(SeckillSubmitOrchestrator.Outcome.DELIVERY_FAILED, result.getOutcome());
        assertEquals("1", redis.opsForValue().get(stock()));
        assertFalse(Boolean.TRUE.equals(redis.opsForHash().hasKey(reservations(), String.valueOf(USER_ID))));
        assertEquals(null, redis.opsForZSet().score(users(), String.valueOf(USER_ID)));
        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(registry(), String.valueOf(COUPON_ID))));
        assertEquals("COMPENSATED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9003001'"));
        assertEquals("SUCCEEDED", scalarString("SELECT status FROM seckill_compensation_record "
                + "WHERE order_number='9003001'"));
    }

    @Test
    @DisplayName("悬空预扣并发对账与新 executor 重跑只恢复一次")
    void concurrentOrphanReconciliationAndRestartRemainIdempotent() throws Exception {
        prepareCoupon();
        SeckillReservationService reservations = context.getBean(SeckillReservationService.class);
        long reservationTime = Instant.now().getEpochSecond();
        assertEquals(SeckillReservationService.ReserveResult.RESERVED,
                reservations.reserve(COUPON_ID, USER_ID, "9003002", reservationTime));
        redis.opsForZSet().add(users(), String.valueOf(USER_ID), reservationTime - 600);
        SeckillReservationScanner scanner = context.getBean(SeckillReservationScanner.class);
        List<SeckillReservationSnapshot> discovered = scanner.scan(1);
        assertEquals(1, discovered.size());
        SeckillReservationSnapshot snapshot = discovered.get(0);
        assertEquals("9003002", snapshot.getOrderNumber());
        SeckillReconciliationService reconciliation = context.getBean(SeckillReconciliationService.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> reconcile(reconciliation, snapshot, ready, start));
            Future<?> second = pool.submit(() -> reconcile(reconciliation, snapshot, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals("1", redis.opsForValue().get(stock()));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                + "WHERE order_number='9003002' AND status='SUCCEEDED' AND (evidence_mask & 8)=8"));
        SeckillCompensationExecutor restarted = new SeckillCompensationExecutor(
                context.getBean(SeckillCompensationRecordMapper.class),
                context.getBean(SeckillOrderMapper.class), reservations,
                context.getBean(SeckillCompensationService.class));
        assertEquals(SeckillReservationService.RollbackResult.ALREADY_APPLIED,
                restarted.execute("9003002"));
        assertEquals("1", redis.opsForValue().get(stock()));
    }

    @Test
    @DisplayName("取消事务原子提交后 Redis 清理前崩溃，恢复、物理删除和再次重启均幂等")
    void cancelledOrderCrashWindowSurvivesDeletionAndRestart() throws Exception {
        prepareCoupon();
        SeckillReservationService reservations = context.getBean(SeckillReservationService.class);
        long reservationTime = Instant.now().getEpochSecond();
        assertEquals(SeckillReservationService.ReserveResult.RESERVED,
                reservations.reserve(COUPON_ID, USER_ID, "9003003", reservationTime));
        redis.opsForZSet().add(users(), String.valueOf(USER_ID), reservationTime - 600);
        execute("INSERT INTO seckill_coupon(id,stock,status) VALUES(" + COUPON_ID + ",0,1)");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) VALUES("
                + USER_ID + "," + COUPON_ID + ",'9003003',1,NOW())");

        assertTrue(context.getBean(SeckillCancellationTransaction.class)
                .cancelTrusted("9003003") != null);
        assertEquals(3, scalarInt("SELECT status FROM seckill_order WHERE order_number='9003003'"));
        assertEquals(1, scalarInt("SELECT stock FROM seckill_coupon WHERE id=" + COUPON_ID));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                + "WHERE order_number='9003003' AND status='PENDING' AND (evidence_mask & 4)=4"));
        assertTrue(Boolean.TRUE.equals(redis.opsForHash().hasKey(reservations(), String.valueOf(USER_ID))));

        SeckillCompensationExecutor restarted = new SeckillCompensationExecutor(
                context.getBean(SeckillCompensationRecordMapper.class),
                context.getBean(SeckillOrderMapper.class), reservations,
                context.getBean(SeckillCompensationService.class));
        assertEquals(SeckillReservationService.RollbackResult.APPLIED, restarted.execute("9003003"));

        assertEquals("1", redis.opsForValue().get(stock()));
        assertFalse(Boolean.TRUE.equals(redis.opsForHash().hasKey(reservations(), String.valueOf(USER_ID))));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                + "WHERE order_number='9003003' AND status='SUCCEEDED' AND (evidence_mask & 4)=4"));

        SeckillOrderServiceImpl deletion = new SeckillOrderServiceImpl();
        ReflectionTestUtils.setField(deletion, "seckillOrderMapper",
                context.getBean(SeckillOrderMapper.class));
        ReflectionTestUtils.setField(deletion, "seckillCompensationRecordMapper",
                context.getBean(SeckillCompensationRecordMapper.class));
        Long orderId = Long.valueOf(scalarString("SELECT id FROM seckill_order WHERE order_number='9003003'"));
        assertTrue(deletion.deleteOrder(orderId));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM seckill_order WHERE order_number='9003003'"));
        assertEquals(SeckillReservationService.RollbackResult.ALREADY_APPLIED,
                restarted.execute("9003003"));
        assertEquals("1", redis.opsForValue().get(stock()));
    }

    private void reconcile(SeckillReconciliationService service, SeckillReservationSnapshot snapshot,
                           CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            service.reconcile(snapshot);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private void prepareCoupon() {
        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(stock(), "1");
        redis.opsForValue().set(start(), String.valueOf(now - 10));
        redis.opsForValue().set(end(), String.valueOf(now + 600));
    }

    private void cleanupRedis() {
        redis.opsForSet().remove(registry(), String.valueOf(COUPON_ID));
        redis.delete(Arrays.asList(stock(), start(), end(), users(), reservations()));
    }

    private String stock() { return "seckill:coupon:stock:" + COUPON_ID; }
    private String start() { return "seckill:coupon:startTime:" + COUPON_ID; }
    private String end() { return "seckill:coupon:endTime:" + COUPON_ID; }
    private String users() { return "seckill:coupon:users:" + COUPON_ID; }
    private String reservations() { return "seckill:coupon:reservations:" + COUPON_ID; }
    private String registry() { return "seckill:coupon:reservation:index"; }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static Map<String, Object> settings(String section) throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), section);
        }
    }

    private static Path configPath() {
        String configured = System.getProperty("b6.config");
        if (configured == null || configured.trim().isEmpty()) throw new IllegalStateException("b6.config is required");
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

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) throw new IllegalStateException("unsafe schema");
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {SeckillMessageLogMapper.class, SeckillCompensationRecordMapper.class,
            SeckillOrderMapper.class, SeckillCouponMapper.class, SeckillReconciliationAnomalyMapper.class,
            SeckillReconciliationCandidateMapper.class})
    static class CrossStoreConfig {
        @Bean DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(environment.getRequiredProperty("b6.test.jdbc-url"));
            source.setUsername(environment.getRequiredProperty("b6.test.username"));
            source.setPassword(environment.getRequiredProperty("b6.test.password"));
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean SeckillReservationService reservationService(StringRedisTemplate redis) {
            return new SeckillReservationService(redis);
        }
        @Bean SeckillCancellationTransaction cancellationTransaction() {
            return new SeckillCancellationTransaction();
        }
        @Bean SeckillRedisScanPageReader redisScanPageReader(StringRedisTemplate redis) {
            return new SeckillRedisScanPageReader(redis);
        }
        @Bean SeckillReservationScanner reservationScanner(SeckillRedisScanPageReader reader,
                SeckillReconciliationCandidateMapper candidates,
                SeckillReconciliationAnomalyMapper anomalies) {
            return new SeckillReservationScanner(reader, candidates, anomalies);
        }
        @Bean SeckillMessagePrepareTransaction prepareTransaction(SeckillMessageLogMapper messages,
                SeckillCompensationRecordMapper compensations) {
            return new SeckillMessagePrepareTransaction(messages, compensations);
        }
        @Bean SeckillCompensationService compensationService(SeckillCompensationRecordMapper mapper,
                SeckillMessageLogMapper messages) {
            return new SeckillCompensationService(mapper, messages);
        }
        @Bean SeckillCompensationExecutor compensationExecutor(SeckillCompensationRecordMapper records,
                SeckillOrderMapper orders, SeckillReservationService reservations,
                SeckillCompensationService compensation) {
            return new SeckillCompensationExecutor(records, orders, reservations, compensation);
        }
        @Bean SeckillReliablePublisher reliablePublisher(SeckillMessageLogMapper mapper,
                RabbitTemplate rabbitTemplate) {
            return new SeckillReliablePublisher(mapper, rabbitTemplate);
        }
        @Bean SeckillOrphanClaimTransaction orphanClaimTransaction(SeckillMessageLogMapper messages,
                SeckillOrderMapper orders, SeckillCompensationService compensation) {
            return new SeckillOrphanClaimTransaction(messages, orders, compensation);
        }
        @Bean SeckillReconciliationService reconciliationService(SeckillOrderMapper orders,
                SeckillMessageLogMapper messages, SeckillCompensationExecutor executor,
                SeckillOrphanClaimTransaction orphan, SeckillReconciliationAnomalyMapper anomalies) {
            return new SeckillReconciliationService(orders, messages, executor, orphan, anomalies);
        }
    }
}
