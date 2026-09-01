package com.fashion.service.impl;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.dto.SeckillCancelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B5 秒杀取消 Redis 编排")
class SeckillOrderCancellationOrchestrationTest {

    private SeckillOrderServiceImpl service;
    private SeckillCancellationTransaction transaction;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        service = new SeckillOrderServiceImpl();
        transaction = mock(SeckillCancellationTransaction.class);
        redisTemplate = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "seckillCancellationTransaction", transaction);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        service.initSeckillRollbackScript();
    }

    @Test
    @DisplayName("数据库提交后 Lua 回补成功返回已取消")
    @SuppressWarnings("unchecked")
    void successfulRedisCompensationReturnsCancelled() {
        SeckillCancelCommand command = new SeckillCancelCommand("SEC-1", 7L, 19L);
        when(transaction.cancelTrusted("SEC-1")).thenReturn(command);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"), eq("7")))
                .thenReturn(1L);

        SeckillCancelResponse response = service.cancelOrder("SEC-1");

        assertEquals(SeckillCancelResponse.CANCELLED, response.getOutcome());
        assertEquals(3, response.getOrderStatus());
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(java.util.Arrays.asList("seckill:coupon:stock:19", "seckill:coupon:users:19")),
                eq("1"), eq("7"));
    }

    @Test
    @DisplayName("Lua 未应用时数据库取消仍成功并明确返回待对账")
    @SuppressWarnings("unchecked")
    void unappliedRedisCompensationReturnsPending() {
        when(transaction.cancelTimeout(5L))
                .thenReturn(new SeckillCancelCommand("SEC-2", 7L, 19L));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"), eq("7")))
                .thenReturn(0L);

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
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"), eq("7")))
                .thenThrow(new IllegalStateException("redis unavailable"));

        SeckillCancelResponse response = service.cancelOrder("SEC-3");

        assertEquals(SeckillCancelResponse.REDIS_RECONCILIATION_PENDING, response.getOutcome());
    }

    @Test
    @DisplayName("数据库 CAS 未命中时不执行 Redis")
    void databaseDidNotCancelReturnsConflict() {
        when(transaction.cancelTrusted("SEC-4")).thenReturn(null);

        assertNull(service.cancelOrder("SEC-4"));
        verify(redisTemplate, org.mockito.Mockito.never())
                .execute(any(RedisScript.class), any(List.class), any());
    }
}
