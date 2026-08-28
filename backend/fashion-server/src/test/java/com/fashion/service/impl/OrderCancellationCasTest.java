package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.mapper.OrderMapper;
import com.fashion.service.CouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 订单取消 CAS")
class OrderCancellationCasTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;
    private CouponService couponService;
    private Orders pendingOrder;

    @BeforeEach
    void setUp() {
        BaseContext.setUserId(7L);
        pendingOrder = new Orders();
        pendingOrder.setId(100L);
        pendingOrder.setUserId(7L);
        pendingOrder.setStatus(1);
        pendingOrder.setPayStatus(0);

        orderMapper = mock(OrderMapper.class);
        couponService = mock(CouponService.class);
        when(orderMapper.getById(100L)).thenReturn(pendingOrder);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "couponService", couponService);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("用户取消 CAS 失败时拒绝释放优惠券")
    void userCancelDoesNotReleaseCouponWhenStateChanged() {
        when(orderMapper.cancelPending(anyLong(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.cancel(100L));

        verify(couponService, never()).release(anyLong(), anyLong());
    }

    @Test
    @DisplayName("用户取消 CAS 成功后才释放优惠券")
    void userCancelReleasesCouponAfterSuccessfulTransition() {
        when(orderMapper.cancelPending(anyLong(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.cancel(100L));

        verify(couponService).release(7L, 100L);
    }

    @Test
    @DisplayName("超时取消 CAS 失败时不释放优惠券")
    void timeoutCancelDoesNotReleaseCouponWhenPaymentWonRace() {
        when(orderMapper.selectTimeoutCouponOrders(30)).thenReturn(Collections.singletonList(pendingOrder));
        when(orderMapper.cancelPending(anyLong(), any())).thenReturn(0);

        service.autoCancelTimeoutCouponOrders();

        verify(couponService, never()).release(anyLong(), anyLong());
    }
}
