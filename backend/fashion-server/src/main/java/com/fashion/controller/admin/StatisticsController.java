package com.fashion.controller.admin;

import com.fashion.entity.Orders;
import com.fashion.entity.Product;
import com.fashion.result.Result;
import com.fashion.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据统计
 */
@RestController
@RequestMapping("/admin/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 销售总览
     */
    @GetMapping("/sales")
    public Result<Map<String, Object>> sales() {
        return Result.success(statisticsService.getSalesOverview());
    }

    /**
     * 商品销售排行
     */
    @GetMapping("/product/sales")
    public Result<List<Product>> productSales() {
        return Result.success(statisticsService.getTopSales());
    }

    /**
     * 销售趋势
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.getSalesTrend(days));
    }

    /**
     * 分类分布
     */
    @GetMapping("/category-distribution")
    public Result<List<Map<String, Object>>> categoryDistribution() {
        return Result.success(statisticsService.getCategoryDistribution());
    }

    /**
     * 最近订单
     */
    @GetMapping("/recent-orders")
    public Result<List<Orders>> recentOrders(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(statisticsService.getRecentOrders(limit));
    }
}
