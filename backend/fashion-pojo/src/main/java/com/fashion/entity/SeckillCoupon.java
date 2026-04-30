package com.fashion.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀券实体类
 */
@Data
public class SeckillCoupon {
    private Long id;
    private String name;
    // 原价
    private Double originalPrice;
    // 秒杀价
    private Double seckillPrice;
    private Integer stock;
    // 每人限购数量
    private Integer limitPerUser;
    // 状态：0-待开始，1-进行中，2-已结束，3-已售罄
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}