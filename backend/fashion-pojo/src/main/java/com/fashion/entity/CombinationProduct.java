package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CombinationProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long combinationId;
    private Long productId;
    private String name;
    private BigDecimal price;
    private Integer copies;
}
