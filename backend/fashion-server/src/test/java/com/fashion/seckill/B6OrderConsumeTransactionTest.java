package com.fashion.seckill;

import com.fashion.entity.SeckillMessage;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 秒杀订单消费事务")
class B6OrderConsumeTransactionTest {
    private SeckillOrderMapper orderMapper;
    private SeckillCouponMapper couponMapper;
    private SeckillMessageLogMapper messageMapper;
    private SeckillAfterCommitDispatcher afterCommit;
    private SeckillReliablePublisher publisher;
    private SeckillOrderConsumeTransaction transaction;

    @BeforeEach
    void setUp() {
        orderMapper = mock(SeckillOrderMapper.class);
        couponMapper = mock(SeckillCouponMapper.class);
        messageMapper = mock(SeckillMessageLogMapper.class);
        afterCommit = mock(SeckillAfterCommitDispatcher.class);
        publisher = mock(SeckillReliablePublisher.class);
        transaction = new SeckillOrderConsumeTransaction(orderMapper, couponMapper,
                messageMapper, afterCommit, publisher);
        when(couponMapper.reduceStock(19L)).thenReturn(1);
        when(messageMapper.insert(any(SeckillMessageLog.class))).thenReturn(1);
        when(messageMapper.claimConsumeAttempt(eq("SECKILL_ORDER_CREATE:9001"), eq(1), eq("9001"), eq(7L), eq(19L),
                anyString()))
                .thenReturn(1);
        when(messageMapper.markConsumedAttempt(eq("SECKILL_ORDER_CREATE:9001"), eq(1), anyString())).thenReturn(1);
    }

    @Test
    @DisplayName("订单库存消费状态和 timeout PREPARED 先完成再提交后发布")
    void delayPublishIsRegisteredOnlyAfterAtomicWrites() {
        SeckillOrderConsumeTransaction.Result result = transaction.consume(message(),
                "SECKILL_ORDER_CREATE:9001", 1);

        assertEquals(SeckillOrderConsumeTransaction.Result.CREATED, result);
        ArgumentCaptor<SeckillOrder> order = ArgumentCaptor.forClass(SeckillOrder.class);
        ArgumentCaptor<SeckillMessageLog> timeout = ArgumentCaptor.forClass(SeckillMessageLog.class);
        InOrder writes = inOrder(orderMapper, couponMapper, messageMapper, afterCommit);
        writes.verify(messageMapper).claimConsumeAttempt(eq("SECKILL_ORDER_CREATE:9001"), eq(1),
                eq("9001"), eq(7L), eq(19L), anyString());
        writes.verify(orderMapper).selectByOrderNumber("9001");
        writes.verify(orderMapper).insert(order.capture());
        writes.verify(couponMapper).reduceStock(19L);
        writes.verify(messageMapper).insert(timeout.capture());
        writes.verify(messageMapper).markConsumedAttempt(eq("SECKILL_ORDER_CREATE:9001"), eq(1), anyString());
        writes.verify(afterCommit).run(any(Runnable.class));
        assertEquals("SECKILL_ORDER_TIMEOUT:9001", timeout.getValue().getMessageId());
        assertEquals("ORDER_TIMEOUT", timeout.getValue().getMessageType());
        assertEquals("PREPARED", timeout.getValue().getStatus());
        verify(publisher, never()).publish(any(), any());

        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(callback.capture());
        callback.getValue().run();
        verify(publisher).publish("SECKILL_ORDER_TIMEOUT:9001", "TIMEOUT_RECOVERY");
    }

    @Test
    @DisplayName("库存扣减失败时抛错且不得登记延迟发送")
    void stockFailureRollsBackWithoutDelayPublish() {
        when(couponMapper.reduceStock(19L)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> transaction.consume(message(), "SECKILL_ORDER_CREATE:9001", 1));

        verify(messageMapper, never()).insert(any(SeckillMessageLog.class));
        verify(afterCommit, never()).run(any());
    }

    @Test
    @DisplayName("等价重复投递不重复订单和库存")
    void equivalentDuplicateIsIdempotent() {
        SeckillOrder existing = new SeckillOrder();
        existing.setOrderNumber("9001");
        existing.setUserId(7L);
        existing.setCouponId(19L);
        when(orderMapper.selectByOrderNumber("9001")).thenReturn(existing);

        assertEquals(SeckillOrderConsumeTransaction.Result.DUPLICATE,
                transaction.consume(message(), "SECKILL_ORDER_CREATE:9001", 1));

        verify(orderMapper, never()).insert(any());
        verify(couponMapper, never()).reduceStock(19L);
        verify(afterCommit, never()).run(any());
    }

    @Test
    @DisplayName("消费耗尽或补偿终态无法 claim 时不得落单和扣库存")
    void terminalSourceStateCannotCreateOrder() {
        when(messageMapper.claimConsumeAttempt(eq("SECKILL_ORDER_CREATE:9001"), eq(1), eq("9001"), eq(7L), eq(19L),
                anyString()))
                .thenReturn(0);
        SeckillMessageLog terminal = new SeckillMessageLog();
        terminal.setMessageId("SECKILL_ORDER_CREATE:9001");
        terminal.setMessageType("ORDER_CREATE");
        terminal.setBusinessKey("9001");
        terminal.setUserId(7L);
        terminal.setCouponId(19L);
        terminal.setStatus("CONSUME_EXHAUSTED");
        terminal.setConsumeAttempt(3);
        when(messageMapper.selectByMessageId("SECKILL_ORDER_CREATE:9001")).thenReturn(terminal);

        assertEquals(SeckillOrderConsumeTransaction.Result.IGNORED_TERMINAL,
                transaction.consume(message(), "SECKILL_ORDER_CREATE:9001", 1));

        verify(orderMapper, never()).insert(any());
        verify(couponMapper, never()).reduceStock(19L);
        verify(messageMapper, never()).markConsumedAttempt(any(),
                org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    @DisplayName("非正数业务身份在查询来源日志前即按永久坏消息拒绝")
    void nonPositiveIdentityIsPermanentEnvelopeFailure() {
        SeckillMessage invalid = message();
        invalid.setUserId(0L);

        assertThrows(SeckillPermanentEnvelopeException.class,
                () -> transaction.validateSourceIdentity(invalid, "SECKILL_ORDER_CREATE:9001"));

        verify(messageMapper, never()).selectByMessageId(any());
    }

    private SeckillMessage message() {
        SeckillMessage message = new SeckillMessage();
        message.setOrderNumber("9001");
        message.setUserId(7L);
        message.setCouponId(19L);
        return message;
    }
}
