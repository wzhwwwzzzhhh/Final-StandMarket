package com.fashion.controller.admin;

import com.fashion.entity.Orders;
import com.fashion.entity.Product;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import com.fashion.service.ProductService;
import com.fashion.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计
 */
@RestController
@RequestMapping("/admin/statistics")
public class StatisticsController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    /**
     * 销售统计
     */
    @GetMapping("/sales")
    public Result<Map<String, Object>> sales() {
        Map<String, Object> result = new HashMap<>();
        
        // 总订单数
        long totalOrders = orderService.count();
        result.put("totalOrders", totalOrders);
        
        // 总销售额
        List<Orders> orderList = orderService.listPaidOrders();
        BigDecimal totalSales = BigDecimal.ZERO;
        for (Orders order : orderList) {
            totalSales = totalSales.add(order.getAmount());
        }
        result.put("totalSales", totalSales);
        
        // 总商品数
        long totalProducts = productService.count();
        result.put("totalProducts", totalProducts);
        
        // 总用户数
        long totalUsers = userService.count();
        result.put("totalUsers", totalUsers);
        
        return Result.success(result);
    }

    /**
     * 商品销售排行
     */
    @GetMapping("/product/sales")
    public Result<List<Product>> productSales() {
        List<Product> list = productService.listTopSales();
        return Result.success(list);
    }
}
