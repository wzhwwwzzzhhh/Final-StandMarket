package com.fashion.service;

import com.fashion.entity.Refund;

import java.util.List;

/**
 * 退款Service
 */
public interface RefundService {

    /**
     * 申请退款
     */
    Refund apply(Long orderId, String reason);

    /**
     * 获取用户的退款记录
     */
    List<Refund> listUserRefunds();

    /**
     * 管理端获取所有退款记录
     */
    List<Refund> listAllRefunds(Integer status);

    /**
     * 审核同意（仅 0 -> 1，等待外部退款处理，不修改订单、支付或库存）
     */
    void approve(Long id, String opinion);

    /**
     * 拒绝退款
     */
    void reject(Long id, String opinion);
}
