package com.fashion.controller.user;

import com.fashion.entity.Refund;
import com.fashion.result.Result;
import com.fashion.service.RefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端退款Controller
 */
@RestController("userRefundController")
@RequestMapping("/user/refund")
public class RefundController {

    @Autowired
    private RefundService refundService;

    /**
     * 申请退款
     */
    @PostMapping("/apply")
    public Result<Refund> apply(@RequestBody Map<String, Object> params) {
        try {
            Object orderIdObj = params.get("orderId");
            if (orderIdObj == null) {
                return Result.error("订单ID不能为空");
            }
            Long orderId = Long.valueOf(orderIdObj.toString());
            String reason = (String) params.get("reason");

            Refund refund = refundService.apply(orderId, reason);
            return Result.success(refund);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 我的退款记录
     */
    @GetMapping("/list")
    public Result<List<Refund>> list() {
        try {
            List<Refund> list = refundService.listUserRefunds();
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
