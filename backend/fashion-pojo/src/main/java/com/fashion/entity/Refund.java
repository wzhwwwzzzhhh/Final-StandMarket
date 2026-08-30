package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款/售后
 */
@Data
public class Refund implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_WAITING_EXTERNAL_REFUND = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_REJECTED = 3;

    private Long id;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 订单详情id（部分退款，预留）
     */
    private Long orderDetailId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 退款原因
     */
    private String reason;

    /**
     * 退款金额
     */
    private BigDecimal amount;

    /**
     * 状态 0待审核 1已同意/待外部退款 2退款完成 3已拒绝
     */
    private Integer status;

    /**
     * 申请退款时的订单状态（拒绝时恢复用）
     */
    private Integer orderStatus;

    /**
     * 审核意见
     */
    private String auditOpinion;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    /**
     * 退款完成时间
     */
    private LocalDateTime refundTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
