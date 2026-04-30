package com.fashion.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSku implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String skuName;
    private String skuValue;
    private BigDecimal price;
    private Integer stock;
}
