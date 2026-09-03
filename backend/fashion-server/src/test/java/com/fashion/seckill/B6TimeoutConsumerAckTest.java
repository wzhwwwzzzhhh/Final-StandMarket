package com.fashion.seckill;

import com.fashion.dto.SeckillCancelResponse;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.service.SeckillOrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 超时消费者有限重试与 ack 决策")
class B6TimeoutConsumerAckTest {
    private SeckillOrderService orderService;
    private SeckillMessageLogMapper messageMapper;
    private SeckillConsumeFailureTransaction failureTransaction;
    private SeckillInvalidMessageService invalidMessageService;
    private SeckillListenerPauser pauser;
    private SeckillTimeoutConsumer consumer;
    private Channel channel;

    @BeforeEach
    void setUp() {
        orderService = mock(SeckillOrderService.class);
        messageMapper = mock(SeckillMessageLogMapper.class);
        failureTransaction = mock(SeckillConsumeFailureTransaction.class);
        invalidMessageService = mock(SeckillInvalidMessageService.class);
        pauser = mock(SeckillListenerPauser.class);
        consumer = new SeckillTimeoutConsumer(orderService, messageMapper, failureTransaction,
                invalidMessageService, pauser);
        channel = mock(Channel.class);
        when(messageMapper.claimTimeoutConsumeAttempt(org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_TIMEOUT:9001"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("9001"),
                org.mockito.ArgumentMatchers.eq("41"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1);
    }

    @Test
    @DisplayName("超时取消完成并持久标记后 ack")
    void successMarksConsumedThenAcknowledges() throws Exception {
        Message message = timeoutMessage(31L);
        when(orderService.cancelTimeoutOrder(41L)).thenReturn(SeckillCancelResponse.cancelled("9001"));
        when(messageMapper.markTimeoutConsumedAttempt(org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_TIMEOUT:9001"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        consumer.consume(message, channel);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(orderService, messageMapper, channel);
        order.verify(orderService).cancelTimeoutOrder(41L);
        order.verify(messageMapper).markTimeoutConsumedAttempt(org.mockito.ArgumentMatchers.eq(
                "SECKILL_ORDER_TIMEOUT:9001"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.anyString());
        order.verify(channel).basicAck(31L, false);
    }

    @Test
    @DisplayName("业务异常持久进入有限重试后 ack 原消息")
    void businessFailureIsPersistedThenAcknowledged() throws Exception {
        Message message = timeoutMessage(32L);
        when(orderService.cancelTimeoutOrder(41L)).thenThrow(new IllegalStateException("status conflict"));
        when(failureTransaction.recordTimeout(org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_TIMEOUT:9001"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("CONSUME_FAILURE"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(SeckillConsumeFailureTransaction.Outcome.RETRY);

        consumer.consume(message, channel);

        verify(failureTransaction).recordTimeout(org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_TIMEOUT:9001"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("CONSUME_FAILURE"),
                org.mockito.ArgumentMatchers.anyString());
        verify(channel).basicAck(32L, false);
        verify(channel, never()).basicReject(32L, true);
    }

    @Test
    @DisplayName("MySQL 无法持久失败事实时先 requeue 当前消息再异步暂停 listener")
    void persistenceFailureRequeuesBeforePausing() throws Exception {
        Message message = timeoutMessage(33L);
        when(orderService.cancelTimeoutOrder(41L)).thenThrow(new IllegalStateException("transaction failed"));
        when(failureTransaction.recordTimeout(org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_TIMEOUT:9001"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("CONSUME_FAILURE"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("mysql down"));

        consumer.consume(message, channel);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(channel, pauser);
        order.verify(channel).basicReject(33L, true);
        order.verify(pauser).pause("seckillTimeoutConsumer");
    }

    @Test
    @DisplayName("身份不匹配的超时消息隔离后 ack")
    void invalidIdentityIsQuarantined() throws Exception {
        Message invalid = MessageBuilder.withBody("41".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setMessageId("SECKILL_ORDER_CREATE:9001").setDeliveryTag(34L).build();

        consumer.consume(invalid, channel);

        verify(invalidMessageService).record(invalid, "IDENTITY_INVALID");
        verify(channel).basicAck(34L, false);
        verify(orderService, never()).cancelTimeoutOrder(41L);
    }

    @Test
    @DisplayName("合法并发 PROCESSING 冲突 ack 重复投递且不形成热 requeue")
    void concurrentProcessingAcknowledgesDuplicateWithoutHotRequeue() throws Exception {
        Message message = timeoutMessage(35L);
        when(messageMapper.claimTimeoutConsumeAttempt(org.mockito.ArgumentMatchers.eq(
                "SECKILL_ORDER_TIMEOUT:9001"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("9001"), org.mockito.ArgumentMatchers.eq("41"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        com.fashion.entity.SeckillMessageLog processing = new com.fashion.entity.SeckillMessageLog();
        processing.setStatus("PROCESSING");
        processing.setMessageType("ORDER_TIMEOUT");
        processing.setBusinessKey("9001");
        processing.setPayload("41");
        when(messageMapper.selectByMessageId("SECKILL_ORDER_TIMEOUT:9001")).thenReturn(processing);

        consumer.consume(message, channel);

        verify(channel).basicAck(35L, false);
        verify(channel, never()).basicReject(35L, true);
        verify(pauser, never()).pause("seckillTimeoutConsumer");
    }

    @Test
    @DisplayName("合法 headers 但 body orderId 与持久 timeout payload 错配时隔离且不取消")
    void persistedTimeoutPayloadMismatchIsQuarantined() throws Exception {
        Message message = timeoutMessage(36L);
        when(messageMapper.claimTimeoutConsumeAttempt(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        com.fashion.entity.SeckillMessageLog persisted = new com.fashion.entity.SeckillMessageLog();
        persisted.setMessageType("ORDER_TIMEOUT");
        persisted.setBusinessKey("9001");
        persisted.setPayload("42");
        when(messageMapper.selectByMessageId("SECKILL_ORDER_TIMEOUT:9001")).thenReturn(persisted);

        consumer.consume(message, channel);

        verify(invalidMessageService).record(message, "SOURCE_IDENTITY_MISMATCH");
        verify(channel).basicAck(36L, false);
        verify(orderService, never()).cancelTimeoutOrder(org.mockito.ArgumentMatchers.anyLong());
        verify(failureTransaction, never()).record(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("无 source log 的格式合法 timeout 消息永久隔离而非暂停重投")
    void missingTimeoutSourceIsQuarantined() throws Exception {
        Message message = timeoutMessage(37L);
        when(messageMapper.claimTimeoutConsumeAttempt(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        when(messageMapper.selectByMessageId("SECKILL_ORDER_TIMEOUT:9001")).thenReturn(null);

        consumer.consume(message, channel);

        verify(invalidMessageService).record(message, "SOURCE_IDENTITY_MISMATCH");
        verify(channel).basicAck(37L, false);
        verify(pauser, never()).pause(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("超限 timeout body 在业务调用前直接隔离")
    void oversizedTimeoutBodyIsQuarantinedBeforeBusinessCall() throws Exception {
        Message oversized = MessageBuilder.withBody(new byte[1024 * 1024])
                .setMessageId("SECKILL_ORDER_TIMEOUT:9001").setDeliveryTag(38L).build();

        consumer.consume(oversized, channel);

        verify(invalidMessageService).record(oversized, "PAYLOAD_TOO_LARGE");
        verify(channel).basicAck(38L, false);
        verify(orderService, never()).cancelTimeoutOrder(org.mockito.ArgumentMatchers.anyLong());
        verify(messageMapper, never()).claimTimeoutConsumeAttempt(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private Message timeoutMessage(long deliveryTag) {
        return MessageBuilder.withBody("41".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setMessageId("SECKILL_ORDER_TIMEOUT:9001")
                .setDeliveryTag(deliveryTag)
                .setContentType("application/json")
                .setHeader("fsm-message-type", "ORDER_TIMEOUT")
                .setHeader("fsm-schema-version", 1)
                .setHeader("fsm-business-key", "9001")
                .setHeader("fsm-publish-attempt", 1)
                .setHeader("fsm-consume-attempt", 1)
                .build();
    }
}
