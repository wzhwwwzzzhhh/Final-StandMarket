package com.fashion.controller.user;

import com.fashion.dto.SeckillSubmitResult;
import com.fashion.entity.SeckillCoupon;
import com.fashion.entity.SeckillOrder;
import com.fashion.result.Result;
import com.fashion.service.SeckillCouponService;
import com.fashion.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端秒杀券管理
 */
@RestController
@RequestMapping("/user/seckill/coupon")
public class UserSeckillCouponController {

    @Autowired
    private SeckillCouponService seckillCouponService;
    

    /**
     * 获取秒杀券列表
     */
    @GetMapping("/list")
    public Result<List<SeckillCoupon>> list() {
        List<SeckillCoupon> coupons = seckillCouponService.listCoupons();
        return Result.success(coupons);
    }

    /**
     * 获取秒杀券详情
     */
    @GetMapping("/{id}")
    public Result<SeckillCoupon> detail(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        SeckillCoupon coupon = seckillCouponService.getById(id);
        if (coupon == null) {
            return Result.error("秒杀券不存在");
        }
        return Result.success(coupon);
    }
    
    /**
     * 创建秒杀订单（抢购秒杀券）
     * 符合RESTful风格：POST /资源/{id}/子资源
     */
    @PostMapping("/{couponId}/orders")
    public Result<SeckillSubmitResult> createSeckillOrder(@PathVariable Long couponId) {
        return seckillCouponService.seckillCoupon(couponId);
    }

}