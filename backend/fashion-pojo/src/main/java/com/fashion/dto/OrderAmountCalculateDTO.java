package com.fashion.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderAmountCalculateDTO {

    private BigDecimal totalAmount;

    private Long activityId;

    private Long couponId;
}
