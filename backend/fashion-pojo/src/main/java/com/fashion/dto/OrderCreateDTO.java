package com.fashion.dto;

import com.fashion.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单创建参数DTO
 */
@Data
public class OrderCreateDTO {
    private List<Long> productIds;
    private Long addressId;
    private Integer payMethod;
    private Long activityId;
    private Long couponId;
    private Integer deliveryStatus;
    private LocalDateTime estimatedDeliveryTime;
    private BigDecimal amount;

}
