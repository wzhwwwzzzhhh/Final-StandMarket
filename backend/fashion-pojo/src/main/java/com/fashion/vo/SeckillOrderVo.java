package com.fashion.vo;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class SeckillOrderVo {
    private Long id;
    private Long userId;
    private Long couponId;
    private String orderNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;

    private String userName;
    private String userPhone;

    private String couponName;
    // 原价
    private Double originalPrice;
    // 秒杀价
    private Double seckillPrice;
}
