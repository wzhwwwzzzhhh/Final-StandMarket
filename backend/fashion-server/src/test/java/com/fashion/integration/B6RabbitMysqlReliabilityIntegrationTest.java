package com.fashion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.config.DirectExchangeConfig;
import com.fashion.config.SeckillMqConfirmConfig;
import com.fashion.entity.SeckillMessage;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import com.fashion.seckill.SeckillAfterCommitDispatcher;
import com.fashion.seckill.SeckillBusinessDeadLetterService;
import com.fashion.seckill.SeckillCompensationService;
import com.fashion.seckill.SeckillConsumeFailureTransaction;
import com.fashion.seckill.SeckillDuplicateOrderTransaction;
import com.fashion.seckill.SeckillInvalidMessageService;
import com.fashion.seckill.SeckillListenerPauser;
import com.fashion.seckill.SeckillMessagePrepareTransaction;
import com.fashion.seckill.SeckillMessageRecoveryTask;
import com.fashion.seckill.SeckillOrderConsumeTransaction;
import com.fashion.seckill.SeckillOrderConsumer;
import com.fashion.seckill.SeckillPublishCallbackPolicy;
import com.fashion.seckill.SeckillPublisherCallbackHandler;
import com.fashion.seckill.SeckillReliablePublisher;
import com.fashion.seckill.SeckillReservationService;
import com.fashion.seckill.SeckillCompensationExecutor;
import com.fashion.seckill.SeckillSubmitOrchestrator;
import com.fashion.service.SeckillOrderService;
import com.fashion.utils.UniqueID;
import com.rabbitmq.client.Channel;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B6 MySQL + RabbitMQ 产品链路联合门禁")
class B6RabbitMysqlReliabilityIntegrationTest {
    private static final String SCHEMA_PATTERN = "fsm_b6_rabbit_mysql_it_[0-9a-f]{32}";
    private static final String VHOST_PATTERN = "fsm_b6_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private RabbitAdmin rabbitAdmin;
    private RabbitTemplate rabbit;
    private CachingConnectionFactory rabbitConnection;
    private SimpleMessageListenerContainer listener;
    private String nackExchange;
    private String nackQueue;

