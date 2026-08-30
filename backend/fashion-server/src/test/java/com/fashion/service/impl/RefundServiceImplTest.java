package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.entity.Refund;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.RefundMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("B3 退款审核状态边界")
class RefundServiceImplTest {

    private RefundServiceImpl service;
    private RefundMapper refundMapper;
    private OrderMapper orderMapper;
    private OrderDetailMapper orderDetailMapper;
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        refundMapper = successfulWriteMock(RefundMapper.class);
        orderMapper = successfulWriteMock(OrderMapper.class);
        orderDetailMapper = mock(OrderDetailMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new RefundServiceImpl();
        ReflectionTestUtils.setField(service, "refundMapper", refundMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        setFieldIfPresent("orderDetailMapper", orderDetailMapper);
        setFieldIfPresent("productMapper", productMapper);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("审核同意只调用固定的 0 到 1 CAS 且没有库存副作用")
    void approveUsesDedicatedWaitingRefundCasWithoutInventorySideEffects() {
        Refund refund = pendingRefund();
        when(refundMapper.getById(10L)).thenReturn(refund);
        when(orderDetailMapper.listByOrderId(100L)).thenReturn(Collections.emptyList());

        service.approve(10L, "同意");

        Set<String> refundCalls = invokedMethods(refundMapper);
        assertTrue(refundCalls.contains("approvePending"));
        assertFalse(refundCalls.contains("update"));
        assertNull(refund.getRefundTime());
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("审核拒绝使用退款和订单两个固定 CAS 而不是通用更新")
    void rejectUsesDedicatedRefundAndOrderCas() {
        Refund refund = pendingRefund();
        when(refundMapper.getById(10L)).thenReturn(refund);
        Orders order = order(6);
        when(orderMapper.getById(100L)).thenReturn(order);

        service.reject(10L, "拒绝");

        Set<String> refundCalls = invokedMethods(refundMapper);
        Set<String> orderCalls = invokedMethods(orderMapper);
        assertTrue(refundCalls.contains("rejectPending"));
        assertTrue(orderCalls.contains("restoreRejectedRefundOrder"));
        assertFalse(refundCalls.contains("update"));
        assertFalse(orderCalls.contains("update"));
    }

    @Test
    @DisplayName("退款申请使用订单状态 CAS 而不是通用订单更新")
    void applyUsesOrderStateCas() {
        BaseContext.setUserId(7L);
        Orders order = order(3);
        when(orderMapper.getById(100L)).thenReturn(order);
        when(refundMapper.listByOrderIdAndStatus(100L, 0)).thenReturn(Collections.emptyList());

        service.apply(100L, "不合适");

        Set<String> orderCalls = invokedMethods(orderMapper);
        assertTrue(orderCalls.contains("markRefunding"));
        assertFalse(orderCalls.contains("update"));
    }

    @Test
    @DisplayName("同意 CAS 零行必须报告记录已处理")
    void approveRejectsZeroRowCas() {
        refundMapper = writeResultMock(RefundMapper.class, 0);
        ReflectionTestUtils.setField(service, "refundMapper", refundMapper);
        when(refundMapper.getById(10L)).thenReturn(pendingRefund());

        assertThrows(RuntimeException.class, () -> service.approve(10L, "同意"));
    }

    @Test
    @DisplayName("申请订单 CAS 零行必须回滚退款申请")
    void applyRejectsZeroRowOrderCas() {
        BaseContext.setUserId(7L);
        orderMapper = writeResultMock(OrderMapper.class, 0);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        when(orderMapper.getById(100L)).thenReturn(order(3));
        when(refundMapper.listByOrderIdAndStatus(100L, 0)).thenReturn(Collections.emptyList());

        assertThrows(RuntimeException.class, () -> service.apply(100L, "不合适"));
    }

    @Test
    @DisplayName("拒绝时订单恢复 CAS 零行必须使整个操作失败")
    void rejectFailsWhenOrderRestoreCasAffectsNoRows() {
        orderMapper = writeResultMock(OrderMapper.class, 0);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        when(refundMapper.getById(10L)).thenReturn(pendingRefund());
        when(orderMapper.getById(100L)).thenReturn(order(6));

        assertThrows(RuntimeException.class, () -> service.reject(10L, "拒绝"));
    }

    @Test
    @DisplayName("拒绝不得恢复到 3 或 4 以外的申请前状态")
    void rejectRequiresRestorablePreviousOrderStatus() {
        Refund refund = pendingRefund();
        refund.setOrderStatus(2);
        when(refundMapper.getById(10L)).thenReturn(refund);

        assertThrows(RuntimeException.class, () -> service.reject(10L, "拒绝"));
    }

    private Refund pendingRefund() {
        Refund refund = new Refund();
        refund.setId(10L);
        refund.setOrderId(100L);
        refund.setUserId(7L);
        refund.setRefundNo("RF-10");
        refund.setStatus(0);
        refund.setOrderStatus(3);
        return refund;
    }

    private Orders order(int status) {
        Orders order = new Orders();
        order.setId(100L);
        order.setUserId(7L);
        order.setStatus(status);
        order.setAmount(new BigDecimal("88.00"));
        return order;
    }

    private Set<String> invokedMethods(Object mock) {
        return mockingDetails(mock).getInvocations().stream()
                .map(Invocation::getMethod)
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
    }

    private void setFieldIfPresent(String name, Object value) {
        if (ReflectionUtils.findField(RefundServiceImpl.class, name) != null) {
            ReflectionTestUtils.setField(service, name, value);
        }
    }

    private <T> T successfulWriteMock(Class<T> type) {
        return writeResultMock(type, 1);
    }

    private <T> T writeResultMock(Class<T> type, int writeResult) {
        return mock(type, invocation -> {
            if (invocation.getMethod().getReturnType() == int.class) {
                return writeResult;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
