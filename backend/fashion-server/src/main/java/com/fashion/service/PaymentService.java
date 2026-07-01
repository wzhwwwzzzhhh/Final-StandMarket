package com.fashion.service;

import com.fashion.entity.Payment;

public interface PaymentService {

    /**
     * 创建支付记录
     */
    Payment createPayment(Long orderId, Integer orderType, java.math.BigDecimal amount, Integer payMethod);

    /**
     * 处理支付（模拟支付网关）
     * @return true-支付成功 false-支付失败
     */
    boolean processPayment(String payNo);

    /**
     * 查询支付状态
     */
    Payment getPaymentStatus(String payNo);

    /**
     * 根据订单ID查询支付记录
     */
    Payment getByOrderId(Long orderId);
}
