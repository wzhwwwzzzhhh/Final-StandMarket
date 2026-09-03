package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.config.SeckillMqConfirmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("B6 RabbitMQ 可靠性配置")
class B6MqConfigurationTest {
    @Test
    @DisplayName("新增业务失败队列且不篡改既有超时死信语义")
    void failureQueueIsSeparateFromTimeoutQueue() {
        DirectExchangeConfig config = new DirectExchangeConfig();

        Queue failure = config.seckillFailureQueue();
        Queue timeoutDelay = config.delayQueue();

        assertEquals("seckill.failure.queue", failure.getName());
        assertTrue(failure.isDurable());
        assertFalse(failure.getArguments().containsKey("x-message-ttl"));
        assertEquals(DirectExchangeConfig.SECKILL_ORDER_TIMEOUT_MILLIS,
                timeoutDelay.getArguments().get("x-message-ttl"));
        assertEquals("dead.exchange", timeoutDelay.getArguments().get("x-dead-letter-exchange"));
        Binding order = config.seckillOrderFailureBinding();
        Binding timeout = config.seckillTimeoutFailureBinding();
        Binding invalid = config.seckillInvalidFailureBinding();
        assertEquals("seckill.order.failed", order.getRoutingKey());
        assertEquals("seckill.timeout.failed", timeout.getRoutingKey());
        assertEquals("seckill.invalid.failed", invalid.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitTemplate 只注册集中 confirm/return callback 并启用 mandatory")
    void callbacksAndMandatoryAreCentralized() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        SeckillPublisherCallbackHandler handler = mock(SeckillPublisherCallbackHandler.class);

        new SeckillMqConfirmConfig(handler).configure(template);

        verify(template).setMandatory(true);
        verify(template).setConfirmCallback(org.mockito.ArgumentMatchers.any());
        verify(template).setReturnsCallback(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("correlation 已携带 mandatory return 时 confirm 必须 failure-dominant")
    void returnedCorrelationCannotBePersistedAsAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        SeckillPublisherCallbackHandler handler = mock(SeckillPublisherCallbackHandler.class);
        new SeckillMqConfirmConfig(handler).configure(template);
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        verify(template).setConfirmCallback(confirmCaptor.capture());
        org.springframework.amqp.core.Message message = MessageBuilder.withBody("{}".getBytes())
                .setMessageId("SECKILL_ORDER_CREATE:9001")
                .setHeader("fsm-publish-attempt", 1).build();
        ReturnedMessage returned = new ReturnedMessage(message, 312, "NO_ROUTE", "market.direct", "missing");
        CorrelationData correlation = new CorrelationData("SECKILL_ORDER_CREATE:9001:P1");
        correlation.setReturned(returned);

        confirmCaptor.getValue().confirm(correlation, true, null);

        verify(handler).handleReturn("SECKILL_ORDER_CREATE:9001", 1, 312,
                "NO_ROUTE", "market.direct", "missing");
        verify(handler).handleConfirm("SECKILL_ORDER_CREATE:9001:P1", false, "mandatory return");
    }
}
