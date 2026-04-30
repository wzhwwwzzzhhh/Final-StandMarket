package com.fashion.controller.user;

import com.fashion.dto.OrderCreateDTO;
import com.fashion.entity.Orders;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户订单Controller
 */
@RestController
@RequestMapping("/user/order")
public class UserOrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 创建订单
     */
    @PostMapping("/create")
    public Result<Orders> create(@RequestBody OrderCreateDTO orderCreateDTO) {
        try {
            Orders createdOrder = orderService.create(orderCreateDTO);
            return Result.success(createdOrder);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取用户订单列表
     */
    @GetMapping("/list")
    public Result<List<Orders>> list(@RequestParam(required = false) Integer status) {
        List<Orders> orderList = orderService.listUserOrders(status);
        return Result.success(orderList);
    }
    
    /**
     * 根据ID查询订单详情
     */
    @GetMapping("/detail/{id}")
    public Result<Orders> detail(@PathVariable Long id) {
        Orders order = orderService.getById(id);
        return Result.success(order);
    }
    
    /**
     * 取消订单
     */
    @PutMapping("/cancel/{id}")
    public Result<String> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success("订单取消成功");
    }
    
    /**
     * 支付订单
     */
    @PutMapping("/pay/{id}")
    public Result<String> pay(@PathVariable Long id) {
        orderService.pay(id);
        return Result.success("支付成功");
    }
    
    /**
     * 确认收货
     */
    @PutMapping("/confirm/{id}")
    public Result<String> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return Result.success("收货成功");
    }
}
