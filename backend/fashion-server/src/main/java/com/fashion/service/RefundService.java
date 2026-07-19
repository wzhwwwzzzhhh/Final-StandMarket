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
     * 同意退款（恢复库存 + 更新订单状态）
     */
    void approve(Long id, String opinion);

    /**
     * 拒绝退款
     */
    void reject(Long id, String opinion);
}
