package com.fashion.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持有券（领取到卡包，下单锁定，支付核销/取消释放）
 */
@Data
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 模板id
     */
    private Long templateId;

    /**
     * 状态 0未使用 1已使用 2已过期 3已锁定(下单核销中)
     */
    private Integer status;

    /**
     * 领取时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime obtainTime;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    /**
     * 核销订单id
     */
    private Long useOrderId;

    /**
     * 核销时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime useTime;

    // ===== 以下为关联展示字段（联表查询填充，非表字段） =====

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型 1满减 2折扣 3现金
     */
    private Integer templateType;

    /**
     * 使用门槛
     */
    private BigDecimal threshold;

    /**
     * 抵扣值
     */
    private BigDecimal discount;

    /**
     * 适用范围 0全店 1指定分类 2指定商品
     */
    private Integer scopeType;

    /**
     * 指定分类id(scope_type=1)
     */
    private Long applyCategoryId;

    /**
     * 指定商品id逗号分隔(scope_type=2)
     */
    private String applyProductIds;

    /**
     * 用户昵称（管理端查询用）
     */
    private String userName;

    /**
     * 用户手机号（管理端查询用）
     */
    private String userPhone;
}