package com.fashion.controller.admin;

import com.fashion.common.annotation.OperationLog;
import com.fashion.dto.OrderStatusUpdateDTO;
import com.fashion.entity.Orders;
import com.fashion.entity.PageResult;
import com.fashion.entity.Payment;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单管理
 */
@RestController
@RequestMapping("/admin/order")
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;

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
    @OperationLog(module = "订单管理", operation = "修改订单状态")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateDTO request) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        try {
            orderService.updateAdminStatus(id, request == null ? null : request.getStatus());
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发货
     */
    @PutMapping("/deliver")
    @OperationLog(module = "订单管理", operation = "订单发货")
    public Result<String> deliver(@RequestBody Map<String, Object> params) {
        Object idObj = params.get("id");
        if (idObj == null) {
            return Result.error("订单ID不能为空");
        }
        Long id = Long.valueOf(idObj.toString());
        String trackingCompany = (String) params.get("trackingCompany");
        String trackingNumber = (String) params.get("trackingNumber");

        if (trackingCompany == null || trackingCompany.trim().isEmpty()) {
            return Result.error("快递公司不能为空");
        }
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            return Result.error("快递单号不能为空");
        }

        try {
            orderService.deliver(id, trackingCompany.trim(), trackingNumber.trim());
            return Result.success("发货成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询订单支付信息
     */
    @GetMapping("/{id}/payment")
    public Result<Payment> getPaymentInfo(@PathVariable Long id) {
        Payment payment = paymentService.getByOrderId(id, 0);
        if (payment == null) {
            return Result.error("暂无支付记录");
        }
        return Result.success(payment);
    }
}
