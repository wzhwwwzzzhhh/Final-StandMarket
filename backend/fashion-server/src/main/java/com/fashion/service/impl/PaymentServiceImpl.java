package com.fashion.service.impl;

import com.fashion.entity.Payment;
import com.fashion.mapper.PaymentMapper;
import com.fashion.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    private final Random random = new Random();

    @Override
    @Transactional
    public Payment createPayment(Long orderId, Integer orderType, BigDecimal amount, Integer payMethod) {
        String payNo = "PAY" + System.currentTimeMillis() + String.format("%04d", random.nextInt(10000));

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setOrderType(orderType);
        payment.setPayNo(payNo);
        payment.setAmount(amount);
        payment.setPayMethod(payMethod);
        payment.setStatus(0); // 待支付
        payment.setCreateTime(LocalDateTime.now());

        paymentMapper.insert(payment);
        log.info("创建支付记录成功，支付流水号：{}，金额：{}", payNo, amount);
        return payment;
    }

    @Override
    @Transactional
    public boolean processPayment(String payNo) {
        Payment payment = paymentMapper.getByPayNo(payNo);
        if (payment == null) {
            log.warn("支付记录不存在，流水号：{}", payNo);
            return false;
        }
        if (payment.getStatus() != 0) {
            log.warn("支付记录状态异常，流水号：{}，当前状态：{}", payNo, payment.getStatus());
            return false;
        }

        // 更新为支付中
        paymentMapper.updateStatus(payment.getId(), 1);

        // 模拟支付网关处理延迟
        try {
            Thread.sleep(1500 + random.nextInt(1500)); // 1.5~3秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 95% 成功率模拟
        boolean success = random.nextInt(100) < 95;
        if (success) {
            paymentMapper.updateStatus(payment.getId(), 2);
            paymentMapper.updatePayTime(payment.getId(), LocalDateTime.now());
            log.info("支付成功，流水号：{}", payNo);
        } else {
            paymentMapper.updateStatus(payment.getId(), 3);
            log.warn("支付失败，流水号：{}", payNo);
        }

        return success;
    }

    @Override
    public Payment getPaymentStatus(String payNo) {
        return paymentMapper.getByPayNo(payNo);
    }

    @Override
    public Payment getByOrderId(Long orderId) {
        return paymentMapper.getByOrderId(orderId);
    }
}
