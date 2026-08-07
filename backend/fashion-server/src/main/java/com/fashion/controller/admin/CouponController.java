package com.fashion.controller.admin;

import com.fashion.common.annotation.OperationLog;
import com.fashion.entity.CouponTemplate;
import com.fashion.entity.PageResult;
import com.fashion.entity.UserCoupon;
import com.fashion.result.Result;
import com.fashion.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 通用优惠券管理（模板 CRUD + 用户持券查询）
 */
@RestController
@RequestMapping("/admin/coupon")
public class CouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 创建模板
     */
    @PostMapping("/template")
    @OperationLog(module = "优惠券管理", operation = "创建优惠券模板")
    public Result<String> saveTemplate(@RequestBody CouponTemplate template) {
        try {
            if (template == null || template.getName() == null || template.getName().isEmpty()) {
                return Result.error("券名称不能为空");
            }
            if (template.getType() == null) {
                return Result.error("券类型不能为空");
            }
            couponService.saveTemplate(template);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新模板
     */
    @PutMapping("/template")
    @OperationLog(module = "优惠券管理", operation = "更新优惠券模板")
    public Result<String> updateTemplate(@RequestBody CouponTemplate template) {
        try {
            if (template.getId() == null) {
                return Result.error("id不能为空");
            }
            couponService.updateTemplate(template);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模板（软删）
     */
    @DeleteMapping("/template")
    @OperationLog(module = "优惠券管理", operation = "删除优惠券模板")
    public Result<String> deleteTemplate(@RequestParam Long id) {
        try {
            if (id == null) {
                return Result.error("id不能为空");
            }
            couponService.deleteTemplate(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 模板详情
     */
    @GetMapping("/template/{id}")
    public Result<CouponTemplate> getTemplate(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        CouponTemplate template = couponService.getTemplate(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        return Result.success(template);
    }

    /**
     * 分页查询模板
     */
    @GetMapping("/template/page")
    public Result<PageResult<CouponTemplate>> pageTemplates(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.success(couponService.pageTemplates(page, pageSize, name, status));
    }

    /**
     * 分页查询用户持券（运营管理）
     */
    @GetMapping("/userCoupon/page")
    public Result<PageResult<UserCoupon>> pageUserCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(couponService.pageUserCoupons(page, pageSize, status, keyword));
    }
}