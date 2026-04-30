package com.fashion.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 秒杀订单统计VO
 * 用于管理端展示秒杀订单的统计信息
 */
@Data
public class SeckillOrderStatisticsVO {
    
    /**
     * 秒杀订单总数
     */
    private Long totalOrders;
    
    /**
     * 待支付订单数
     */
    private Long pendingOrdersCount;
    
    /**
     * 已支付订单数
     */
    private Long paidOrdersCount;
    
    /**
     * 已取消订单数
     */
    private Long canceledOrdersCount;
    
    /**
     * 秒杀总销售额
     */
    private BigDecimal totalAmount;
    
    /**
     * 今日销售额
     */
    private BigDecimal todayAmount;
    
    /**
     * 平均响应时间（毫秒）
     */
    private Integer avgResponseTime;
    
    /**
     * 成功订单数
     */
    private Long successOrdersCount;
    
    /**
     * 失败订单数
     */
    private Long failedOrdersCount;
    
    /**
     * 订单成功率
     */
    private BigDecimal successRate;
    
    /**
     * 平均订单金额
     */
    private BigDecimal avgOrderAmount;
}