package com.fashion.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板（管理端创建，用户领取后生成 user_coupon 持有券）
 */
@Data
public class CouponTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 券名称（如：满100减20）
     */
    private String name;

    /**
     * 类型 1满减 2折扣 3现金
     */
    private Integer type;

    /**
     * 使用门槛（满X元可用，0=无门槛）
     */
    private BigDecimal threshold;

    /**
     * 满减金额/现金金额，或折扣值(如8.5=85折)
     */
    private BigDecimal discount;

    /**
     * 发行总量 0=不限量
     */
    private Integer totalCount;

    /**
     * 每人限领
     */
    private Integer perUserLimit;

    /**
     * 有效期类型 1固定时间 2领取后N天
     */
    private Integer validType;

    /**
     * 领取后有效天数(valid_type=2)
     */
    private Integer validDays;

    /**
     * 有效开始(valid_type=1)
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 有效结束(valid_type=1)
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

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
     * 状态 0停用 1启用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}