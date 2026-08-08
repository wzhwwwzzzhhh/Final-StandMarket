package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.CouponTemplate;
import com.fashion.entity.UserCoupon;
import com.fashion.result.Result;
import com.fashion.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端通用优惠券（领券中心 / 卡包 / 结算页可用券）
 */
@RestController
@RequestMapping("/user/coupon")
public class UserCouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 可领券列表（首页/领券中心）
     */
    @GetMapping("/templates")
    public Result<List<CouponTemplate>> claimableTemplates() {
        return Result.success(couponService.listClaimableTemplates());
    }

    /**
     * 领取优惠券
     */
    @PostMapping("/claim/{templateId}")
    public Result<String> claim(@PathVariable Long templateId) {
        try {
            Long userId = BaseContext.getUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            couponService.claim(userId, templateId);
            return Result.success("领取成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 我的卡包
     */
    @GetMapping("/my")
    public Result<List<UserCoupon>> myCoupons(@RequestParam(required = false) Integer status) {
        try {
            Long userId = BaseContext.getUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            return Result.success(couponService.listMyCoupons(userId, status));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 结算页可用券（按金额门槛 + 商品范围过滤）
     */
    @GetMapping("/available")
    public Result<List<UserCoupon>> available(
            @RequestParam(required = false) BigDecimal totalAmount,
            @RequestParam(required = false) String productIds) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        List<Long> ids = new ArrayList<>();
        if (productIds != null && !productIds.isEmpty()) {
            for (String s : productIds.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        ids.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return Result.success(couponService.listAvailable(userId, totalAmount, ids));
    }
}