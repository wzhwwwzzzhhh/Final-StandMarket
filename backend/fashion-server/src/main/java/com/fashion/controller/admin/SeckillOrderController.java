package com.fashion.controller.admin;

import com.fashion.entity.PageResult;
import com.fashion.entity.SeckillOrder;
import com.fashion.result.Result;
import com.fashion.service.SeckillOrderService;
import com.fashion.vo.SeckillOrderStatisticsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端秒杀订单控制器
 * 负责秒杀订单的管理和统计功能
 */
@RestController
@RequestMapping("/admin/seckill/order")
@Slf4j
public class SeckillOrderController {

    @Autowired
    private SeckillOrderService seckillOrderService;

    /**
     * 分页查询秒杀订单列表
     */
    @GetMapping("/page")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        
        log.info("分页查询秒杀订单列表，页码：{}，页大小：{}，搜索条件：{}", page, pageSize, search);
        
        try {
            PageResult pageResult = seckillOrderService.getSeckillOrderPage(page, pageSize, search, status, activityId, startTime, endTime);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("分页查询秒杀订单列表失败", e);
            return Result.error("查询失败");
        }
    }

    /**
     * 根据订单号查询秒杀订单详情
     */
    @GetMapping("/{orderNumber}")
    public Result<SeckillOrder> detail(@PathVariable String orderNumber) {
        log.info("查询秒杀订单详情，订单号：{}", orderNumber);
        
        try {
            SeckillOrder order = seckillOrderService.getOrderByNumber(orderNumber);
            if (order == null) {
                return Result.error("订单不存在");
            }
            return Result.success(order);
        } catch (Exception e) {
            log.error("查询秒杀订单详情失败，订单号：{}", orderNumber, e);
            return Result.error("查询失败");
        }
    }

    /**
     * 确认订单支付
     */
    @PostMapping("/{orderNumber}/confirm-payment")
    public Result<?> confirmPayment(@PathVariable String orderNumber) {
        log.info("确认秒杀订单支付，订单号：{}", orderNumber);
        
        try {
            boolean success = seckillOrderService.confirmPayment(orderNumber);
            if (success) {
                return Result.success("确认支付成功");
            } else {
                return Result.error("确认支付失败");
            }
        } catch (Exception e) {
            log.error("确认秒杀订单支付失败，订单号：{}", orderNumber, e);
            return Result.error("确认支付失败");
        }
    }

    /**
     * 取消秒杀订单
     */
    @PostMapping("/{orderNumber}/cancel")
    public Result<?> cancelOrder(@PathVariable String orderNumber) {
        log.info("取消秒杀订单，订单号：{}", orderNumber);
        
        try {
            boolean success = seckillOrderService.cancelOrder(orderNumber);
            if (success) {
                return Result.success("取消订单成功");
            } else {
                return Result.error("取消订单失败");
            }
        } catch (Exception e) {
            log.error("取消秒杀订单失败，订单号：{}", orderNumber, e);
            return Result.error("取消订单失败");
        }
    }

    /**
     * 删除秒杀订单
     */
    @DeleteMapping
    public Result<?> deleteOrder(@RequestParam Long id) {
        log.info("删除秒杀订单，订单ID：{}", id);
        
        try {
            boolean success = seckillOrderService.deleteOrder(id);
            if (success) {
                return Result.success("删除订单成功");
            } else {
                return Result.error("删除订单失败");
            }
        } catch (Exception e) {
            log.error("删除秒杀订单失败，订单ID：{}", id, e);
            return Result.error("删除订单失败");
        }
    }

    /**
     * 获取秒杀订单统计信息
     */
    @GetMapping("/statistics")
    public Result<SeckillOrderStatisticsVO> getStatistics() {
        log.info("获取秒杀订单统计信息");
        
        try {
            SeckillOrderStatisticsVO statistics = seckillOrderService.getSeckillOrderStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取秒杀订单统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }

    /**
     * 获取秒杀订单基础统计（简化版）
     */
    @GetMapping("/basic-statistics")
    public Result<Map<String, Object>> getBasicStatistics() {
        log.info("获取秒杀订单基础统计信息");
        
        try {
            Map<String, Object> statistics = new HashMap<>();
            
            // 获取总订单数
            Long totalOrders = seckillOrderService.getTotalSeckillOrderCount();
            statistics.put("totalOrders", totalOrders);
            
            // 获取待支付订单数
            Long pendingOrders = seckillOrderService.getPendingOrderCount();
            statistics.put("pendingOrders", pendingOrders);
            
            // 获取总销售额
            Double totalAmount = seckillOrderService.getTotalSalesAmount();
            statistics.put("totalAmount", totalAmount);
            
            // 获取平均响应时间（模拟数据）
            statistics.put("avgResponseTime", 156);
            
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取秒杀订单基础统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }
}