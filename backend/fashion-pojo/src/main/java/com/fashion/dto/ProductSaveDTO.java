package com.fashion.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品保存参数DTO
 */
@Data
public class ProductSaveDTO {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Long categoryId;
    private String tag;
    private Integer status;
}
