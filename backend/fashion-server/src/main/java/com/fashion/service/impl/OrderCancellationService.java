package com.fashion.service.impl;

import com.fashion.entity.OrderDetail;
import com.fashion.entity.Orders;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 普通订单取消的独立事务边界。
 */
@Service
public class OrderCancellationService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CouponService couponService;

    @Transactional
    public boolean cancelForUser(Long orderId, Long userId) {
        Orders order = lockPendingOrder(orderId);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new IllegalStateException("订单不存在或无权操作");
        }
        cancelLockedOrder(order);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelTimeout(Long orderId) {
        Orders order = orderMapper.getByIdForUpdate(orderId);
        if (!isPendingOrdinaryOrder(order)) {
            return false;
        }
        cancelLockedOrder(order);
        return true;
    }

    private Orders lockPendingOrder(Long orderId) {
        Orders order = orderMapper.getByIdForUpdate(orderId);
        if (!isPendingOrdinaryOrder(order)) {
            throw new IllegalStateException("订单状态已变化，无法取消");
        }
        return order;
    }

    private boolean isPendingOrdinaryOrder(Orders order) {
        return order != null
                && Objects.equals(order.getStatus(), 1)
                && Objects.equals(order.getPayStatus(), 0)
                && !Objects.equals(order.getIsSeckill(), 1);
    }

    private void cancelLockedOrder(Orders order) {
        if (orderMapper.cancelPending(order.getId(), order.getStockDeducted(), LocalDateTime.now()) != 1) {
            throw new IllegalStateException("订单状态已变化，无法取消");
        }
        if (Objects.equals(order.getStockDeducted(), 1)) {
            restoreInventory(order.getId());
        }
        if (order.getUserCouponId() != null) {
            couponService.release(order.getUserId(), order.getId());
        }
    }

    private void restoreInventory(Long orderId) {
        List<OrderDetail> details = orderDetailMapper.listByOrderId(orderId);
        if (details == null || details.isEmpty()) {
            throw new IllegalStateException("已扣库存订单缺少订单明细");
        }
        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (OrderDetail detail : details) {
            if (detail.getProductId() == null || detail.getNumber() == null || detail.getNumber() <= 0) {
                throw new IllegalStateException("订单明细库存数量无效");
            }
            quantityByProductId.merge(detail.getProductId(), detail.getNumber(), Math::addExact);
        }
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            if (productMapper.restoreStock(entry.getKey(), entry.getValue()) != 1) {
                throw new IllegalStateException("商品库存回补失败，商品ID：" + entry.getKey());
            }
        }
    }
}
