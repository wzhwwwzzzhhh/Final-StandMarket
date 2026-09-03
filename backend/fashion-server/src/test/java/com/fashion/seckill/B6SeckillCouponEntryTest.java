package com.fashion.seckill;

import com.fashion.context.BaseContext;
import com.fashion.dto.SeckillSubmitResult;
import com.fashion.result.Result;
import com.fashion.service.impl.SeckillCouponServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 秒杀 API 入口")
class B6SeckillCouponEntryTest {
    private SeckillSubmitOrchestrator orchestrator;
    private RLock lock;
    private SeckillCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(SeckillSubmitOrchestrator.class);
        RedissonClient redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redisson.getLock("seckill:lock:7")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        service = new SeckillCouponServiceImpl();
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        ReflectionTestUtils.setField(service, "seckillSubmitOrchestrator", orchestrator);
        BaseContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("可靠编排成功时返回处理中订单号")
    void mapsProcessingSubmission() {
        when(orchestrator.submit(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new SeckillSubmitOrchestrator.Submission(
                        SeckillSubmitOrchestrator.Outcome.PROCESSING, "9001"));

        Result<SeckillSubmitResult> result = service.seckillCoupon(19L);

        assertEquals(1, result.getCode());
        assertEquals("9001", result.getData().getOrderNumber());
        assertEquals(0, result.getData().getStatus());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("同步投递失败不返回处理中订单")
    void deliveryFailureIsVisibleToCaller() {
        when(orchestrator.submit(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new SeckillSubmitOrchestrator.Submission(
                        SeckillSubmitOrchestrator.Outcome.DELIVERY_FAILED, "9001"));

        Result<SeckillSubmitResult> result = service.seckillCoupon(19L);

        assertEquals(0, result.getCode());
        assertEquals("消息投递失败，请稍后重试", result.getMsg());
        assertNull(result.getData());
        verify(lock).unlock();
    }
}