    @BeforeAll
    void createIsolatedDependencies() throws Exception {
        Map<String, Object> datasource = section("datasource");
        Map<String, Object> rabbitSettings = section("rabbitmq");
        B6IntegrationSafety.requireLoopback(value(datasource, "host"), "MySQL");
        B6IntegrationSafety.requireLoopback(value(rabbitSettings, "host"), "RabbitMQ");
        String vhost = value(rabbitSettings, "virtual-host");
        if (!vhost.matches(VHOST_PATTERN)) {
            throw new IllegalStateException("joint fixture requires isolated fsm_b6_it_<32hex> vhost");
        }

        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b6_rabbit_mysql_it_" + UUID.randomUUID().toString().replace("-", "");
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

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b6.test.jdbc-url", schemaUrl);
        properties.put("b6.test.username", username);
        properties.put("b6.test.password", password);
        properties.put("b6.test.rabbit-host", value(rabbitSettings, "host"));
        properties.put("b6.test.rabbit-port", value(rabbitSettings, "port"));
        properties.put("b6.test.rabbit-username", value(rabbitSettings, "username"));
        properties.put("b6.test.rabbit-password", value(rabbitSettings, "password"));
        properties.put("b6.test.rabbit-vhost", vhost);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b6Joint", properties));
        context.register(JointConfig.class, DirectExchangeConfig.class, SeckillMqConfirmConfig.class);
        context.refresh();
        rabbitAdmin = context.getBean(RabbitAdmin.class);
        rabbit = context.getBean(RabbitTemplate.class);
        rabbitConnection = context.getBean(CachingConnectionFactory.class);
        Object brokerVersion = rabbitConnection.createConnection().getDelegate().getServerProperties().get("version");
        assertTrue(String.valueOf(brokerVersion).startsWith("3.12."));
        rabbitAdmin.initialize();

        String runId = UUID.randomUUID().toString().replace("-", "");
        nackExchange = "fsm_b6_it_nack_exchange_" + runId;
        nackQueue = "fsm_b6_it_nack_queue_" + runId;
        DirectExchange exchange = new DirectExchange(nackExchange, true, false);
        Queue queue = QueueBuilder.durable(nackQueue).withArgument("x-max-length", 1)
                .withArgument("x-overflow", "reject-publish").build();
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("reject"));
    }

    @AfterAll
    void cleanup() throws Exception {
        stopListener();
        try {
            if (rabbitAdmin != null) {
                if (nackQueue != null) rabbitAdmin.deleteQueue(nackQueue);
                if (nackExchange != null) rabbitAdmin.deleteExchange(nackExchange);
            }
            if (context != null) context.close();
        } finally {
            if (schema != null) {
                validateSchema(schema);
                try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                     Statement statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE `" + schema + "`");
                }
            }
        }
    }

    @BeforeEach
    void reset() throws Exception {
        stopListener();
        rabbitAdmin.purgeQueue(DirectExchangeConfig.SeckillQueue, false);
        rabbitAdmin.purgeQueue(DirectExchangeConfig.delayQueue, false);
        rabbitAdmin.purgeQueue(DirectExchangeConfig.deadQueue, false);
        rabbitAdmin.purgeQueue(DirectExchangeConfig.SECKILL_FAILURE_QUEUE, false);
        rabbitAdmin.purgeQueue(nackQueue, false);
        execute("DELETE FROM seckill_reconciliation_anomaly");
        execute("DELETE FROM seckill_compensation_record");
        execute("DELETE FROM seckill_message_log");
        execute("DELETE FROM seckill_order");
        execute("DELETE FROM seckill_coupon");
        execute("INSERT INTO seckill_coupon(id,stock,status,start_time,end_time) "
                + "VALUES(19,1,1,DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 1 HOUR))");
    }

    @AfterEach
    void stopConsumerAfterTest() {
        stopListener();
    }

    @Test
    @DisplayName("产品 publisher + confirm config 将真实 ack/return/nack 幂等收敛到 MySQL")
    void productPublisherPersistsRealBrokerCallbacks() throws Exception {
        SeckillReliablePublisher publisher = context.getBean(SeckillReliablePublisher.class);
        insertCreateLog("9501", DirectExchangeConfig.SeckillExchange, DirectExchangeConfig.SeckillRoutingKey);
        publisher.publish("SECKILL_ORDER_CREATE:9501", "INITIAL");
        await(() -> "BROKER_ACKED".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9501'")));

        SeckillPublisherCallbackHandler callback = context.getBean(SeckillPublisherCallbackHandler.class);
        long version = scalarLong("SELECT version FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9501'");
        callback.handleConfirm("SECKILL_ORDER_CREATE:9501:P1", true, null);
        callback.handleConfirm("SECKILL_ORDER_CREATE:9501:P2", false, "stale nack");
        assertEquals("BROKER_ACKED", stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9501'"));
        assertEquals(version, scalarLong("SELECT version FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9501'"));

        insertCreateLog("9502", DirectExchangeConfig.SeckillExchange, "missing.route");
        publisher.publish("SECKILL_ORDER_CREATE:9502", "INITIAL");
        await(() -> "COMPENSATION_PENDING".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9502'")));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM seckill_compensation_record WHERE order_number='9502'"));

        rabbit.send(nackExchange, "reject", MessageBuilder.withBody("fill".getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build(), new CorrelationData("B6:FILL:P1"));
        await(() -> rabbitAdmin.getQueueInfo(nackQueue).getMessageCount() == 1);
        insertCreateLog("9503", nackExchange, "reject");
        publisher.publish("SECKILL_ORDER_CREATE:9503", "INITIAL");
        await(() -> "COMPENSATION_PENDING".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9503'")));
        assertEquals("NACK", stringOrNull("SELECT confirm_status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9503'"));
    }

    @Test
    @DisplayName("真实 listener 每次 ack 后由持久恢复重投，第 3 次进入唯一业务 DLQ 且不 requeue")
    void poisonMessageUsesPersistentRetriesAndBusinessDeadLetter() throws Exception {
        SeckillOrderConsumeTransaction failingConsume = mock(SeckillOrderConsumeTransaction.class);
        doThrow(new IllegalStateException("poison")).when(failingConsume)
                .consume(any(SeckillMessage.class), any(String.class), anyInt());
        SeckillOrderConsumer consumer = new SeckillOrderConsumer(new ObjectMapper(), failingConsume,
                context.getBean(SeckillDuplicateOrderTransaction.class),
                context.getBean(SeckillConsumeFailureTransaction.class),
                context.getBean(SeckillInvalidMessageService.class), mock(SeckillListenerPauser.class));
        listener = new SimpleMessageListenerContainer(rabbitConnection);
        listener.setQueueNames(DirectExchangeConfig.SeckillQueue);
        listener.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        listener.setDefaultRequeueRejected(false);
        listener.setConcurrentConsumers(1);
        listener.setMessageListener((ChannelAwareMessageListener) consumer::consume);
        listener.start();

        insertCreateLog("9601", DirectExchangeConfig.SeckillExchange, DirectExchangeConfig.SeckillRoutingKey);
        context.getBean(SeckillReliablePublisher.class).publish("SECKILL_ORDER_CREATE:9601", "INITIAL");
        SeckillMessageRecoveryTask recovery = new SeckillMessageRecoveryTask(
                context.getBean(SeckillMessageLogMapper.class), context.getBean(SeckillReliablePublisher.class),
                mock(SeckillOrderService.class), context.getBean(SeckillCompensationService.class),
                context.getBean(SeckillBusinessDeadLetterService.class));
        for (int attempt = 1; attempt <= 2; attempt++) {
            final int expected = attempt;
            await(() -> scalarLongQuiet("SELECT consume_attempt FROM seckill_message_log "
                    + "WHERE message_id='SECKILL_ORDER_CREATE:9601'") == expected);
            execute("UPDATE seckill_message_log SET next_retry_at=NOW(3) "
                    + "WHERE message_id='SECKILL_ORDER_CREATE:9601'");
            recovery.runOnce();
        }
        await(() -> scalarLongQuiet("SELECT consume_attempt FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9601'") == 3);
        await(() -> scalarLongQuiet("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_id='SECKILL_DEAD:SECKILL_ORDER_CREATE:9601'") == 1);
        recovery.runOnce();
        await(() -> "BROKER_ACKED".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_DEAD:SECKILL_ORDER_CREATE:9601'")));

        assertEquals("CONSUME_EXHAUSTED", stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9601'"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM seckill_compensation_record WHERE order_number='9601'"));
        assertEquals(0, rabbitAdmin.getQueueInfo(DirectExchangeConfig.SeckillQueue).getMessageCount());
        assertEquals(1, rabbitAdmin.getQueueInfo(DirectExchangeConfig.SECKILL_FAILURE_QUEUE).getMessageCount());
    }

    @Test
    @DisplayName("真实 MySQL 回滚不产生 delay；提交完成后产品 publisher 才写入 delay queue")
    void delayPublishOccursOnlyAfterDatabaseCommit() throws Exception {
        insertCreateLogWithStatus("9701", "BROKER_ACKED");
        execute("UPDATE seckill_coupon SET stock=0 WHERE id=19");
        SeckillMessage message = message("9701");
        SeckillOrderConsumeTransaction consume = context.getBean(SeckillOrderConsumeTransaction.class);

        assertThrows(IllegalStateException.class,
                () -> consume.consume(message, "SECKILL_ORDER_CREATE:9701", 1));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM seckill_order WHERE order_number='9701'"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9701'"));
        assertEquals(0, rabbitAdmin.getQueueInfo(DirectExchangeConfig.delayQueue).getMessageCount());

        execute("UPDATE seckill_coupon SET stock=1 WHERE id=19");
        consume.consume(message, "SECKILL_ORDER_CREATE:9701", 1);
        await(() -> "BROKER_ACKED".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9701'")));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM seckill_order WHERE order_number='9701'"));
        assertEquals(1, rabbitAdmin.getQueueInfo(DirectExchangeConfig.delayQueue).getMessageCount());
    }

    @Test
    @DisplayName("初始 publish 被 latch 阻塞时独立 MySQL 连接已可见 PREPARED")
    void preparedIsCommittedBeforeInitialPublish() throws Exception {
        CountDownLatch publishEntered = new CountDownLatch(1);
        CountDownLatch releasePublish = new CountDownLatch(1);
        SeckillReliablePublisher blockingPublisher = spy(context.getBean(SeckillReliablePublisher.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            publishEntered.countDown();
            if (!releasePublish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("publish latch timed out");
            }
            return invocation.callRealMethod();
        }).when(blockingPublisher).publish("SECKILL_ORDER_CREATE:9801", "INITIAL");

        UniqueID uniqueID = mock(UniqueID.class);
        when(uniqueID.nextId("seckill:order")).thenReturn(9801L);
        SeckillReservationService reservations = mock(SeckillReservationService.class);
        when(reservations.reserve(19L, 7L, "9801", 1L))
                .thenReturn(SeckillReservationService.ReserveResult.RESERVED);
        SeckillSubmitOrchestrator orchestrator = new SeckillSubmitOrchestrator(uniqueID, reservations,
                context.getBean(SeckillMessagePrepareTransaction.class), blockingPublisher,
                context.getBean(SeckillCompensationService.class), mock(SeckillCompensationExecutor.class));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<SeckillSubmitOrchestrator.Submission> submission =
                pool.submit(() -> orchestrator.submit(7L, 19L, 1L));
        try {
            assertTrue(publishEntered.await(5, TimeUnit.SECONDS));
            assertEquals("PREPARED", stringOrNull("SELECT status FROM seckill_message_log "
                    + "WHERE message_id='SECKILL_ORDER_CREATE:9801'"));
            assertEquals(0, rabbitAdmin.getQueueInfo(DirectExchangeConfig.SeckillQueue).getMessageCount());
        } finally {
            releasePublish.countDown();
        }
        assertEquals(SeckillSubmitOrchestrator.Outcome.PROCESSING,
                submission.get(5, TimeUnit.SECONDS).getOutcome());
        await(() -> "BROKER_ACKED".equals(stringOrNull("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9801'")));
        pool.shutdownNow();
    }

    @Test
    @DisplayName("应用真实 topology 的三种 failure routing key 全部进入同一持久业务队列")
    void allFailureBindingsAreDeclared() throws Exception {
        String[] routes = {DirectExchangeConfig.SECKILL_ORDER_FAILURE_ROUTING_KEY,
                DirectExchangeConfig.SECKILL_TIMEOUT_FAILURE_ROUTING_KEY,
                DirectExchangeConfig.SECKILL_INVALID_FAILURE_ROUTING_KEY};
        for (String route : routes) {
            rabbit.convertAndSend(DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE, route, route);
        }
        await(() -> rabbitAdmin.getQueueInfo(DirectExchangeConfig.SECKILL_FAILURE_QUEUE).getMessageCount() == 3);
    }

    private void insertCreateLog(String orderNumber, String exchange, String routingKey) throws Exception {
        insertCreateLogWithStatus(orderNumber, "PREPARED", exchange, routingKey);
    }

    private void insertCreateLogWithStatus(String orderNumber, String status) throws Exception {
        insertCreateLogWithStatus(orderNumber, status,
                DirectExchangeConfig.SeckillExchange, DirectExchangeConfig.SeckillRoutingKey);
    }

    private void insertCreateLogWithStatus(String orderNumber, String status,
                                           String exchange, String routingKey) throws Exception {
        String payload = new ObjectMapper().writeValueAsString(message(orderNumber)).replace("'", "''");
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES ('SECKILL_ORDER_CREATE:"
                + orderNumber + "','ORDER_CREATE','INITIAL','" + orderNumber + "',7,19,'" + payload + "','"
                + exchange + "','" + routingKey + "','" + status + "')");
    }

    private SeckillMessage message(String orderNumber) {
        SeckillMessage message = new SeckillMessage();
        message.setOrderNumber(orderNumber);
        message.setUserId(7L);
        message.setCouponId(19L);
        return message;
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        do {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertTrue(condition.getAsBoolean(), "asynchronous broker/database state did not converge");
    }

    private void stopListener() {
        if (listener != null) {
            listener.stop();
            listener.destroy();
            listener = null;
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalarLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private long scalarLongQuiet(String sql) {
        try {
            return scalarLong(sql);
        } catch (Exception failure) {
            return Long.MIN_VALUE;
        }
    }

    private String stringOrNull(String sql) {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        } catch (Exception failure) {
            return null;
        }
    }

    private Map<String, Object> section(String key) throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), key);
        }
    }

    private Path configPath() {
        String configured = System.getProperty("b6.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b6.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("B6 config is missing");
        return path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) throw new IllegalStateException("missing config section " + key);
        return (Map<String, Object>) child;
    }

    private String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) throw new IllegalStateException("missing config value " + key);
        return String.valueOf(result);
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe joint integration schema");
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {SeckillOrderMapper.class, SeckillCouponMapper.class,
            SeckillMessageLogMapper.class, SeckillCompensationRecordMapper.class,
            SeckillReconciliationCandidateMapper.class, SeckillReconciliationAnomalyMapper.class})
    static class JointConfig {
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

        @Bean CachingConnectionFactory rabbitConnectionFactory(org.springframework.core.env.Environment env) {
            CachingConnectionFactory factory = new CachingConnectionFactory(
                    env.getRequiredProperty("b6.test.rabbit-host"),
                    Integer.parseInt(env.getRequiredProperty("b6.test.rabbit-port")));
            factory.setUsername(env.getRequiredProperty("b6.test.rabbit-username"));
            factory.setPassword(env.getRequiredProperty("b6.test.rabbit-password"));
            factory.setVirtualHost(env.getRequiredProperty("b6.test.rabbit-vhost"));
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            factory.getRabbitConnectionFactory().setConnectionTimeout(2000);
            return factory;
        }

        @Bean RabbitTemplate rabbitTemplate(CachingConnectionFactory factory) {
            return new RabbitTemplate(factory);
        }

        @Bean RabbitAdmin rabbitAdmin(CachingConnectionFactory factory) {
            return new RabbitAdmin(factory);
        }

        @Bean SeckillAfterCommitDispatcher afterCommitDispatcher() { return new SeckillAfterCommitDispatcher(); }
        @Bean SeckillPublishCallbackPolicy callbackPolicy() { return new SeckillPublishCallbackPolicy(); }
        @Bean SeckillCompensationService compensationService(SeckillCompensationRecordMapper mapper) {
            return new SeckillCompensationService(mapper);
        }
        @Bean SeckillPublisherCallbackHandler callbackHandler(SeckillMessageLogMapper messages,
                SeckillPublishCallbackPolicy policy, SeckillCompensationService compensation) {
            return new SeckillPublisherCallbackHandler(messages, policy, compensation);
        }
        @Bean SeckillReliablePublisher reliablePublisher(SeckillMessageLogMapper messages,
                RabbitTemplate rabbitTemplate) {
            return new SeckillReliablePublisher(messages, rabbitTemplate);
        }
        @Bean SeckillMessagePrepareTransaction messagePrepareTransaction(SeckillMessageLogMapper messages,
                SeckillCompensationRecordMapper compensations) {
            return new SeckillMessagePrepareTransaction(messages, compensations);
        }
        @Bean SeckillBusinessDeadLetterService deadLetterService(SeckillMessageLogMapper messages,
                SeckillCompensationService compensation, SeckillAfterCommitDispatcher afterCommit) {
            return new SeckillBusinessDeadLetterService(messages, compensation, afterCommit);
        }
        @Bean SeckillConsumeFailureTransaction failureTransaction(SeckillMessageLogMapper messages,
                SeckillBusinessDeadLetterService deadLetter) {
            return new SeckillConsumeFailureTransaction(messages, deadLetter);
        }
        @Bean SeckillInvalidMessageService invalidMessageService(SeckillMessageLogMapper messages,
                SeckillAfterCommitDispatcher afterCommit) {
            return new SeckillInvalidMessageService(messages, afterCommit);
        }
        @Bean SeckillDuplicateOrderTransaction duplicateTransaction(SeckillOrderMapper orders,
                SeckillMessageLogMapper messages) {
            return new SeckillDuplicateOrderTransaction(orders, messages);
        }
        @Bean SeckillOrderConsumeTransaction consumeTransaction(SeckillOrderMapper orders,
                SeckillCouponMapper coupons, SeckillMessageLogMapper messages,
                SeckillAfterCommitDispatcher afterCommit, SeckillReliablePublisher publisher) {
            return new SeckillOrderConsumeTransaction(orders, coupons, messages, afterCommit, publisher);
        }
    }
}
