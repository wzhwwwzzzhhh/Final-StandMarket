package com.fashion.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.SeckillMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@DisplayName("B6 订单消费者 ack 决策")
class B6OrderConsumerAckTest {
    private SeckillOrderConsumeTransaction consumeTransaction;
    private SeckillConsumeFailureTransaction failureTransaction;
    private SeckillListenerPauser pauser;
    private SeckillDuplicateOrderTransaction duplicateTransaction;
    private SeckillInvalidMessageService invalidMessageService;
    private SeckillOrderConsumer consumer;
    private Channel channel;

    @BeforeEach
    void setUp() {
        consumeTransaction = mock(SeckillOrderConsumeTransaction.class);
        failureTransaction = mock(SeckillConsumeFailureTransaction.class);
        pauser = mock(SeckillListenerPauser.class);
        invalidMessageService = mock(SeckillInvalidMessageService.class);
        duplicateTransaction = mock(SeckillDuplicateOrderTransaction.class);
        consumer = new SeckillOrderConsumer(new ObjectMapper(), consumeTransaction,
                duplicateTransaction, failureTransaction, invalidMessageService, pauser);
        channel = mock(Channel.class);
    }

    @Test
    @DisplayName("非法 envelope 持久隔离后 ack，且不进入普通业务重试")
    void invalidEnvelopeIsQuarantinedThenAcknowledged() throws Exception {
        Message invalid = MessageBuilder.withBody("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setDeliveryTag(14L).setReceivedExchange("market.direct")
                .setReceivedRoutingKey("seckillOrder").setContentType("application/json").build();

        consumer.consume(invalid, channel);

        verify(invalidMessageService).record(invalid, "PAYLOAD_INVALID");
        verify(channel).basicAck(14L, false);
        verify(failureTransaction, never()).record(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("缺少 B6 schema/type/business/attempt headers 的合法 JSON 也必须隔离")
    void missingContractHeadersAreQuarantined() throws Exception {
        SeckillMessage body = new SeckillMessage();
        body.setOrderNumber("9001");
        body.setUserId(7L);
        body.setCouponId(19L);
        Message invalid = MessageBuilder.withBody(new ObjectMapper().writeValueAsBytes(body))
                .setMessageId("SECKILL_ORDER_CREATE:9001").setDeliveryTag(16L)
                .setContentType("application/json").build();

        consumer.consume(invalid, channel);

        verify(invalidMessageService).record(invalid, "ENVELOPE_INVALID");
        verify(channel).basicAck(16L, false);
        verify(consumeTransaction, never()).consume(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("事务成功后显式 ack 且不 requeue")
    void successAcknowledgesOnce() throws Exception {
        consumer.consume(message(11L), channel);

        verify(channel).basicAck(11L, false);
        verify(channel, never()).basicReject(11L, true);
    }

    @Test
    @DisplayName("业务失败被 MySQL 接管后 ack 原消息并由恢复轨道有限重发")
    void persistedFailureAcknowledgesOriginal() throws Exception {
        Message message = message(12L);
        when(consumeTransaction.consume(org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_CREATE:9001"),
                org.mockito.ArgumentMatchers.eq(1)))
                .thenThrow(new IllegalStateException("stock conflict"));
        when(failureTransaction.record("SECKILL_ORDER_CREATE:9001", 1, "CONSUME_FAILURE"))
                .thenReturn(SeckillConsumeFailureTransaction.Outcome.RETRY);

        consumer.consume(message, channel);

        verify(channel).basicAck(12L, false);
        verify(channel, never()).basicNack(12L, false, true);
    }

    @Test
    @DisplayName("MySQL 无法接管失败事实时先 requeue 当前消息再异步暂停 listener")
    void persistenceFailureRequeuesBeforePausing() throws Exception {
        Message message = message(13L);
        when(consumeTransaction.consume(org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1)))
                .thenThrow(new IllegalStateException("transaction failed"));
        when(failureTransaction.record("SECKILL_ORDER_CREATE:9001", 1, "CONSUME_FAILURE"))
                .thenThrow(new IllegalStateException("mysql down"));

        consumer.consume(message, channel);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(channel, pauser);
        order.verify(channel).basicReject(13L, true);
        order.verify(pauser).pause("seckillOrderConsumer");
    }

    @Test
    @DisplayName("并发唯一冲突在原事务回滚后由新事务确认等价重复再 ack")
    void duplicateKeyUsesIndependentIdentityResolver() throws Exception {
        Message message = message(15L);
        when(consumeTransaction.consume(org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_CREATE:9001"),
                org.mockito.ArgumentMatchers.eq(1)))
                .thenThrow(new DuplicateKeyException("uk order_number"));

        consumer.consume(message, channel);

        verify(duplicateTransaction).resolve(org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_CREATE:9001"));
        verify(channel).basicAck(15L, false);
        verify(failureTransaction, never()).record(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("body 与持久 PREPARED 身份错配时永久隔离且不消耗合法重试次数")
    void persistentIdentityMismatchIsQuarantinedWithoutRetry() throws Exception {
        Message message = message(17L);
        doThrow(new SeckillPermanentEnvelopeException("source identity mismatch"))
                .when(consumeTransaction).validateSourceIdentity(
                        org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                        org.mockito.ArgumentMatchers.eq("SECKILL_ORDER_CREATE:9001"));

        consumer.consume(message, channel);

        verify(invalidMessageService).record(message, "SOURCE_IDENTITY_MISMATCH");
        verify(channel).basicAck(17L, false);
        verify(consumeTransaction, never()).consume(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(failureTransaction, never()).record(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("业务异常摘要不持久化 JDBC URL、password 或 SQL/payload")
    void sensitiveExceptionMessageIsReducedToControlledCode() throws Exception {
        Message message = message(18L);
        when(consumeTransaction.consume(org.mockito.ArgumentMatchers.any(SeckillMessage.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1)))
                .thenThrow(new IllegalStateException(
                        "jdbc:mysql://db/orders?password=secret SQL select * payload={card:123}"));

        consumer.consume(message, channel);

        verify(failureTransaction).record("SECKILL_ORDER_CREATE:9001", 1, "CONSUME_FAILURE");
    }

    private Message message(long deliveryTag) throws Exception {
        SeckillMessage body = new SeckillMessage();
        body.setOrderNumber("9001");
        body.setUserId(7L);
        body.setCouponId(19L);
        return MessageBuilder.withBody(new ObjectMapper().writeValueAsBytes(body))
                .setMessageId("SECKILL_ORDER_CREATE:9001")
                .setDeliveryTag(deliveryTag)
                .setContentType("application/json")
                .setHeader("fsm-message-type", "ORDER_CREATE")
                .setHeader("fsm-schema-version", 1)
                .setHeader("fsm-business-key", "9001")
                .setHeader("fsm-publish-attempt", 1)
                .setHeader("fsm-consume-attempt", 1)
                .build();
    }
}
