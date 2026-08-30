package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("B2 订单用户鉴权边界")
class OrderAuthorizationTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;

    @BeforeEach
    void setUp() {
        BaseContext.removeUserId();
        orderMapper = mock(OrderMapper.class);
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", mock(OrderDetailMapper.class));
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("未登录不能读取用户1的订单")
    void anonymousCannotListFallbackUserOrders() {
        assertThrows(IllegalStateException.class, () -> service.listUserOrders(null));
        verify(orderMapper, never()).listUserOrders(any(), any());
    }

    @Test
    @DisplayName("未登录不能取消用户1的订单")
    void anonymousCannotCancelFallbackUserOrder() {
        assertThrows(IllegalStateException.class, () -> service.cancel(1L));
        verify(orderMapper, never()).getById(any());
    }

    @Test
    @DisplayName("未登录不能确认用户1的订单")
    void anonymousCannotConfirmFallbackUserOrder() {
        assertThrows(IllegalStateException.class, () -> service.confirm(1L));
        verify(orderMapper, never()).getById(any());
    }
}
