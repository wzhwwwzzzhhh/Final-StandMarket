package com.fashion.controller.admin;

import com.fashion.common.annotation.OperationLog;
import com.fashion.entity.Refund;
import com.fashion.result.Result;
import com.fashion.service.RefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端退款审核Controller
 */
@RestController("adminRefundController")
@RequestMapping("/admin/refund")
public class RefundController {

    @Autowired
    private RefundService refundService;

    /**
     * 退款审核列表（可选 status 筛选）
     */
    @GetMapping("/list")
    public Result<List<Refund>> list(@RequestParam(required = false) Integer status) {
        try {
            List<Refund> list = refundService.listAllRefunds(status);
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 同意退款
     */
    @PutMapping("/approve")
    @OperationLog(module = "退款管理", operation = "同意退款")
    public Result<String> approve(@RequestBody Map<String, Object> params) {
        try {
            Object idObj = params.get("id");
            if (idObj == null) {
                return Result.error("退款ID不能为空");
            }
            Long id = Long.valueOf(idObj.toString());
            String opinion = (String) params.get("opinion");

            refundService.approve(id, opinion);
            return Result.success("已同意，等待退款处理");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 拒绝退款
     */
    @PutMapping("/reject")
    @OperationLog(module = "退款管理", operation = "拒绝退款")
    public Result<String> reject(@RequestBody Map<String, Object> params) {
        try {
            Object idObj = params.get("id");
            if (idObj == null) {
                return Result.error("退款ID不能为空");
            }
            Long id = Long.valueOf(idObj.toString());
            String opinion = (String) params.get("opinion");
            if (opinion == null || opinion.trim().isEmpty()) {
                return Result.error("审核意见不能为空");
            }

            refundService.reject(id, opinion.trim());
            return Result.success("已拒绝退款");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
