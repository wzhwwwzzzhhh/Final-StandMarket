package com.fashion.service.impl;

import com.fashion.dto.SeckillCancelCommand;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.seckill.SeckillCompensationService;
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
    @Autowired
    private SeckillCompensationService seckillCompensationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelForUser(String orderNumber, Long userId) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNumberAndUserId(orderNumber, userId);
        if (order == null
                || seckillOrderMapper.cancelPendingByOrderNumberAndUserId(orderNumber, userId) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        persistCancellationEvidence(order);
        return command(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelTrusted(String orderNumber) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
        if (order == null || seckillOrderMapper.cancelPending(orderNumber) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        persistCancellationEvidence(order);
        return command(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeckillCancelCommand cancelTimeout(Long orderId) {
        SeckillOrder order = seckillOrderMapper.selectById(orderId);
        if (order == null || seckillOrderMapper.cancelPending(order.getOrderNumber()) != 1) {
            return null;
        }
        restoreStockOrThrow(order.getCouponId());
        persistCancellationEvidence(order);
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

    private void persistCancellationEvidence(SeckillOrder order) {
        seckillCompensationService.requestRelease(order.getOrderNumber(), order.getUserId(), order.getCouponId(),
                "CANCEL_COMMITTED", SeckillCompensationService.EVIDENCE_CANCEL_COMMITTED);
    }
}
