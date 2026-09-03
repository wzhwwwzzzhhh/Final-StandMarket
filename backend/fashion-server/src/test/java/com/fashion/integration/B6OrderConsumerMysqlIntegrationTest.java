package com.fashion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.SeckillMessage;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.seckill.SeckillAfterCommitDispatcher;
import com.fashion.seckill.SeckillConsumeFailureTransaction;
import com.fashion.seckill.SeckillDuplicateOrderTransaction;
import com.fashion.seckill.SeckillInvalidMessageService;
import com.fashion.seckill.SeckillListenerPauser;
import com.fashion.seckill.SeckillOrderConsumeTransaction;
import com.fashion.seckill.SeckillOrderConsumer;
import com.fashion.seckill.SeckillMessagePrepareTransaction;
import com.fashion.seckill.SeckillOrphanClaimTransaction;
import com.fashion.seckill.SeckillReservationSnapshot;
import com.fashion.seckill.SeckillReliablePublisher;
import com.fashion.seckill.SeckillCompensationService;
import com.fashion.seckill.SeckillBusinessDeadLetterService;
import com.rabbitmq.client.Channel;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B6 MySQL 并发重复消息唯一订单门禁")
class B6OrderConsumerMysqlIntegrationTest {
    private static final java.util.concurrent.atomic.AtomicBoolean FAIL_COMPENSATION =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String SCHEMA_PATTERN = "fsm_b6_consumer_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private SeckillReliablePublisher publisher;

