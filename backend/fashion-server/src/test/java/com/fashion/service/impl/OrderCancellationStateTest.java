package com.fashion.service.impl;

import com.fashion.entity.OrderDetail;
import com.fashion.entity.Orders;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B2 订单取消库存闭环")
class OrderCancellationStateTest {

    private OrderCancellationService service;
    private OrderMapper orderMapper;
    private OrderDetailMapper orderDetailMapper;
    private ProductMapper productMapper;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderDetailMapper = mock(OrderDetailMapper.class);
        productMapper = mock(ProductMapper.class);
        couponService = mock(CouponService.class);
        service = new OrderCancellationService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", orderDetailMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "couponService", couponService);
    }

    @Test
    @DisplayName("已扣库存订单按商品聚合后只回补一次")
    void restoresDeductedInventoryOnceAfterWinningCancelCas() {
        Orders order = pendingOrder(1, null);
        when(orderMapper.getByIdForUpdate(100L)).thenReturn(order);
        when(orderMapper.cancelPending(any(), any(), any())).thenReturn(1);
        when(orderDetailMapper.listByOrderId(100L)).thenReturn(Arrays.asList(detail(9L, 2), detail(9L, 3)));
        when(productMapper.restoreStock(9L, 5)).thenReturn(1);

        assertTrue(service.cancelForUser(100L, 7L));

        verify(productMapper).restoreStock(9L, 5);
        verify(productMapper, never()).restoreStock(9L, 2);
        verify(productMapper, never()).restoreStock(9L, 3);
        verify(couponService, never()).release(any(), any());
    }

    @Test
    @DisplayName("历史未扣库存订单可取消但不能回补库存")
    void historicalOrderCancelsWithoutRestoringInventory() {
        Orders order = pendingOrder(0, null);
        when(orderMapper.getByIdForUpdate(100L)).thenReturn(order);
        when(orderMapper.cancelPending(any(), any(), any())).thenReturn(1);

        assertTrue(service.cancelForUser(100L, 7L));

        verify(productMapper, never()).restoreStock(any(), any());
        verify(orderDetailMapper, never()).listByOrderId(any());
    }

    @Test
    @DisplayName("有券订单取消赢家必须严格释放绑定券")
    void releasesCouponOnlyForCouponOrder() {
        Orders order = pendingOrder(0, 55L);
        when(orderMapper.getByIdForUpdate(100L)).thenReturn(order);
        when(orderMapper.cancelPending(any(), any(), any())).thenReturn(1);

        assertTrue(service.cancelForUser(100L, 7L));

        verify(couponService).release(7L, 100L);
    }

    private Orders pendingOrder(Integer stockDeducted, Long userCouponId) {
        Orders order = new Orders();
        order.setId(100L);
        order.setUserId(7L);
        order.setStatus(1);
        order.setPayStatus(0);
        order.setIsSeckill(0);
        order.setStockDeducted(stockDeducted);
        order.setUserCouponId(userCouponId);
        return order;
    }

    private OrderDetail detail(Long productId, Integer quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setProductId(productId);
        detail.setNumber(quantity);
        return detail;
    }
}
