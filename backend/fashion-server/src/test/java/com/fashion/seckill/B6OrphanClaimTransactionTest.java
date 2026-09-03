package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 悬空补偿与消息创建串行化")
class B6OrphanClaimTransactionTest {
    @Test
    @DisplayName("锁定消息唯一键后只有仍无消息无订单时建立补偿")
    void absentMessageAndOrderCreatesDurableClaim() {
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillOrderMapper orders = mock(SeckillOrderMapper.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillReservationSnapshot snapshot = snapshot();
        when(messages.insertIfAbsent(org.mockito.ArgumentMatchers.any(SeckillMessageLog.class)))
                .thenReturn(1);
        SeckillMessageLog fence = new SeckillMessageLog();
        fence.setMessageType("ORDER_CREATE");
        fence.setPublishPurpose("INITIAL");
        fence.setStatus("COMPENSATION_PENDING");
        fence.setLastError("ORPHAN_FENCE");
        fence.setBusinessKey("9001");
        fence.setUserId(7L);
        fence.setCouponId(19L);
        when(messages.selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001")).thenReturn(fence);

        SeckillOrphanClaimTransaction.Result result = new SeckillOrphanClaimTransaction(
                messages, orders, compensation).claim(snapshot);

        assertEquals(SeckillOrphanClaimTransaction.Result.CLAIMED, result);
        verify(messages).selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001");
        verify(messages).insertIfAbsent(org.mockito.ArgumentMatchers.argThat(candidate ->
                "SECKILL_ORDER_CREATE:9001".equals(candidate.getMessageId())
                        && "COMPENSATION_PENDING".equals(candidate.getStatus())));
        verify(compensation).requestRelease("9001", 7L, 19L, "ORPHAN_RECONCILED",
                SeckillCompensationService.EVIDENCE_ORPHAN_RECONCILED);
    }

    @Test
    @DisplayName("并发 producer 已插入同一唯一键时 orphan fence 失败且不得补偿")
    void producerInsertWinsUniqueFenceRace() {
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillOrderMapper orders = mock(SeckillOrderMapper.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        when(messages.insertIfAbsent(org.mockito.ArgumentMatchers.any(SeckillMessageLog.class)))
                .thenReturn(0);
        SeckillMessageLog producer = new SeckillMessageLog();
        producer.setMessageType("ORDER_CREATE");
        producer.setPublishPurpose("INITIAL");
        producer.setStatus("PREPARED");
        when(messages.selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001")).thenReturn(producer);

        SeckillOrphanClaimTransaction.Result result = new SeckillOrphanClaimTransaction(
                messages, orders, compensation).claim(snapshot());

        assertEquals(SeckillOrphanClaimTransaction.Result.MESSAGE_EXISTS, result);
        verify(compensation, never()).requestRelease(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("并发 PREPARED 已存在时不得再释放 reservation")
    void concurrentPreparedMessageWinsOverOrphanClaim() {
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillOrderMapper orders = mock(SeckillOrderMapper.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillMessageLog producer = new SeckillMessageLog();
        producer.setMessageType("ORDER_CREATE");
        producer.setPublishPurpose("INITIAL");
        producer.setStatus("PREPARED");
        when(messages.selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001")).thenReturn(producer);

        SeckillOrphanClaimTransaction.Result result = new SeckillOrphanClaimTransaction(
                messages, orders, compensation).claim(snapshot());

        assertEquals(SeckillOrphanClaimTransaction.Result.MESSAGE_EXISTS, result);
        verify(compensation, never()).requestRelease(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("身份一致的已取消订单即使有历史消息也建立可审计释放授权")
    void cancelledOrderAuthorizesReservationRelease() {
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillOrderMapper orders = mock(SeckillOrderMapper.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        when(messages.selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001"))
                .thenReturn(new SeckillMessageLog());
        SeckillOrder cancelled = new SeckillOrder();
        cancelled.setOrderNumber("9001");
        cancelled.setUserId(7L);
        cancelled.setCouponId(19L);
        cancelled.setStatus(3);
        when(orders.selectByOrderNumber("9001")).thenReturn(cancelled);

        SeckillOrphanClaimTransaction.Result result = new SeckillOrphanClaimTransaction(
                messages, orders, compensation).claim(snapshot());

        assertEquals(SeckillOrphanClaimTransaction.Result.CLAIMED, result);
        verify(compensation).requestRelease("9001", 7L, 19L, "CANCELLED_ORDER_RECONCILED",
                SeckillCompensationService.EVIDENCE_CANCELLED_ORDER_RECONCILED);
    }

    private SeckillReservationSnapshot snapshot() {
        return new SeckillReservationSnapshot(19L, 7L, "9001", true, true,
                Duration.ofMinutes(10));
    }
}