    @BeforeAll
    void createSchemaAndContext() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        B6IntegrationSafety.requireLoopback(value(datasource, "host"), "MySQL");
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b6_consumer_it_" + UUID.randomUUID().toString().replace("-", "");
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
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b6Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();
        publisher = context.getBean(SeckillReliablePublisher.class);
    }

    @AfterAll
    void closeAndDrop() throws Exception {
        if (context != null) context.close();
        if (schema != null) {
            validateSchema(schema);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void resetData() throws Exception {
        FAIL_COMPENSATION.set(false);
        execute("DELETE FROM seckill_reconciliation_anomaly");
        execute("DELETE FROM seckill_compensation_record");
        execute("DELETE FROM seckill_message_log");
        execute("DELETE FROM seckill_order");
        execute("DELETE FROM seckill_coupon");
        execute("INSERT INTO seckill_coupon(id,stock,status,start_time,end_time) "
                + "VALUES(19,1,1,DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 1 HOUR))");
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_ORDER_CREATE:9001','ORDER_CREATE','INITIAL','9001',7,19,'{}',"
                + "'market.direct','seckillOrder','BROKER_ACKED')");
    }

    @Test
    @DisplayName("两个并发等价投递最终只有一个订单、一次 DB 扣减和一个 timeout log")
    void concurrentEquivalentDeliveriesCreateOneOrder() throws Exception {
        SeckillOrderConsumeTransaction consume = context.getBean(SeckillOrderConsumeTransaction.class);
        SeckillDuplicateOrderTransaction duplicate = context.getBean(SeckillDuplicateOrderTransaction.class);
        SeckillConsumeFailureTransaction failure = mock(SeckillConsumeFailureTransaction.class);
        SeckillOrderConsumer consumer = new SeckillOrderConsumer(new ObjectMapper(), consume, duplicate,
                failure, mock(SeckillInvalidMessageService.class), mock(SeckillListenerPauser.class));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> invoke(consumer, envelope(101L), ready, start));
            Future<?> second = pool.submit(() -> invoke(consumer, envelope(102L), ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_order WHERE order_number='9001'"));
        assertEquals(0, scalarInt("SELECT stock FROM seckill_coupon WHERE id=19"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertEquals("CONSUMED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9001'"));
        verify(publisher).publish("SECKILL_ORDER_TIMEOUT:9001", "TIMEOUT_RECOVERY");
        verify(failure, never()).record(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    @DisplayName("旧或重复 callback 在已有错误后仍追加有界审计")
    void callbackAuditAppendsAfterExistingError() throws Exception {
        SeckillMessageLogMapper messages = context.getBean(SeckillMessageLogMapper.class);
        execute("UPDATE seckill_message_log SET last_error='RETURNED' "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9001'");

        assertEquals(1, messages.appendCallbackAudit("SECKILL_ORDER_CREATE:9001",
                "SECKILL_ORDER_CREATE:9001:P1", "stale nack"));
        assertEquals(1, messages.appendCallbackAudit("SECKILL_ORDER_CREATE:9001",
                "SECKILL_ORDER_CREATE:9001:P2", "duplicate ack"));

        String audit = scalarString("SELECT last_error FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9001'");
        assertTrue(audit.contains("RETURNED"));
        assertTrue(audit.contains("P1 stale nack"));
        assertTrue(audit.contains("P2 duplicate ack"));
        assertTrue(audit.length() <= 500);
    }

    @Test
    @DisplayName("毒消息 attempt 1-3 有限推进后建立唯一业务死信与补偿事实")
    void poisonMessageExhaustsIntoBusinessDeadLetter() throws Exception {
        SeckillOrderConsumeTransaction consume = mock(SeckillOrderConsumeTransaction.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("poison business failure"))
                .when(consume).consume(any(SeckillMessage.class),
                        org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_CREATE:9001"),
                        org.mockito.ArgumentMatchers.anyInt());
        SeckillOrderConsumer consumer = new SeckillOrderConsumer(new ObjectMapper(), consume,
                mock(SeckillDuplicateOrderTransaction.class),
                context.getBean(SeckillConsumeFailureTransaction.class),
                context.getBean(SeckillInvalidMessageService.class), mock(SeckillListenerPauser.class));

        consumer.consume(envelope(201L, 1), mock(Channel.class));
        consumer.consume(envelope(202L, 2), mock(Channel.class));
        consumer.consume(envelope(203L, 3), mock(Channel.class));

        assertEquals("CONSUME_EXHAUSTED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9001'"));
        assertEquals(3, scalarInt("SELECT consume_attempt FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_CREATE:9001'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_id='SECKILL_DEAD:SECKILL_ORDER_CREATE:9001' "
                + "AND message_type='BUSINESS_DEAD_LETTER' AND payload LIKE '%\"attempt\":3%'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                + "WHERE order_number='9001' AND (evidence_mask & 2)=2"));
    }

    @Test
    @DisplayName("非法 envelope 以 body hash 幂等隔离且不创建订单")
    void invalidEnvelopeIsDurablyQuarantined() throws Exception {
        SeckillOrderConsumer consumer = new SeckillOrderConsumer(new ObjectMapper(),
                mock(SeckillOrderConsumeTransaction.class), mock(SeckillDuplicateOrderTransaction.class),
                context.getBean(SeckillConsumeFailureTransaction.class),
                context.getBean(SeckillInvalidMessageService.class), mock(SeckillListenerPauser.class));
        Message invalid = MessageBuilder.withBody("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setMessageId("untrusted-id").setDeliveryTag(204L).setContentType("application/json").build();

        consumer.consume(invalid, mock(Channel.class));

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_type='INVALID_MESSAGE' AND body_size=8"));
        assertEquals("CONSUME_EXHAUSTED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_type='INVALID_MESSAGE' AND body_size=8"));
        assertEquals("PENDING", scalarString("SELECT dead_letter_status FROM seckill_message_log "
                + "WHERE message_type='INVALID_MESSAGE' AND body_size=8"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log dead_letter "
                + "JOIN seckill_message_log source ON source.message_id=dead_letter.source_message_id "
                + "WHERE source.message_type='INVALID_MESSAGE' "
                + "AND dead_letter.message_type='BUSINESS_DEAD_LETTER' "
                + "AND dead_letter.status='PREPARED'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM seckill_order"));
    }

    @Test
    @DisplayName("scheduler 路径 DLQ insert 后补偿失败整体回滚且下一轮可重试收敛")
    void deadLetterAndCompensationAreAtomicOutsideConsumerTransaction() throws Exception {
        com.fashion.entity.SeckillMessageLog source = context.getBean(SeckillMessageLogMapper.class)
                .selectByMessageId("SECKILL_ORDER_CREATE:9001");
        SeckillBusinessDeadLetterService service = context.getBean(SeckillBusinessDeadLetterService.class);
        FAIL_COMPENSATION.set(true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.createForExhaustedOrder(source));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_type='BUSINESS_DEAD_LETTER'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record"));

        FAIL_COMPENSATION.set(false);
        service.createForExhaustedOrder(source);

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_type='BUSINESS_DEAD_LETTER'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                + "WHERE order_number='9001'"));
    }

    @Test
    @DisplayName("候选源先按 coupon 去重再限流，不因单券历史倾斜跳过较小 coupon")
    void reconciliationCandidateLimitIsAppliedAfterPerSourceDeduplication() throws Exception {
        execute("DELETE FROM seckill_coupon");
        execute("DELETE FROM seckill_message_log");
        execute("INSERT INTO seckill_coupon(id,stock,status,start_time,end_time) VALUES"
                + "(1000,1,1,DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 1 HOUR)),"
                + "(1001,1,0,DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR))");
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_ORDER_CREATE:9101','ORDER_CREATE','INITIAL','9101',7,1,'{}','market.direct','seckillOrder','BROKER_ACKED'),"
                + "('SECKILL_ORDER_CREATE:9102','ORDER_CREATE','INITIAL','9102',8,1,'{}','market.direct','seckillOrder','BROKER_ACKED'),"
                + "('SECKILL_ORDER_CREATE:9103','ORDER_CREATE','INITIAL','9103',9,1,'{}','market.direct','seckillOrder','BROKER_ACKED'),"
                + "('SECKILL_ORDER_CREATE:9201','ORDER_CREATE','INITIAL','9201',10,2,'{}','market.direct','seckillOrder','BROKER_ACKED')");

        List<Long> candidates = context.getBean(SeckillReconciliationCandidateMapper.class)
                .selectCouponIdsAfter(0L, 2);

        assertEquals(java.util.Arrays.asList(1L, 2L), candidates);
    }

    @Test
    @DisplayName("同券同类型异常按设计聚合，详情指纹仅保留最新样本")
    void reconciliationAnomalyAggregatesByTypeAndCoupon() throws Exception {
        SeckillReconciliationAnomalyMapper anomalies =
                context.getBean(SeckillReconciliationAnomalyMapper.class);

        anomalies.upsert("LEDGER_CARDINALITY_MISMATCH", 19L, 7L, "9001",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        anomalies.upsert("LEDGER_CARDINALITY_MISMATCH", 19L, 8L, "9002",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_reconciliation_anomaly "
                + "WHERE anomaly_type='LEDGER_CARDINALITY_MISMATCH' AND coupon_id=19"));
        assertEquals(2, scalarInt("SELECT occurrence_count FROM seckill_reconciliation_anomaly "
                + "WHERE anomaly_type='LEDGER_CARDINALITY_MISMATCH' AND coupon_id=19"));
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                scalarString("SELECT details_hash FROM seckill_reconciliation_anomaly "
                        + "WHERE anomaly_type='LEDGER_CARDINALITY_MISMATCH' AND coupon_id=19"));
    }

    @Test
    @DisplayName("补偿 lease 过期重领后旧 fencing token 不能覆盖新 owner")
    void compensationLeaseUsesPerClaimFencingToken() throws Exception {
        SeckillCompensationRecordMapper compensations =
                context.getBean(SeckillCompensationRecordMapper.class);
        compensations.upsertReleaseReservation("9001", 7L, 19L,
                "RECONCILIATION", SeckillCompensationService.EVIDENCE_ORPHAN_RECONCILED);
        Long id = compensations.selectByOrderNumber("9001").getId();

        assertEquals(1, compensations.claimByOrder("9001", "claim-a"));
        execute("UPDATE seckill_compensation_record SET locked_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) "
                + "WHERE id=" + id);
        assertEquals(1, compensations.claimByOrder("9001", "claim-b"));

        assertEquals(0, compensations.markRollbackResultOwned(id, "claim-a", "SUCCEEDED", null));
        assertEquals(1, compensations.markRollbackResultOwned(id, "claim-b", "SUCCEEDED", null));
        assertEquals("SUCCEEDED", scalarString("SELECT status FROM seckill_compensation_record WHERE id=" + id));
    }

    @Test
    @DisplayName("timeout lease 重领后旧 owner 不能写失败，新 owner 才能完成 attempt")
    void timeoutConsumeLeaseUsesPerDeliveryFencingToken() throws Exception {
        SeckillMessageLogMapper messages = context.getBean(SeckillMessageLogMapper.class);
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status,due_at) VALUES "
                + "('SECKILL_ORDER_TIMEOUT:9001','ORDER_TIMEOUT','TIMEOUT_RECOVERY','9001',7,19,'41',"
                + "'delay.exchange','delay.routingKey','BROKER_ACKED',DATE_SUB(NOW(),INTERVAL 1 SECOND))");

        assertEquals(1, messages.claimTimeoutConsumeAttempt("SECKILL_ORDER_TIMEOUT:9001", 1,
                "9001", "41", "timeout-a"));
        assertEquals(1, scalarInt("SELECT processing_attempt FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        execute("UPDATE seckill_message_log SET locked_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'");
        assertEquals(1, messages.claimTimeoutConsumeAttempt("SECKILL_ORDER_TIMEOUT:9001", 1,
                "9001", "41", "timeout-b"));

        assertEquals(0, messages.recordTimeoutConsumeFailure("SECKILL_ORDER_TIMEOUT:9001", 1,
                "TIMEOUT_CONSUME_FAILURE", "timeout-a"));
        assertEquals(1, messages.markTimeoutConsumedAttempt("SECKILL_ORDER_TIMEOUT:9001", 1,
                "timeout-b"));
        assertEquals("CONSUMED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001' AND processing_attempt IS NOT NULL"));
    }

    @Test
    @DisplayName("timeout publish 耗尽只迁移一次并在 dueAt 后执行至多三次本地兜底")
    void timeoutPublishExhaustionUsesBoundedFallbackAttempts() throws Exception {
        SeckillMessageLogMapper messages = context.getBean(SeckillMessageLogMapper.class);
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status,publish_attempt,current_correlation_id,"
                + "confirm_status,due_at,next_retry_at) VALUES "
                + "('SECKILL_ORDER_TIMEOUT:9001','ORDER_TIMEOUT','TIMEOUT_RECOVERY','9001',7,19,'41',"
                + "'delay.exchange','delay.routingKey','TIMEOUT_PUBLISH_PENDING',5,"
                + "'SECKILL_ORDER_TIMEOUT:9001:P5','PENDING',DATE_SUB(NOW(),INTERVAL 1 SECOND),NOW())");

        assertEquals(1, messages.markPublishAttemptsExhausted(5));
        assertEquals(0, messages.markPublishAttemptsExhausted(5));
        assertEquals("TIMEOUT_FALLBACK_PENDING", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertEquals("TIMEOUT_FALLBACK", scalarString("SELECT publish_purpose FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertTrue(messages.selectRecoverable(100).stream()
                .anyMatch(row -> "SECKILL_ORDER_TIMEOUT:9001".equals(row.getMessageId())));

        assertEquals(1, messages.recordTimeoutFallbackFailure("SECKILL_ORDER_TIMEOUT:9001", "first", 3));
        execute("UPDATE seckill_message_log SET next_retry_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'");
        assertEquals(1, messages.recordTimeoutFallbackFailure("SECKILL_ORDER_TIMEOUT:9001", "second", 3));
        execute("UPDATE seckill_message_log SET next_retry_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'");
        assertEquals(1, messages.recordTimeoutFallbackFailure("SECKILL_ORDER_TIMEOUT:9001", "third", 3));

        assertEquals(3, scalarInt("SELECT fallback_attempt FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertEquals("MANUAL_REQUIRED", scalarString("SELECT status FROM seckill_message_log "
                + "WHERE message_id='SECKILL_ORDER_TIMEOUT:9001'"));
        assertTrue(messages.selectRecoverable(100).stream()
                .noneMatch(row -> "SECKILL_ORDER_TIMEOUT:9001".equals(row.getMessageId())));
    }

    @Test
    @DisplayName("producer 与 orphan claim 并发时唯一消息 fence 禁止 PREPARED 和补偿并存")
    void producerAndOrphanClaimShareDurableUniqueFence() throws Exception {
        SeckillMessagePrepareTransaction prepare = context.getBean(SeckillMessagePrepareTransaction.class);
        SeckillOrphanClaimTransaction orphan = context.getBean(SeckillOrphanClaimTransaction.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int index = 0; index < 12; index++) {
                String orderNumber = String.valueOf(9300 + index);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> producer = pool.submit(() -> {
                    await(start);
                    try {
                        prepare.prepareOrderCreate(orderNumber, 7L, 19L, "{}");
                    } catch (RuntimeException lostFenceRace) {
                        // The unique message fence deliberately lets exactly one side win.
                    }
                });
                Future<?> reconciler = pool.submit(() -> {
                    await(start);
                    orphan.claim(new SeckillReservationSnapshot(19L, 7L, orderNumber,
                            true, true, Duration.ofMinutes(10)));
                });
                start.countDown();
                producer.get();
                reconciler.get();

                assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_message_log WHERE business_key='"
                        + orderNumber + "' AND message_type='ORDER_CREATE'"));
                int compensationCount = scalarInt("SELECT COUNT(*) FROM seckill_compensation_record "
                        + "WHERE order_number='" + orderNumber + "'");
                String status = scalarString("SELECT status FROM seckill_message_log WHERE business_key='"
                        + orderNumber + "' AND message_type='ORDER_CREATE'");
                assertTrue((compensationCount == 0 && "PREPARED".equals(status))
                                || (compensationCount == 1 && "COMPENSATION_PENDING".equals(status)),
                        "message and compensation must reflect the same fence winner");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void invoke(SeckillOrderConsumer consumer, Message message,
                        CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            consumer.consume(message, mock(Channel.class));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private Message envelope(long tag) {
        return envelope(tag, 1);
    }

    private Message envelope(long tag, int attempt) {
        try {
            SeckillMessage body = new SeckillMessage();
            body.setOrderNumber("9001");
            body.setUserId(7L);
            body.setCouponId(19L);
            return MessageBuilder.withBody(new ObjectMapper().writeValueAsBytes(body))
                    .setMessageId("SECKILL_ORDER_CREATE:9001").setDeliveryTag(tag)
                    .setContentType("application/json")
                    .setHeader("fsm-message-type", "ORDER_CREATE")
                    .setHeader("fsm-schema-version", 1)
                    .setHeader("fsm-business-key", "9001")
                    .setHeader("fsm-publish-attempt", 1)
                    .setHeader("fsm-consume-attempt", attempt)
                    .build();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private Map<String, Object> datasourceSettings() throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "datasource");
        }
    }

    private Path configPath() {
        String configured = System.getProperty("b6.config");
        if (configured == null || configured.trim().isEmpty()) throw new IllegalStateException("b6.config is required");
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

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getInt(1);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) throw new IllegalStateException("unsafe schema");
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {SeckillOrderMapper.class, SeckillCouponMapper.class,
            SeckillMessageLogMapper.class, SeckillCompensationRecordMapper.class,
            SeckillReconciliationCandidateMapper.class, SeckillReconciliationAnomalyMapper.class})
    static class SpringMysqlConfig {
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
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean SeckillAfterCommitDispatcher afterCommitDispatcher() { return new SeckillAfterCommitDispatcher(); }
        @Bean SeckillReliablePublisher reliablePublisher() { return mock(SeckillReliablePublisher.class); }
        @Bean SeckillCompensationService compensationService(SeckillCompensationRecordMapper mapper) {
            return new SeckillCompensationService(mapper) {
                @Override
                public void requestRelease(String orderNumber, Long userId, Long couponId,
                                           String reason, long evidenceMask) {
                    if (FAIL_COMPENSATION.get()) throw new IllegalStateException("injected compensation failure");
                    super.requestRelease(orderNumber, userId, couponId, reason, evidenceMask);
                }
            };
        }
        @Bean SeckillBusinessDeadLetterService deadLetterService(SeckillMessageLogMapper messages,
                SeckillCompensationService compensation) {
            return new SeckillBusinessDeadLetterService(messages, compensation);
        }
        @Bean SeckillConsumeFailureTransaction consumeFailureTransaction(SeckillMessageLogMapper messages,
                SeckillBusinessDeadLetterService deadLetter) {
            return new SeckillConsumeFailureTransaction(messages, deadLetter);
        }
        @Bean SeckillInvalidMessageService invalidMessageService(SeckillMessageLogMapper messages) {
            return new SeckillInvalidMessageService(messages);
        }
        @Bean SeckillMessagePrepareTransaction messagePrepareTransaction(SeckillMessageLogMapper messages,
                SeckillCompensationRecordMapper compensations) {
            return new SeckillMessagePrepareTransaction(messages, compensations);
        }
        @Bean SeckillOrphanClaimTransaction orphanClaimTransaction(SeckillMessageLogMapper messages,
                SeckillOrderMapper orders, SeckillCompensationService compensation) {
            return new SeckillOrphanClaimTransaction(messages, orders, compensation);
        }
        @Bean SeckillOrderConsumeTransaction consumeTransaction(SeckillOrderMapper orders,
                SeckillCouponMapper coupons, SeckillMessageLogMapper messages,
                SeckillAfterCommitDispatcher afterCommit, SeckillReliablePublisher publisher) {
            return new SeckillOrderConsumeTransaction(orders, coupons, messages, afterCommit, publisher);
        }
        @Bean SeckillDuplicateOrderTransaction duplicateTransaction(SeckillOrderMapper orders,
                SeckillMessageLogMapper messages) {
            return new SeckillDuplicateOrderTransaction(orders, messages);
        }
    }
}
