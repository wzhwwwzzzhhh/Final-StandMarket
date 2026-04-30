package com.fashion.dto;

import lombok.Data;

/**
 * 购物车操作参数DTO
 */
@Data
public class ShoppingCartDTO {
    private Long productId;
    private Integer number;
    private String skuInfo;
}
