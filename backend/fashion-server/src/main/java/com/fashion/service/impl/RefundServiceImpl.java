package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.entity.Refund;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.RefundMapper;
import com.fashion.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Transactional
    @Override
    public Refund apply(Long orderId, String reason) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        // 校验订单存在性与归属
        Orders order = orderMapper.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }

        // 仅已发货(3)和已完成(4)可申请退款
        if (order.getStatus() != 3 && order.getStatus() != 4) {
            throw new RuntimeException("当前订单状态不可申请退款");
        }

        // 校验是否已有待处理的退款申请（通过 order_id 精确查询，防并发重复提交）
        List<Refund> pending = refundMapper.listByOrderIdAndStatus(orderId, 0);
        if (!pending.isEmpty()) {
            throw new RuntimeException("该订单已有退款申请正在处理中");
        }

        // 生成退款单号
        String refundNo = "RF" + System.currentTimeMillis();

        // 创建退款记录
        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setRefundNo(refundNo);
        refund.setReason(reason);
        refund.setAmount(order.getAmount());
        refund.setStatus(Refund.STATUS_PENDING);
        refund.setOrderStatus(order.getStatus()); // 保存当前订单状态，拒绝时恢复用
        refund.setCreateTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        if (refundMapper.insert(refund) != 1) {
            throw new IllegalStateException("退款申请创建失败");
        }

        if (orderMapper.markRefunding(orderId, userId, order.getStatus()) != 1) {
            throw new IllegalStateException("订单状态已变化，退款申请失败");
        }

        log.info("退款申请已提交 orderId={}, refundNo={}, userId={}", orderId, refundNo, userId);
        return refund;
    }

    @Override
    public List<Refund> listUserRefunds() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return refundMapper.listByUserId(userId);
    }

    @Override
    public List<Refund> listAllRefunds(Integer status) {
        return refundMapper.listAll(status);
    }

    @Transactional
    @Override
    public void approve(Long id, String opinion) {
        LocalDateTime now = LocalDateTime.now();
        if (refundMapper.approvePending(id, opinion, now, now) != 1) {
            throw new RuntimeException("退款记录不存在或已处理");
        }
        log.info("退款审核已同意，等待外部退款处理 refundId={}", id);
    }

    @Transactional
    @Override
    public void reject(Long id, String opinion) {
        Refund refund = refundMapper.getById(id);
        if (refund == null) {
            throw new RuntimeException("退款记录不存在");
        }
        if (refund.getStatus() == null || refund.getStatus() != Refund.STATUS_PENDING) {
            throw new RuntimeException("该退款申请已处理，不可重复操作");
        }
        if (refund.getOrderStatus() == null
                || (refund.getOrderStatus() != 3 && refund.getOrderStatus() != 4)) {
            throw new RuntimeException("退款申请前订单状态不可恢复");
        }

        LocalDateTime now = LocalDateTime.now();
        if (refundMapper.rejectPending(id, opinion, now, now) != 1) {
            throw new RuntimeException("退款记录不存在或已处理");
        }
        if (orderMapper.restoreRejectedRefundOrder(refund.getOrderId(), refund.getOrderStatus()) != 1) {
            throw new IllegalStateException("订单状态已变化，拒绝退款失败");
        }
        log.info("退款已拒绝 refundId={}, refundNo={}", id, refund.getRefundNo());
    }
}
