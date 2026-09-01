package com.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeckillCancelCommand {
    private String orderNumber;
    private Long userId;
    private Long couponId;
}
