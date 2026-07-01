package com.fashion.service;

import com.fashion.entity.Orders;
import com.fashion.entity.Product;

import java.util.List;
import java.util.Map;

public interface StatisticsService {

    /**
     * 获取销售总览
     */
    Map<String, Object> getSalesOverview();

    /**
     * 获取近N天销售趋势
     */
    List<Map<String, Object>> getSalesTrend(int days);

    /**
     * 获取商品分类分布
     */
    List<Map<String, Object>> getCategoryDistribution();

    /**
     * 获取最近订单
     */
    List<Orders> getRecentOrders(int limit);

    /**
     * 获取商品销售排行
     */
    List<Product> getTopSales();
}
