package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 reliable publisher")
class B6ReliablePublisherTest {

    private SeckillMessageLogMapper mapper;
    private RabbitTemplate rabbitTemplate;
    private SeckillReliablePublisher publisher;
    private SeckillMessageLog log;

    @BeforeEach
    void setUp() {
        mapper = mock(SeckillMessageLogMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new SeckillReliablePublisher(mapper, rabbitTemplate);
        log = new SeckillMessageLog();
        log.setMessageId("SECKILL_ORDER_CREATE:9001");
        log.setMessageType("ORDER_CREATE");
        log.setPublishPurpose("INITIAL");
        log.setBusinessKey("9001");
        log.setPayload("{\"orderNumber\":\"9001\"}");
        log.setPayloadSchemaVersion(1);
        log.setExchangeName("market.direct");
        log.setRoutingKey("seckillOrder");
        log.setPublishAttempt(1);
        log.setConsumeAttempt(0);
        log.setCurrentCorrelationId("SECKILL_ORDER_CREATE:9001:P1");
        when(mapper.claimNextPublishAttempt(log.getMessageId(), "INITIAL")).thenReturn(1);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);
    }

    @Test
    @DisplayName("每次发布使用持久消息和 attempt 唯一 correlation")
    void publishesPersistentCorrelatedMessage() {
        publisher.publish(log.getMessageId(), "INITIAL");

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(eq("market.direct"), eq("seckillOrder"),
                message.capture(), correlation.capture());
        assertEquals("SECKILL_ORDER_CREATE:9001", message.getValue().getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getValue().getMessageProperties().getDeliveryMode());
        assertEquals(1, message.getValue().getMessageProperties().getHeaders().get("fsm-publish-attempt"));
        assertEquals(1, message.getValue().getMessageProperties().getHeaders().get("fsm-consume-attempt"));
        assertEquals("SECKILL_ORDER_CREATE:9001:P1", correlation.getValue().getId());
    }

    @Test
    @DisplayName("同步发送异常先持久化当前 attempt 失败再向调用方抛出")
    void synchronousFailureIsPersisted() {
        doThrow(new AmqpConnectException(new IllegalStateException("broker down")))
                .when(rabbitTemplate).send(eq("market.direct"), eq("seckillOrder"),
                        any(Message.class), any(CorrelationData.class));

        assertThrows(AmqpConnectException.class,
                () -> publisher.publish(log.getMessageId(), "INITIAL"));

        verify(mapper).markSynchronousFailure(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P1", "broker unavailable");
    }

    @Test
    @DisplayName("同步异常与已开始消费竞态时不得回滚已转移的 reservation 所有权")
    void synchronousFailureAfterConsumerClaimIsTreatedAsTransferred() {
        doThrow(new AmqpConnectException(new IllegalStateException("connection closed after write")))
                .when(rabbitTemplate).send(eq("market.direct"), eq("seckillOrder"),
                        any(Message.class), any(CorrelationData.class));
        when(mapper.markSynchronousFailure(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P1", "broker unavailable")).thenReturn(0);
        SeckillMessageLog processing = new SeckillMessageLog();
        processing.setStatus("PROCESSING");
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log, processing);

        assertDoesNotThrow(() -> publisher.publish(log.getMessageId(), "INITIAL"));
    }

    @Test
    @DisplayName("timeout 消息按不可变 dueAt 的剩余时间设置 per-message expiration")
    void timeoutUsesRemainingDeadline() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        publisher = new SeckillReliablePublisher(mapper, rabbitTemplate, clock);
        log.setMessageType("ORDER_TIMEOUT");
        log.setDueAt(LocalDateTime.ofInstant(clock.instant().plusSeconds(90), ZoneOffset.UTC));
        when(mapper.claimNextPublishAttempt(log.getMessageId(), "TIMEOUT_RECOVERY")).thenReturn(1);

        publisher.publish(log.getMessageId(), "TIMEOUT_RECOVERY");

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("market.direct"), eq("seckillOrder"),
                message.capture(), any(CorrelationData.class));
        assertEquals("90000", message.getValue().getMessageProperties().getExpiration());
    }
}
