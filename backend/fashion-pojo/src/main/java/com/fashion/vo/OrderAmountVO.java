package com.fashion.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderAmountVO {

    private BigDecimal totalAmount;

    private BigDecimal activityDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal finalAmount;

    private String activityName;

    private String activityDiscountText;

    private String couponName;

    private String couponDiscountText;
}
