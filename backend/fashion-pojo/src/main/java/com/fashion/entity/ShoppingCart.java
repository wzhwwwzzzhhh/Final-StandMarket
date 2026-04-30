package com.fashion.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 购物车实体类
 */
@Data
public class ShoppingCart implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 主键
    private Long id;
    
    // 商品名称
    private String name;
    
    // 商品图片
    private String image;
    
    // 用户id
    private Long userId;
    
    // 商品id
    private Long productId;
    
    // 组合商品id
    private Long combinationId;
    
    // SKU信息
    private String skuInfo;
    
    // 数量
    private Integer number;
    
    // 金额
    private BigDecimal amount;
    
    // 创建时间
    private LocalDateTime createTime;
}