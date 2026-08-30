package com.fashion.service.impl;

import com.fashion.entity.Orders;
import com.fashion.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 管理端普通订单状态边界")
class OrderAdminStatusTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;
    private Orders order;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        order = new Orders();
        order.setId(100L);
        order.setStatus(2);
        order.setPayStatus(1);
        when(orderMapper.getById(100L)).thenReturn(order);
        when(orderMapper.updateAdminStatus(100L, 3)).thenReturn(1);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
    }

    @Test
    @DisplayName("管理端不能把普通订单直接设置为已支付")
    void rejectsPaidStatus() {
        assertThrows(RuntimeException.class, () -> service.updateAdminStatus(100L, 2));

        verify(orderMapper, never()).updateAdminStatus(100L, 2);
    }

    @Test
    @DisplayName("管理端不能推进未支付订单")
    void rejectsUnpaidOrderProgression() {
        order.setStatus(1);
        order.setPayStatus(0);

        assertThrows(RuntimeException.class, () -> service.updateAdminStatus(100L, 3));

        verify(orderMapper, never()).updateAdminStatus(100L, 3);
    }

    @Test
    @DisplayName("管理端通用状态入口不能绕过发货专用流程")
    void rejectsOperationalStateBypass() {
        assertThrows(IllegalStateException.class, () -> service.updateAdminStatus(100L, 3));

        verify(orderMapper, never()).updateAdminStatus(100L, 3);
    }
}
