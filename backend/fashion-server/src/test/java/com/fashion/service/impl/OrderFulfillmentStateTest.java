package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B2 订单履约状态服务")
class OrderFulfillmentStateTest {

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("发货只接受专用 CAS 的单行成功")
    void deliveryRequiresCasWinner() {
        OrderMapper mapper = mock(OrderMapper.class);
        when(mapper.deliverPaidOrder(org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("SF"), org.mockito.ArgumentMatchers.eq("X1"), any(LocalDateTime.class)))
                .thenReturn(1);
        OrderServiceImpl service = service(mapper);

        assertDoesNotThrow(() -> service.deliver(100L, "SF", "X1"));
        verify(mapper).deliverPaidOrder(org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("SF"), org.mockito.ArgumentMatchers.eq("X1"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("非法前态确认收货必须拒绝")
    void confirmationRejectsCasLoser() {
        BaseContext.setUserId(7L);
        OrderMapper mapper = mock(OrderMapper.class);
        when(mapper.confirmDeliveredOrder(org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class))).thenReturn(0);
        OrderServiceImpl service = service(mapper);

        assertThrows(IllegalStateException.class, () -> service.confirm(100L));
        verify(mapper).confirmDeliveredOrder(org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class));
    }

    private OrderServiceImpl service(OrderMapper mapper) {
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        return service;
    }
}
