package com.fashion.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀提交响应DTO
 * 用于异步秒杀场景下返回简化信息
 */
@Data
public class SeckillSubmitResult {
    
    /**
     * 订单号（用于轮询）
     */
    private String orderNumber;
    
    /**
     * 处理状态
     * 0: 处理中
     * 1: 成功
     * 2: 失败
     */
    private Integer status;
    
    /**
     * 状态信息
     */
    private String message;
    
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    
    /**
     * 秒杀券ID
     */
    private Long couponId;
    
    /**
     * 默认构造方法
     */
    public SeckillSubmitResult() {
        this.submitTime = LocalDateTime.now();
    }
    
    /**
     * 带参构造方法
     */
    public SeckillSubmitResult(String orderNumber, Integer status, String message, Long couponId) {
        this.orderNumber = orderNumber;
        this.status = status;
        this.message = message;
        this.couponId = couponId;
        this.submitTime = LocalDateTime.now();
    }
}