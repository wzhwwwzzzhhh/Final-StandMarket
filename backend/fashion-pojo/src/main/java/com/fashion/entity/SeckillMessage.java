package com.fashion.entity;

import lombok.Data;

@Data
public class SeckillMessage {
    private Long userId;
    private Long couponId;
    private String orderNumber;
    private String createTime;
}
