package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 订单取消 CAS")
class OrderCancellationCasTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;
    private OrderCancellationService cancellationService;
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
        cancellationService = mock(OrderCancellationService.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderCancellationService", cancellationService);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("用户取消 CAS 失败时拒绝释放优惠券")
    void userCancelDoesNotReleaseCouponWhenStateChanged() {
        doThrow(new IllegalStateException("state changed"))
                .when(cancellationService).cancelForUser(100L, 7L);

        assertThrows(IllegalStateException.class, () -> service.cancel(100L));

        verify(cancellationService).cancelForUser(100L, 7L);
    }

    @Test
    @DisplayName("用户取消 CAS 成功后才释放优惠券")
    void userCancelReleasesCouponAfterSuccessfulTransition() {
        when(cancellationService.cancelForUser(100L, 7L)).thenReturn(true);

        assertDoesNotThrow(() -> service.cancel(100L));

        verify(cancellationService).cancelForUser(100L, 7L);
    }

    @Test
    @DisplayName("超时取消 CAS 失败时不释放优惠券")
    void timeoutCancelDoesNotReleaseCouponWhenPaymentWonRace() {
        when(orderMapper.selectTimeoutOrders(30, 0L, 100)).thenReturn(Collections.singletonList(pendingOrder));
        when(cancellationService.cancelTimeout(100L)).thenReturn(false);

        service.autoCancelTimeoutOrders();

        verify(cancellationService).cancelTimeout(100L);
    }
}
