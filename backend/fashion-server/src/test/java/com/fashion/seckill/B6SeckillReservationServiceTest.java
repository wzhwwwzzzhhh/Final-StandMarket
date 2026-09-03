package com.fashion.seckill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 Redis reservation 编排")
class B6SeckillReservationServiceTest {

    private StringRedisTemplate redisTemplate;
    private SeckillReservationService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new SeckillReservationService(redisTemplate);
    }

    @Test
    @DisplayName("预扣原子写入订单 token 和活跃券 registry")
    @SuppressWarnings("unchecked")
    void reserveUsesSixKeysAndOrderToken() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                eq("1"), eq("1725148800"), eq("7"), eq("9001")))
                .thenReturn(0L);

        assertEquals(SeckillReservationService.ReserveResult.RESERVED,
                service.reserve(19L, 7L, "9001", 1725148800L));

        verify(redisTemplate).execute(any(RedisScript.class), eq(Arrays.asList(
                        "seckill:coupon:stock:19",
                        "seckill:coupon:startTime:19",
                        "seckill:coupon:endTime:19",
                        "seckill:coupon:users:19",
                        "seckill:coupon:reservations:19",
                        "seckill:coupon:reservation:index")),
                eq("1"), eq("1725148800"), eq("7"), eq("9001"));
    }

    @Test
    @DisplayName("回滚必须携带期望订单号并区分目标成功但账本异常")
    @SuppressWarnings("unchecked")
    void rollbackIsTokenAwareAndPreservesLedgerAnomaly() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                eq("19"), eq("1"), eq("7"), eq("9001")))
                .thenReturn(2L);

        assertEquals(SeckillReservationService.RollbackResult.APPLIED_LEDGER_INCONSISTENT,
                service.rollback(19L, 7L, "9001"));

        verify(redisTemplate).execute(any(RedisScript.class), eq(Arrays.asList(
                        "seckill:coupon:stock:19",
                        "seckill:coupon:users:19",
                        "seckill:coupon:reservations:19",
                        "seckill:coupon:reservation:index")),
                eq("19"), eq("1"), eq("7"), eq("9001"));
    }
}
