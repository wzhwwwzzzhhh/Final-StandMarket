package com.fashion.service;

import com.fashion.entity.Payment;

public interface PaymentService {

    /**
     * 创建支付记录
     */
    Payment createAlipayPayment(Long orderId);

    /**
     * 查询支付状态
     */
    Payment getPaymentStatus(String payNo);

    /**
     * 在当前事务中锁定支付记录，供支付状态迁移使用。
     */
    Payment getByIdForUpdate(Long id);

    /**
     * 根据订单ID和订单类型查询最新支付记录
     */
    Payment getByOrderId(Long orderId, Integer orderType);

    /**
     * 更新支付成功（条件更新，重复通知幂等）
     * @return true 表示本次完成状态迁移，false 表示记录已处理
     */
    boolean updatePaySuccess(Long id, String tradeNo, java.time.LocalDateTime payTime);
}
