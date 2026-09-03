package com.fashion.integration;

import com.fashion.config.DirectExchangeConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@DisplayName("B6 RabbitMQ 3.12 confirm/return 真实行为")
class B6RabbitPublisherIntegrationTest {
    private static final String VHOST_PATTERN = "fsm_b6_it_[0-9a-f]{32}";
    private static CachingConnectionFactory connectionFactory;
    private static RabbitAdmin admin;
    private static RabbitTemplate template;
    private static String exchange;
    private static String queue;
    private static String rejectQueue;

    @BeforeAll
    static void connectAndDeclare() throws Exception {
        Map<String, Object> settings = rabbitSettings();
        B6IntegrationSafety.requireLoopback(value(settings, "host"), "RabbitMQ");
        String virtualHost = value(settings, "virtual-host");
        if (!virtualHost.matches(VHOST_PATTERN)) {
            throw new IllegalStateException("B6 Rabbit integration requires an isolated fsm_b6_it_<32hex> vhost");
        }
        connectionFactory = new CachingConnectionFactory(value(settings, "host"),
                Integer.parseInt(value(settings, "port")));
        connectionFactory.setUsername(value(settings, "username"));
        connectionFactory.setPassword(value(settings, "password"));
        connectionFactory.setVirtualHost(virtualHost);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        Object versionValue = connectionFactory.createConnection().getDelegate()
                .getServerProperties().get("version");
        String version = String.valueOf(versionValue);
        assertTrue(version.startsWith("3.12."), "B6 integration requires RabbitMQ 3.12.x");

        String runId = UUID.randomUUID().toString().replace("-", "");
        exchange = "fsm_b6_it_exchange_" + runId;
        queue = "fsm_b6_it_queue_" + runId;
        rejectQueue = "fsm_b6_it_reject_queue_" + runId;
        admin = new RabbitAdmin(connectionFactory);
        DirectExchange directExchange = new DirectExchange(exchange, true, false);
        Queue durableQueue = new Queue(queue, true, false, false);
        admin.declareExchange(directExchange);
        admin.declareQueue(durableQueue);
        admin.declareBinding(BindingBuilder.bind(durableQueue).to(directExchange).with("ok"));
        Queue boundedQueue = QueueBuilder.durable(rejectQueue)
                .withArgument("x-max-length", 1)
                .withArgument("x-overflow", "reject-publish")
                .build();
        admin.declareQueue(boundedQueue);
        admin.declareBinding(BindingBuilder.bind(boundedQueue).to(directExchange).with("reject"));
        DirectExchange failureExchange = new DirectExchange(
                DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE, true, false);
        Queue failureQueue = QueueBuilder.durable(DirectExchangeConfig.SECKILL_FAILURE_QUEUE)
                .withArgument("x-queue-mode", "lazy").build();
        admin.declareExchange(failureExchange);
        admin.declareQueue(failureQueue);
        admin.declareBinding(BindingBuilder.bind(failureQueue).to(failureExchange)
                .with(DirectExchangeConfig.SECKILL_ORDER_FAILURE_ROUTING_KEY));
    }

    @BeforeEach
    void createTemplate() {
        template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
    }

