package com.fashion.service.impl;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillCancellationTransaction {

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillCouponMapper seckillCouponMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelForUser(String orderNumber, Long userId) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNumberAndUserId(orderNumber, userId);
        if (order == null
                || seckillOrderMapper.cancelPendingByOrderNumberAndUserId(orderNumber, userId) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        return command(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelTrusted(String orderNumber) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
        if (order == null || seckillOrderMapper.cancelPending(orderNumber) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        return command(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelTimeout(Long orderId) {
        SeckillOrder order = seckillOrderMapper.selectById(orderId);
        if (order == null || seckillOrderMapper.cancelPending(order.getOrderNumber()) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        return command(order);
    }

    private void restoreStockOrThrow(Long couponId) {
        if (couponId == null || seckillCouponMapper.restoreStock(couponId) != 1) {
            throw new IllegalStateException("秒杀券库存恢复失败");
        }
    }

    private SeckillCancelCommand command(SeckillOrder order) {
        return new SeckillCancelCommand(order.getOrderNumber(), order.getUserId(), order.getCouponId());
    }
}
