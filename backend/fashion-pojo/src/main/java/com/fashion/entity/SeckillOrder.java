package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder implements Serializable {
    /**
     * 秒杀订单
     */
    // 序列化ID
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long couponId;
    private String orderNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    
    // 管理端查询所需字段
    private String couponName;      // 秒杀券名称
    private BigDecimal seckillPrice; // 秒杀价格
    private String activityName;    // 活动名称
}
