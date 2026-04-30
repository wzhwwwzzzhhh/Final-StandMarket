package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细
 */
@Data
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 名字
     */
    private String name;

    /**
     * 图片
     */
    private String image;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 商品id
     */
    private Long productId;

    /**
     * 组合商品id
     */
    private Long combinationId;

    /**
     * SKU信息
     */
    private String skuInfo;

    /**
     * 数量
     */
    private Integer number;

    /**
     * 金额
     */
    private BigDecimal amount;
}
