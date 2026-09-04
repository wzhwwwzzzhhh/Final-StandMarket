package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.dto.SeckillCancelResponse;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.entity.OrderDetail;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.entity.Refund;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.PaymentMapper;
import com.fashion.mapper.RefundMapper;
import com.fashion.mapper.ReviewMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.vo.ReviewMineVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B4 用户交易资源归属服务合约")
class UserResourceOwnershipContractTest {

    @AfterEach
    void tearDown() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("普通订单用户读取由服务端登录态绑定 id 和 user_id")
    void ordinaryOrderReadUsesCurrentUserPredicate() {
        BaseContext.setUserId(7L);
        OrderMapper mapper = mock(OrderMapper.class);
        OrderDetailMapper detailMapper = mock(OrderDetailMapper.class);
        Orders owned = order(100L, 7L);
        when(mapper.getByIdAndUserId(100L, 7L)).thenReturn(owned);
        when(detailMapper.listByOrderId(100L)).thenReturn(Collections.<OrderDetail>emptyList());
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", detailMapper);

        Orders result = invoke(service, "getCurrentUserOrderById", new Class<?>[]{Long.class}, 100L);

        assertSame(owned, result);
        verify(mapper).getByIdAndUserId(100L, 7L);
        verify(mapper, never()).getById(100L);
    }

    @Test
    @DisplayName("未登录不能调用用户订单专用读取")
    void anonymousOrderReadStopsBeforeMapper() {
        OrderMapper mapper = mock(OrderMapper.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", mock(OrderDetailMapper.class));

        assertThrows(IllegalStateException.class,
                () -> invoke(service, "getCurrentUserOrderById", new Class<?>[]{Long.class}, 100L));

        verify(mapper, never()).getByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("同步回跳支付流水由当前用户订单归属查询")
    void paymentReturnLookupUsesCurrentUserPredicate() {
        BaseContext.setUserId(7L);
        PaymentMapper mapper = mock(PaymentMapper.class);
        Payment owned = new Payment();
        owned.setPayNo("PAY-100");
        when(mapper.getByPayNoAndUserId("PAY-100", 7L)).thenReturn(owned);
        PaymentServiceImpl service = new PaymentServiceImpl();
        ReflectionTestUtils.setField(service, "paymentMapper", mapper);

        Payment result = invoke(service, "getPaymentStatusForCurrentUser",
                new Class<?>[]{String.class}, "PAY-100");

        assertSame(owned, result);
        verify(mapper).getByPayNoAndUserId("PAY-100", 7L);
        verify(mapper, never()).getByPayNo("PAY-100");
    }

    @Test
    @DisplayName("退款申请不先裸查任意订单")
    void refundApplicationUsesOwnerScopedOrderQuery() {
        BaseContext.setUserId(7L);
        OrderMapper orderMapper = mock(OrderMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        when(orderMapper.getByIdAndUserId(100L, 7L)).thenReturn(null);
        when(orderMapper.getById(100L)).thenReturn(refundableOrder());
        when(refundMapper.listByOrderIdAndStatus(100L, 0)).thenReturn(Collections.<Refund>emptyList());
        when(refundMapper.insert(any(Refund.class))).thenReturn(1);
        when(orderMapper.markRefunding(100L, 7L, 3)).thenReturn(1);
        RefundServiceImpl service = new RefundServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "refundMapper", refundMapper);

        assertThrows(RuntimeException.class, () -> service.apply(100L, "不合适"));

        verify(orderMapper).getByIdAndUserId(100L, 7L);
        verify(orderMapper, never()).getById(100L);
        verify(refundMapper, never()).insert(any());
    }

    @Test
    @DisplayName("评价服务只使用登录态身份并按订单商品检查")
    void reviewServiceUsesCurrentUser() {
        BaseContext.setUserId(7L);
        ReviewMapper mapper = mock(ReviewMapper.class);
        ReviewServiceImpl service = new ReviewServiceImpl();
        ReflectionTestUtils.setField(service, "reviewMapper", mapper);
        ReviewCreateDTO submitted = new ReviewCreateDTO();
        submitted.setOrderId(100L);
        submitted.setProductId(200L);
        submitted.setRating(5);
        ReviewMineVO saved = new ReviewMineVO();
        saved.setOrderId(100L);
        saved.setProductId(200L);
        when(mapper.insertAuthorized(7L, submitted)).thenReturn(1);
        when(mapper.selectMineByOrderProductUser(100L, 200L, 7L)).thenReturn(saved);
        when(mapper.existsByOrderProductUser(100L, 200L, 7L)).thenReturn(1);

        ReviewMineVO result = service.addReview(submitted);
        boolean reviewed = service.hasReviewed(100L, 200L);

        assertSame(saved, result);
        assertTrue(reviewed);
        verify(mapper).insertAuthorized(7L, submitted);
        verify(mapper).existsByOrderProductUser(100L, 200L, 7L);
    }

    @Test
    @DisplayName("秒杀用户详情与取消使用当前用户归属 SQL")
    void seckillUserOperationsUseCurrentUser() {
        BaseContext.setUserId(7L);
        SeckillOrderMapper mapper = mock(SeckillOrderMapper.class);
        SeckillOrder owned = new SeckillOrder();
        owned.setOrderNumber("SK-100");
        owned.setUserId(7L);
        when(mapper.selectByOrderNumberAndUserId("SK-100", 7L)).thenReturn(owned);
        SeckillCancellationTransaction cancellation = mock(SeckillCancellationTransaction.class);
        when(cancellation.cancelForUser("SK-100", 7L)).thenReturn(null);
        SeckillOrderServiceImpl service = new SeckillOrderServiceImpl();
        ReflectionTestUtils.setField(service, "seckillOrderMapper", mapper);
        ReflectionTestUtils.setField(service, "seckillCancellationTransaction", cancellation);

        SeckillOrder result = invoke(service, "getCurrentUserOrderByNumber",
                new Class<?>[]{String.class}, "SK-100");
        SeckillCancelResponse canceled = invoke(service, "cancelCurrentUserOrder",
                new Class<?>[]{String.class}, "SK-100");

        assertSame(owned, result);
        assertNull(canceled);
        verify(mapper).selectByOrderNumberAndUserId("SK-100", 7L);
        verify(cancellation).cancelForUser("SK-100", 7L);
        verify(mapper, never()).selectByOrderNumber("SK-100");
    }

    private static Orders order(Long id, Long userId) {
        Orders order = new Orders();
        order.setId(id);
        order.setUserId(userId);
        return order;
    }

    private static Orders refundableOrder() {
        Orders order = order(100L, 7L);
        order.setStatus(3);
        order.setAmount(new BigDecimal("88.00"));
        return order;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return (T) method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new AssertionError("method failed: " + methodName, cause);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("missing required method: " + methodName, e);
        }
    }
}
