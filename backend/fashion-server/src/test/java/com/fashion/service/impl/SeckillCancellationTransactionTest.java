package com.fashion.service.impl;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.seckill.SeckillCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B5 秒杀取消数据库事务")
class SeckillCancellationTransactionTest {

    private SeckillCancellationTransaction transaction;
    private SeckillOrderMapper orderMapper;
    private SeckillCouponMapper couponMapper;
    private SeckillCompensationService compensationService;

    @BeforeEach
    void setUp() {
        transaction = new SeckillCancellationTransaction();
        orderMapper = mock(SeckillOrderMapper.class);
        couponMapper = mock(SeckillCouponMapper.class);
        compensationService = mock(SeckillCompensationService.class);
        ReflectionTestUtils.setField(transaction, "seckillOrderMapper", orderMapper);
        ReflectionTestUtils.setField(transaction, "seckillCouponMapper", couponMapper);
        ReflectionTestUtils.setField(transaction, "seckillCompensationService", compensationService);
    }

    @Test
    @DisplayName("用户取消只有 CAS 胜者回补一次数据库库存")
    void ownerCancellationWinnerRestoresStockOnce() {
        SeckillOrder order = order("SEC-1", 7L, 19L);
        when(orderMapper.selectByOrderNumberAndUserId("SEC-1", 7L)).thenReturn(order);
        when(orderMapper.cancelPendingByOrderNumberAndUserId("SEC-1", 7L)).thenReturn(1);
        when(couponMapper.restoreStock(19L)).thenReturn(1);

        SeckillCancelCommand command = transaction.cancelForUser("SEC-1", 7L);

        assertNotNull(command);
        assertEquals("SEC-1", command.getOrderNumber());
        assertEquals(7L, command.getUserId());
        assertEquals(19L, command.getCouponId());
        verify(couponMapper).restoreStock(19L);
        verify(compensationService).requestRelease("SEC-1", 7L, 19L, "CANCEL_COMMITTED",
                SeckillCompensationService.EVIDENCE_CANCEL_COMMITTED);
    }

    @Test
    @DisplayName("取消 CAS 失败不回补库存也不生成 Redis 指令")
    void lostCancellationRaceDoesNotRestoreStock() {
        SeckillOrder order = order("SEC-2", 7L, 19L);
        when(orderMapper.selectByOrderNumberAndUserId("SEC-2", 7L)).thenReturn(order);
        when(orderMapper.cancelPendingByOrderNumberAndUserId("SEC-2", 7L)).thenReturn(0);

        assertNull(transaction.cancelForUser("SEC-2", 7L));

        verify(couponMapper, never()).restoreStock(19L);
    }

    @Test
    @DisplayName("数据库库存回补零行时抛错交给 Spring 回滚状态 CAS")
    void failedStockRestoreAbortsTransaction() {
        SeckillOrder order = order("SEC-3", 7L, 19L);
        when(orderMapper.selectByOrderNumber("SEC-3")).thenReturn(order);
        when(orderMapper.cancelPending("SEC-3")).thenReturn(1);
        when(couponMapper.restoreStock(19L)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> transaction.cancelTrusted("SEC-3"));
    }

    private SeckillOrder order(String orderNumber, Long userId, Long couponId) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber(orderNumber);
        order.setUserId(userId);
        order.setCouponId(couponId);
        return order;
    }
}
