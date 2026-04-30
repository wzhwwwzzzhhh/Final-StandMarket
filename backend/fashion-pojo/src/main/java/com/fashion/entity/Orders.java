package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单
 */
@Data
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 订单号
     */
    private String number;

    /**
     * 订单状态 1待付款 2待发货 3已发货 4已完成 5已取消 6退款
     */
    private Integer status;

    /**
     * 下单用户
     */
    private Long userId;

    /**
     * 地址id
     */
    private Long addressBookId;

    /**
     * 下单时间
     */
    private LocalDateTime orderTime;

    /**
     * 结账时间
     */
    private LocalDateTime checkoutTime;

    /**
     * 支付方式 1微信,2支付宝
     */
    private Integer payMethod;

    /**
     * 支付状态 0未支付 1已支付 2退款
     */
    private Integer payStatus;

    /**
     * 实收金额
     */
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 地址
     */
    private String address;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 收货人
     */
    private String consignee;

    /**
     * 订单取消原因
     */
    private String cancelReason;

    /**
     * 订单拒绝原因
     */
    private String rejectionReason;

    /**
     * 订单取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 预计送达时间
     */
    private LocalDateTime estimatedDeliveryTime;

    /**
     * 配送状态  1立即送出  0选择具体时间
     */
    private Integer deliveryStatus;

    /**
     * 送达时间
     */
    private LocalDateTime deliveryTime;

    /**
     * 运费
     */
    private BigDecimal shippingFee;

    /**
     * 订单明细
     */
    private List<OrderDetail> items;

    /**
     * 商品ID列表
     */
    private List<Long> productIds;
}
