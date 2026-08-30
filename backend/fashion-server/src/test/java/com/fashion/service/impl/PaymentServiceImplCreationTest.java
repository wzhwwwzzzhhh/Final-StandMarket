package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.PaymentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 普通订单支付宝流水创建")
class PaymentServiceImplCreationTest {

    private PaymentServiceImpl service;
    private OrderMapper orderMapper;
    private PaymentMapper paymentMapper;
    private Orders order;

    @BeforeEach
    void setUp() {
        BaseContext.setUserId(7L);
        order = new Orders();
        order.setId(100L);
        order.setUserId(7L);
        order.setStatus(1);
        order.setPayStatus(0);
        order.setAmount(new BigDecimal("10.00"));
        order.setStockDeducted(1);

        orderMapper = mock(OrderMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        when(orderMapper.getByIdForUpdate(100L)).thenReturn(order);

        service = new PaymentServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "paymentMapper", paymentMapper);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("订单行锁内复用金额和支付方式一致的活动流水")
    void reusesMatchingActivePayment() {
        Payment existing = payment("10.00", 2);
        when(paymentMapper.getActiveByOrderIdAndType(100L, 0)).thenReturn(existing);

        Payment result = service.createAlipayPayment(100L);

        assertSame(existing, result);
        verify(orderMapper).getByIdForUpdate(100L);
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("活动流水金额不一致时拒绝静默复用")
    void rejectsMismatchedActivePayment() {
        when(paymentMapper.getActiveByOrderIdAndType(100L, 0))
                .thenReturn(payment("9.99", 2));

        assertThrows(IllegalStateException.class, () -> service.createAlipayPayment(100L));

        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("唯一冲突后只做一次锁定当前读并返回一致赢家")
    void duplicateInsertConvergesOnWinnerWithOneCurrentRead() {
        Payment winner = payment("10.00", 2);
        when(paymentMapper.getActiveByOrderIdAndType(100L, 0)).thenReturn(null);
        when(paymentMapper.getActiveByOrderIdAndTypeForUpdate(100L, 0)).thenReturn(winner);
        doThrow(new DuplicateKeyException("active payment race"))
                .when(paymentMapper).insert(any(Payment.class));

        Payment result = service.createAlipayPayment(100L);

        assertSame(winner, result);
        verify(paymentMapper).getActiveByOrderIdAndType(100L, 0);
        verify(paymentMapper).getActiveByOrderIdAndTypeForUpdate(100L, 0);
    }

    @Test
    @DisplayName("已支付订单即使没有活动流水也不能创建新尝试")
    void rejectsAlreadyPaidOrder() {
        order.setStatus(2);
        order.setPayStatus(1);

        assertThrows(IllegalStateException.class, () -> service.createAlipayPayment(100L));

        verify(paymentMapper, never()).getActiveByOrderIdAndType(100L, 0);
        verify(paymentMapper, never()).getActiveByOrderIdAndTypeForUpdate(100L, 0);
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("订单归属不是当前用户时拒绝创建流水")
    void rejectsOrderOwnedByAnotherUser() {
        order.setUserId(8L);

        assertThrows(IllegalStateException.class, () -> service.createAlipayPayment(100L));

        verify(paymentMapper, never()).getActiveByOrderIdAndType(100L, 0);
        verify(paymentMapper, never()).getActiveByOrderIdAndTypeForUpdate(100L, 0);
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("唯一冲突后找不到活动赢家时保留原异常")
    void unrelatedDuplicateKeyIsNotMisclassifiedAsPaymentRace() {
        DuplicateKeyException conflict = new DuplicateKeyException("pay_no collision");
        when(paymentMapper.getActiveByOrderIdAndType(100L, 0)).thenReturn(null);
        when(paymentMapper.getActiveByOrderIdAndTypeForUpdate(100L, 0)).thenReturn(null);
        doThrow(conflict).when(paymentMapper).insert(any(Payment.class));

        DuplicateKeyException thrown = assertThrows(
                DuplicateKeyException.class, () -> service.createAlipayPayment(100L));

        assertSame(conflict, thrown);
        verify(paymentMapper).getActiveByOrderIdAndType(100L, 0);
        verify(paymentMapper).getActiveByOrderIdAndTypeForUpdate(100L, 0);
    }

    @Test
    @DisplayName("未成功扣库存的历史订单不能新建或复用支付流水")
    void rejectsOrderWithoutDeductedInventory() {
        order.setStockDeducted(0);
        when(paymentMapper.getActiveByOrderIdAndType(100L, 0)).thenReturn(payment("10.00", 2));

        assertThrows(IllegalStateException.class, () -> service.createAlipayPayment(100L));

        verify(paymentMapper, never()).getActiveByOrderIdAndType(100L, 0);
        verify(paymentMapper, never()).insert(any());
    }

    private Payment payment(String amount, int payMethod) {
        Payment payment = new Payment();
        payment.setId(10L);
        payment.setOrderId(100L);
        payment.setOrderType(0);
        payment.setAmount(new BigDecimal(amount));
        payment.setPayMethod(payMethod);
        payment.setStatus(0);
        payment.setPayNo("PAY-100");
        return payment;
    }
}
