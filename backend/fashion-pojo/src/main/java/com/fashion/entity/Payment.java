package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录
 */
@Data
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单类型 0-普通订单 1-秒杀订单
     */
    private Integer orderType;

    /**
     * 支付流水号
     */
    private String payNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付方式 1微信 2支付宝
     */
    private Integer payMethod;

    /**
     * 支付状态 0待支付 1支付中 2成功 3失败
     */
    private Integer status;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
