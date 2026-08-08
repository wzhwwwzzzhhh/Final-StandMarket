package com.fashion.service.impl;

import com.fashion.entity.Orders;
import com.fashion.entity.Product;
import com.fashion.mapper.CategoryMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.UserMapper;
import com.fashion.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public Map<String, Object> getSalesOverview() {
        Map<String, Object> result = new HashMap<>();

        long totalOrders = orderMapper.count();
        result.put("totalOrders", totalOrders);

        List<Orders> paidOrders = orderMapper.listPaidOrders();
        BigDecimal totalSales = paidOrders.stream()
                .map(Orders::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("totalSales", totalSales);

        long totalProducts = productMapper.count();
        result.put("totalProducts", totalProducts);

        long totalUsers = userMapper.count(null, null);
        result.put("totalUsers", totalUsers);

        return result;
    }

    @Override
    public List<Map<String, Object>> getSalesTrend(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        List<Orders> paidOrders = orderMapper.listPaidOrders();

        Map<String, Map<String, Object>> dailyMap = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", date.format(fmt));
            entry.put("amount", BigDecimal.ZERO);
            entry.put("count", 0);
            dailyMap.put(date.format(fmt), entry);
        }

        for (Orders order : paidOrders) {
            if (order.getCheckoutTime() != null) {
                LocalDate orderDate = order.getCheckoutTime().toLocalDate();
                if (!orderDate.isBefore(startDate)) {
                    String key = orderDate.format(fmt);
                    Map<String, Object> entry = dailyMap.get(key);
                    if (entry != null) {
                        BigDecimal current = (BigDecimal) entry.get("amount");
                        entry.put("amount", current.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO));
                        entry.put("count", (int) entry.get("count") + 1);
                    }
                }
            }
        }

        return new ArrayList<>(dailyMap.values());
    }

    @Override
    public List<Map<String, Object>> getCategoryDistribution() {
        return productMapper.selectCategoryDistribution();
    }

    @Override
    public List<Orders> getRecentOrders(int limit) {
        return orderMapper.selectRecentOrders(limit);
    }

    @Override
    public List<Product> getTopSales() {
        return productMapper.listTopSales();
    }
}
