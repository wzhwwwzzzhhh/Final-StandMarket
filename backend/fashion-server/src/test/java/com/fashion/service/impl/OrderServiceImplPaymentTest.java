package com.fashion.service.impl;

import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.mapper.OrderMapper;
import com.fashion.service.CouponService;
import com.fashion.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 普通订单支付回调事务")
class OrderServiceImplPaymentTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;
    private PaymentService paymentService;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        Orders order = order(2, 1);
        Payment payment = payment(0, null);

        orderMapper = mock(OrderMapper.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("getById") || name.equals("getByIdForUpdate")) {
                return order;
            }
            if (name.equals("markPaid")) {
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        paymentService = mock(PaymentService.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("getByIdForUpdate")) {
                return payment;
            }
            if (name.equals("updatePaySuccess")) {
                return true;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        couponService = mock(CouponService.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "paymentService", paymentService);
        ReflectionTestUtils.setField(service, "couponService", couponService);
    }

    @Test
    @DisplayName("已发货订单收到相同 trade_no 的重复通知时零写入成功返回")
    void shippedOrderAcceptsConsistentDuplicate() {
        Orders order = order(3, 1);
        Payment payment = payment(2, "TRADE-100");
        resetLockedRecords(order, payment);

        assertDoesNotThrow(() -> service.handlePayCallback(
                100L, 10L, "TRADE-100", LocalDateTime.now()));

        verify(paymentService, never()).updatePaySuccess(anyLong(), any(), any());
        verify(orderMapper, never()).markPaid(anyLong(), any());
        verify(couponService, never()).markUsed(anyLong(), anyLong());
    }

    @Test
    @DisplayName("已支付记录的 trade_no 不一致时拒绝吞掉异常通知")
    void conflictingTradeNumberIsRejected() {
        Orders order = order(3, 1);
        Payment payment = payment(2, "TRADE-ORIGINAL");
        resetLockedRecords(order, payment);

        assertThrows(IllegalStateException.class, () -> service.handlePayCallback(
                100L, 10L, "TRADE-CONFLICT", LocalDateTime.now()));

        verify(paymentService, never()).updatePaySuccess(anyLong(), any(), any());
        verify(couponService, never()).markUsed(anyLong(), anyLong());
    }

    @ParameterizedTest(name = "status={0}, payStatus={1}")
    @CsvSource({"2,1", "3,1", "4,1", "6,1", "6,2"})
    @DisplayName("订单仍承认原支付事实时一致重复通知零写入成功")
    void consistentDuplicateAcceptsLegitimateLaterOrderStates(int status, int payStatus) {
        resetLockedRecords(order(status, payStatus), payment(2, "TRADE-100"));

        assertDoesNotThrow(() -> service.handlePayCallback(
                100L, 10L, "TRADE-100", LocalDateTime.now()));

        verify(paymentService, never()).updatePaySuccess(anyLong(), any(), any());
        verify(orderMapper, never()).markPaid(anyLong(), any());
        verify(couponService, never()).markUsed(anyLong(), anyLong());
    }

    @ParameterizedTest(name = "status={0}, payStatus={1}")
    @CsvSource({"1,0", "5,0", "2,0", "5,1"})
    @DisplayName("订单不承认原支付事实时拒绝重复通知")
    void inconsistentDuplicateRejectsContradictoryOrderStates(int status, int payStatus) {
        resetLockedRecords(order(status, payStatus), payment(2, "TRADE-100"));

        assertThrows(IllegalStateException.class, () -> service.handlePayCallback(
                100L, 10L, "TRADE-100", LocalDateTime.now()));

        verify(paymentService, never()).updatePaySuccess(anyLong(), any(), any());
        verify(couponService, never()).markUsed(anyLong(), anyLong());
    }

    private void resetLockedRecords(Orders order, Payment payment) {
        orderMapper = mock(OrderMapper.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("getById") || name.equals("getByIdForUpdate")) {
                return order;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        paymentService = mock(PaymentService.class, invocation -> {
            if (invocation.getMethod().getName().equals("getByIdForUpdate")) {
                return payment;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "paymentService", paymentService);
    }

    private static Orders order(int status, int payStatus) {
        Orders order = new Orders();
        order.setId(100L);
        order.setUserId(7L);
        order.setAmount(new BigDecimal("10.00"));
        order.setStatus(status);
        order.setPayStatus(payStatus);
        return order;
    }

    private static Payment payment(int status, String tradeNo) {
        Payment payment = new Payment();
        payment.setId(10L);
        payment.setOrderId(100L);
        payment.setOrderType(0);
        payment.setAmount(new BigDecimal("10.00"));
        payment.setStatus(status);
        payment.setTradeNo(tradeNo);
        return payment;
    }
}