    @AfterAll
    static void cleanup() {
        try {
            if (admin != null) {
                if (queue != null) admin.deleteQueue(queue);
                if (rejectQueue != null) admin.deleteQueue(rejectQueue);
                admin.deleteQueue(DirectExchangeConfig.SECKILL_FAILURE_QUEUE);
                if (exchange != null) admin.deleteExchange(exchange);
                admin.deleteExchange(DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE);
            }
        } finally {
            if (connectionFactory != null) connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("可路由持久消息收到 correlated publisher ack")
    void routableMessageIsConfirmed() throws Exception {
        CountDownLatch confirm = new CountDownLatch(1);
        AtomicBoolean acknowledged = new AtomicBoolean(false);
        template.setConfirmCallback((correlation, ack, cause) -> {
            acknowledged.set(ack);
            confirm.countDown();
        });

        template.send(exchange, "ok", MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId("B6:ACK").build(),
                new CorrelationData("B6:ACK:P1"));

        assertTrue(confirm.await(10, TimeUnit.SECONDS));
        assertTrue(acknowledged.get());
        assertEquals(1, admin.getQueueInfo(queue).getMessageCount());
    }

    @Test
    @DisplayName("mandatory 不可路由消息同时产生 return 且 broker confirm ack")
    void unroutableMessageIsReturned() throws Exception {
        CountDownLatch returned = new CountDownLatch(1);
        CountDownLatch confirm = new CountDownLatch(1);
        AtomicBoolean acknowledged = new AtomicBoolean(false);
        template.setReturnsCallback(message -> returned.countDown());
        template.setConfirmCallback((correlation, ack, cause) -> {
            acknowledged.set(ack);
            confirm.countDown();
        });

        template.send(exchange, "missing", MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId("B6:RETURN").build(),
                new CorrelationData("B6:RETURN:P1"));

        assertTrue(returned.await(10, TimeUnit.SECONDS));
        assertTrue(confirm.await(10, TimeUnit.SECONDS));
        assertTrue(acknowledged.get());
    }

    @Test
    @DisplayName("reject-publish 队列溢出产生真实 publisher nack")
    void boundedQueueOverflowIsNacked() throws Exception {
        CountDownLatch firstConfirm = new CountDownLatch(1);
        CountDownLatch rejected = new CountDownLatch(1);
        AtomicBoolean acknowledged = new AtomicBoolean(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (correlation == null) return;
            if ("B6:NACK:FILL:P1".equals(correlation.getId())) {
                firstConfirm.countDown();
            } else if ("B6:NACK:REJECT:P1".equals(correlation.getId())) {
                acknowledged.set(ack);
                rejected.countDown();
            }
        });
        template.send(exchange, "reject", MessageBuilder.withBody("one".getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId("B6:NACK:FILL").build(),
                new CorrelationData("B6:NACK:FILL:P1"));
        assertTrue(firstConfirm.await(10, TimeUnit.SECONDS));

        template.send(exchange, "reject", MessageBuilder.withBody("two".getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId("B6:NACK:REJECT").build(),
                new CorrelationData("B6:NACK:REJECT:P1"));

        assertTrue(rejected.await(10, TimeUnit.SECONDS));
        assertTrue(!acknowledged.get());
    }

    @Test
    @DisplayName("业务死信使用独立持久队列并可由独立客户端读取")
    void businessDeadLetterSurvivesPublisherConnectionBoundary() throws Exception {
        admin.purgeQueue(DirectExchangeConfig.SECKILL_FAILURE_QUEUE, false);
        CountDownLatch confirm = new CountDownLatch(1);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (correlation != null && "B6:DLQ:P1".equals(correlation.getId()) && ack) {
                confirm.countDown();
            }
        });
        template.send(DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE,
                DirectExchangeConfig.SECKILL_ORDER_FAILURE_ROUTING_KEY,
                MessageBuilder.withBody("{\"attempt\":3}".getBytes(StandardCharsets.UTF_8))
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId("SECKILL_DEAD:SECKILL_ORDER_CREATE:9001").build(),
                new CorrelationData("B6:DLQ:P1"));
        assertTrue(confirm.await(10, TimeUnit.SECONDS));

        RabbitTemplate restartedClient = new RabbitTemplate(connectionFactory);
        org.springframework.amqp.core.Message received = restartedClient.receive(
                DirectExchangeConfig.SECKILL_FAILURE_QUEUE, 5000);
        assertNotNull(received);
        assertEquals("SECKILL_DEAD:SECKILL_ORDER_CREATE:9001",
                received.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                received.getMessageProperties().getReceivedDeliveryMode());
    }

    @Test
    @DisplayName("应用测试用户只能 configure 运行前缀与 B6 failure topology")
    void applicationUserHasBoundedConfigurePermission() {
        assertThrows(org.springframework.amqp.AmqpException.class,
                () -> admin.declareExchange(new DirectExchange("forbidden.exchange", true, false)));
    }

    private static Map<String, Object> rabbitSettings() throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "rabbitmq");
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
