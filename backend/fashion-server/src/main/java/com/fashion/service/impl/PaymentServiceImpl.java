package com.fashion.service.impl;

import com.fashion.entity.Payment;
import com.fashion.entity.Orders;
import com.fashion.context.BaseContext;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.PaymentMapper;
import com.fashion.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    @Transactional
    public Payment createAlipayPayment(Long orderId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        Orders order = orderMapper.getByIdForUpdate(orderId);
        validatePayableOrder(order, userId);

        Payment existing = paymentMapper.getActiveByOrderIdAndType(orderId, 0);
        if (existing != null) {
            validateReusablePayment(existing, order);
            return existing;
        }

        String payNo = "PAY" + UUID.randomUUID().toString().replace("-", "");

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setOrderType(0);
        payment.setPayNo(payNo);
        payment.setAmount(order.getAmount());
        payment.setPayMethod(2);
        payment.setStatus(0); // 待支付
        payment.setCreateTime(LocalDateTime.now());

        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException conflict) {
            Payment winner = paymentMapper.getActiveByOrderIdAndTypeForUpdate(orderId, 0);
            if (winner == null) {
                throw conflict;
            }
            validateReusablePayment(winner, order);
            return winner;
        }
        log.info("创建支付记录成功 payNo={}, amount={}", payNo, order.getAmount());
        return payment;
    }

    private void validatePayableOrder(Orders order, Long userId) {
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new IllegalStateException("订单不存在或无权操作");
        }
        if (order.getStatus() == null || order.getStatus() != 1
                || order.getPayStatus() == null || order.getPayStatus() != 0) {
            throw new IllegalStateException("订单状态不是待支付");
        }
        if (!Objects.equals(order.getStockDeducted(), 1)) {
            throw new IllegalStateException("订单库存尚未成功扣减");
        }
        if (order.getAmount() == null) {
            throw new IllegalStateException("订单金额无效");
        }
    }

    private void validateReusablePayment(Payment payment, Orders order) {
        if (payment.getOrderType() == null || payment.getOrderType() != 0
                || !Objects.equals(payment.getOrderId(), order.getId())
                || payment.getStatus() == null || (payment.getStatus() != 0 && payment.getStatus() != 1)
                || payment.getAmount() == null || payment.getAmount().compareTo(order.getAmount()) != 0
                || payment.getPayMethod() == null || payment.getPayMethod() != 2) {
            throw new IllegalStateException("活动支付记录与订单不一致");
        }
    }

    @Override
    public Payment getPaymentStatus(String payNo) {
        return paymentMapper.getByPayNo(payNo);
    }

    @Override
    public Payment getPaymentStatusForCurrentUser(String payNo) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        if (payNo == null || payNo.trim().isEmpty()) {
            return null;
        }
        return paymentMapper.getByPayNoAndUserId(payNo, userId);
    }

    @Override
    public Payment getByIdForUpdate(Long id) {
        return paymentMapper.getByIdForUpdate(id);
    }

    @Override
    public Payment getByOrderId(Long orderId, Integer orderType) {
        return paymentMapper.getByOrderIdAndType(orderId, orderType);
    }

    @Override
    @Transactional
    public boolean updatePaySuccess(Long id, String tradeNo, LocalDateTime payTime) {
        int rows = paymentMapper.markSuccess(id, tradeNo, payTime);
        if (rows > 0) {
            log.info("支付记录更新成功 id={}, tradeNo={}", id, tradeNo);
            return true;
        }
        log.info("支付记录已处理，忽略重复成功通知 id={}, tradeNo={}", id, tradeNo);
        return false;
    }
}
