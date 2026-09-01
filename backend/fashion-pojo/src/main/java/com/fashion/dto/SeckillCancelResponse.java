package com.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SeckillCancelResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String CANCELLED = "CANCELLED";
    public static final String REDIS_RECONCILIATION_PENDING = "REDIS_RECONCILIATION_PENDING";

    private String orderNumber;
    private Integer orderStatus;
    private String outcome;
    private String message;

    public static SeckillCancelResponse cancelled(String orderNumber) {
        return new SeckillCancelResponse(orderNumber, 3, CANCELLED, "取消订单成功");
    }

    public static SeckillCancelResponse redisReconciliationPending(String orderNumber) {
        return new SeckillCancelResponse(orderNumber, 3, REDIS_RECONCILIATION_PENDING,
                "订单已取消，库存恢复待处理");
    }
}
