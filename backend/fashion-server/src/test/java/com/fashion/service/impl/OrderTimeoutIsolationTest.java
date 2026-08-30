package com.fashion.service.impl;

import com.fashion.entity.Orders;
import com.fashion.mapper.OrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B2 超时订单逐单失败隔离")
class OrderTimeoutIsolationTest {

    @Test
    @DisplayName("中间订单失败不阻止后续候选进入独立取消事务")
    void middleFailureDoesNotStopFollowingOrder() {
        OrderMapper mapper = mock(OrderMapper.class);
        OrderCancellationService cancellation = mock(OrderCancellationService.class);
        when(mapper.selectTimeoutOrders(30, 0L, 100)).thenReturn(Arrays.asList(order(1L), order(2L), order(3L)));
        when(cancellation.cancelTimeout(1L)).thenReturn(true);
        doThrow(new IllegalStateException("injected failure")).when(cancellation).cancelTimeout(2L);
        when(cancellation.cancelTimeout(3L)).thenReturn(true);

        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderCancellationService", cancellation);

        service.autoCancelTimeoutOrders();

        verify(cancellation).cancelTimeout(1L);
        verify(cancellation).cancelTimeout(2L);
        verify(cancellation).cancelTimeout(3L);
    }

    private Orders order(Long id) {
        Orders order = new Orders();
        order.setId(id);
        order.setUserId(7L);
        return order;
    }
}
