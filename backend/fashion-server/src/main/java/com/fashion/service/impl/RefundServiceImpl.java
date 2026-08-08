package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.OrderDetail;
import com.fashion.entity.Orders;
import com.fashion.entity.Refund;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
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

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ProductMapper productMapper;

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
        refund.setStatus(0); // 待审核
        refund.setOrderStatus(order.getStatus()); // 保存当前订单状态，拒绝时恢复用
        refund.setCreateTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        refundMapper.insert(refund);

        // 更新订单状态为 6（退款中）
        order.setStatus(6);
        orderMapper.update(order);

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
        Refund refund = refundMapper.getById(id);
        if (refund == null) {
            throw new RuntimeException("退款记录不存在");
        }
        if (refund.getStatus() != 0) {
            throw new RuntimeException("该退款申请已处理，不可重复操作");
        }

        // 更新退款记录为已退款
        refund.setStatus(2); // 已退款
        refund.setAuditOpinion(opinion);
        refund.setAuditTime(LocalDateTime.now());
        refund.setRefundTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        refundMapper.update(refund);

        // 恢复库存（product 级别，按 order_detail 中的 product_id 和 number）
        List<OrderDetail> details = orderDetailMapper.listByOrderId(refund.getOrderId());
        for (OrderDetail detail : details) {
            if (detail.getProductId() != null && detail.getNumber() != null) {
                productMapper.restoreStock(detail.getProductId(), detail.getNumber());
                log.info("恢复库存 productId={}, delta={}", detail.getProductId(), detail.getNumber());
            }
        }

        // 订单保持 status=6（退款状态）
        // TODO: MVP 阶段仅恢复库存，未调用支付网关退款（如支付宝 alipay.trade.refund）
        log.warn("退款已同意，但未触发支付网关退款（MVP 限制），refundId={}, refundNo={}", id, refund.getRefundNo());
    }

    @Transactional
    @Override
    public void reject(Long id, String opinion) {
        Refund refund = refundMapper.getById(id);
        if (refund == null) {
            throw new RuntimeException("退款记录不存在");
        }
        if (refund.getStatus() != 0) {
            throw new RuntimeException("该退款申请已处理，不可重复操作");
        }

        // 更新退款记录为拒绝
        refund.setStatus(3); // 拒绝
        refund.setAuditOpinion(opinion);
        refund.setAuditTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        refundMapper.update(refund);

        // 恢复订单到申请退款前的状态（用 refund.order_status）
        if (refund.getOrderStatus() != null) {
            Orders order = orderMapper.getById(refund.getOrderId());
            if (order != null) {
                order.setStatus(refund.getOrderStatus());
                orderMapper.update(order);
                log.info("退款拒绝，订单状态恢复到 status={}", refund.getOrderStatus());
            }
        } else {
            log.warn("退款拒绝时 order_status 为空，订单 status=6 无法恢复，orderId={}, refundId={}",
                     refund.getOrderId(), refund.getId());
        }

        log.info("退款已拒绝 refundId={}, refundNo={}, opinion={}", id, refund.getRefundNo(), opinion);
    }
}
