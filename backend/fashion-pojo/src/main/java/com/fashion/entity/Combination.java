package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Combination implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long categoryId;
    private String name;
    private BigDecimal price;
    private Integer status;
    private String description;
    private String image;
    private Integer stock;
    private Integer sales;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
