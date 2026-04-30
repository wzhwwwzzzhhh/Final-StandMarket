package com.fashion.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券DTO
 */
@Data
public class UserCouponDto {
    
    /**
     * 秒杀订单ID
     */
    private Long id;
    
    /**
     * 秒杀券ID
     */
    private Long couponId;
    
    /**
     * 秒杀券名称
     */
    private String couponName;
    
    /**
     * 原价
     */
    private BigDecimal originalPrice;
    
    /**
     * 秒杀价
     */
    private BigDecimal seckillPrice;
    
    /**
     * 秒杀券开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 秒杀券结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 订单号
     */
    private String orderNumber;
    
    /**
     * 秒杀订单状态（1:待支付 2:已支付 3:已取消）
     */
    private Integer status;
    
    /**
     * 创建时间（购买时间）
     */
    private LocalDateTime createTime;
    
    /**
     * 支付时间
     */
    private LocalDateTime payTime;
    
    /**
     * 优惠券状态（计算字段）
     * 1:可用 2:已使用 3:已过期
     */
    private Integer couponStatus;
    
    /**
     * 优惠券状态文本
     */
    private String couponStatusText;
}