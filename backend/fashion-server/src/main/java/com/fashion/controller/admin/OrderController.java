package com.fashion.controller.admin;

import com.fashion.entity.Orders;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理
 */
@RestController
@RequestMapping("/admin/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 分页查询
     */
    @GetMapping
    public Result<PageResult<Orders>> page(@RequestParam int page, @RequestParam int pageSize, @RequestParam(required = false) String number, @RequestParam(required = false) String status) {
        System.out.println("OrderController.page() called with page=" + page + ", pageSize=" + pageSize + ", number=" + number + ", status=" + status);
        try {
            System.out.println("OrderController.page() orderService is null: " + (orderService == null));
            // 转换status参数类型
            Integer statusInt = null;
            if (status != null && !status.equals("0")) {
                try {
                    statusInt = Integer.parseInt(status);
                    System.out.println("OrderController.page() converted status to: " + statusInt);
                } catch (NumberFormatException e) {
                    System.out.println("OrderController.page() invalid status: " + status);
                }
            }
            // 调用Service层的分页查询方法
            System.out.println("OrderController.page() calling orderService.pageOrders()");
            PageResult<Orders> pageResult = orderService.pageOrders(page, pageSize, number, statusInt);
            System.out.println("OrderController.page() returned successfully with total=" + pageResult.getTotal() + ", records size=" + pageResult.getRecords().size());
            return Result.success(pageResult);
        } catch (Exception e) {
            System.out.println("OrderController.page() caught exception:");
            e.printStackTrace();
            return Result.error("查询订单列表失败");
        }
    }

    /**
     * 根据id查询订单
     */
    @GetMapping("/{id}")
    public Result<Orders> getById(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        Orders order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    /**
     * 修改订单状态
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Orders orders) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        orders.setId(id);
        boolean flag = orderService.update(orders);
        if (!flag) {
            return Result.error("修改失败");
        }
        return Result.success();
    }
}
