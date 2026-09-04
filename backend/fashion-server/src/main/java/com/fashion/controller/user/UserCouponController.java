package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.CouponTemplate;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.PublicBusinessException;
import com.fashion.result.Result;
import com.fashion.service.CouponService;
import com.fashion.service.support.CartSelectionValidator;
import com.fashion.vo.AvailableCouponVO;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.fashion.exception.PublicBusinessException.Code.CART_ITEM_FORMAT_INVALID;
import static com.fashion.exception.PublicBusinessException.Code.SELECT_CART_ITEMS;
import static com.fashion.exception.PublicBusinessException.of;

/**
 * 用户端通用优惠券（领券中心 / 卡包 / 结算页可用券）
 */
@RestController
@RequestMapping("/user/coupon")
@Slf4j
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
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            String traceId = UUID.randomUUID().toString();
            log.error("领取优惠券失败 traceId={}, templateId={}, exceptionType={}",
                    traceId, templateId, e.getClass().getName());
            return Result.error("领取优惠券失败，请稍后重试");
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
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            String traceId = UUID.randomUUID().toString();
            log.error("获取我的优惠券失败 traceId={}, exceptionType={}",
                    traceId, e.getClass().getName());
            return Result.error("获取优惠券失败，请稍后重试");
        }
    }

    /**
     * 结算页可用券（按金额门槛 + 商品范围过滤）
     */
    @GetMapping("/available")
    public Result<List<AvailableCouponVO>> available(@RequestParam String cartItemIds) {
        try {
            Long userId = BaseContext.getUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            List<Long> ids = parseCartItemIds(cartItemIds);
            return Result.success(couponService.listAvailable(userId, ids));
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            String traceId = UUID.randomUUID().toString();
            log.error("获取可用优惠券失败 traceId={}, exceptionType={}",
                    traceId, e.getClass().getName());
            return Result.error("获取可用优惠券失败，请稍后重试");
        }
    }

    private List<Long> parseCartItemIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw of(SELECT_CART_ITEMS);
        }
        List<Long> ids = new ArrayList<>();
        try {
            for (String token : raw.split(",", -1)) {
                if (token.trim().isEmpty()) {
                    throw of(CART_ITEM_FORMAT_INVALID);
                }
                ids.add(Long.parseLong(token.trim()));
            }
        } catch (NumberFormatException e) {
            throw of(CART_ITEM_FORMAT_INVALID);
        }
        CartSelectionValidator.validate(ids);
        return ids;
    }
}
