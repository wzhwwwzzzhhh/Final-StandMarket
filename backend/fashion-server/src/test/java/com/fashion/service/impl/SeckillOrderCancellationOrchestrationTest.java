package com.fashion.service.impl;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.dto.SeckillCancelResponse;
import com.fashion.seckill.SeckillCompensationService;
import com.fashion.seckill.SeckillCompensationExecutor;
import com.fashion.seckill.SeckillReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B5 秒杀取消 Redis 编排")
class SeckillOrderCancellationOrchestrationTest {

    private SeckillOrderServiceImpl service;
    private SeckillCancellationTransaction transaction;
    private SeckillCompensationExecutor compensationExecutor;

    @BeforeEach
    void setUp() {
        service = new SeckillOrderServiceImpl();
        transaction = mock(SeckillCancellationTransaction.class);
        compensationExecutor = mock(SeckillCompensationExecutor.class);
        ReflectionTestUtils.setField(service, "seckillCancellationTransaction", transaction);
        ReflectionTestUtils.setField(service, "seckillCompensationExecutor", compensationExecutor);
    }

    @Test
    @DisplayName("数据库提交后 Lua 回补成功返回已取消")
    @SuppressWarnings("unchecked")
    void successfulRedisCompensationReturnsCancelled() {
        SeckillCancelCommand command = new SeckillCancelCommand("SEC-1", 7L, 19L);
        when(transaction.cancelTrusted("SEC-1")).thenReturn(command);
        when(compensationExecutor.execute("SEC-1"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);

        SeckillCancelResponse response = service.cancelOrder("SEC-1");

        assertEquals(SeckillCancelResponse.CANCELLED, response.getOutcome());
        assertEquals(3, response.getOrderStatus());
        verify(compensationExecutor).execute("SEC-1");
    }

    @Test
    @DisplayName("Lua 未应用时数据库取消仍成功并明确返回待对账")
    @SuppressWarnings("unchecked")
    void unappliedRedisCompensationReturnsPending() {
        when(transaction.cancelTimeout(5L))
                .thenReturn(new SeckillCancelCommand("SEC-2", 7L, 19L));
        when(compensationExecutor.execute("SEC-2"))
                .thenReturn(SeckillReservationService.RollbackResult.TOKEN_MISMATCH);

        SeckillCancelResponse response = service.cancelTimeoutOrder(5L);

        assertEquals(SeckillCancelResponse.REDIS_RECONCILIATION_PENDING, response.getOutcome());
        assertEquals(3, response.getOrderStatus());
    }

    @Test
    @DisplayName("Redis 异常不会伪装成整体失败而是进入待对账")
    @SuppressWarnings("unchecked")
    void redisExceptionReturnsPending() {
        when(transaction.cancelTrusted("SEC-3"))
                .thenReturn(new SeckillCancelCommand("SEC-3", 7L, 19L));
        when(compensationExecutor.execute("SEC-3"))
                .thenReturn(SeckillReservationService.RollbackResult.INFRA_FAILURE);

        SeckillCancelResponse response = service.cancelOrder("SEC-3");

        assertEquals(SeckillCancelResponse.REDIS_RECONCILIATION_PENDING, response.getOutcome());
    }

    @Test
    @DisplayName("数据库 CAS 未命中时不执行 Redis")
    void databaseDidNotCancelReturnsConflict() {
        when(transaction.cancelTrusted("SEC-4")).thenReturn(null);

        assertNull(service.cancelOrder("SEC-4"));
        verify(compensationExecutor, org.mockito.Mockito.never())
                .execute(org.mockito.ArgumentMatchers.anyString());
    }
}
