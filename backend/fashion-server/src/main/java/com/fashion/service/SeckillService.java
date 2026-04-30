package com.fashion.service;

import com.fashion.entity.SeckillActivity;
import com.fashion.entity.SeckillCoupon;
import com.fashion.entity.SeckillOrder;
import com.fashion.entity.SpecialOffer;
import com.fashion.result.Result;

import java.util.List;

public interface SeckillService {

    List<SeckillActivity> listActivities();

    // 秒杀券管理

    SeckillCoupon getCouponById(Long id);

    List<SeckillCoupon> listCouponsByActivityId(Long activityId);

    // 特价商品管理

    SpecialOffer getSpecialOfferById(Long id);

    List<SpecialOffer> listSpecialOffers();

    // 秒杀核心逻辑
    Result<SeckillOrder> seckillCoupon(Long couponId);

    // 创建秒杀订单
    Result<SeckillOrder> createOrder(Long couponId);


    Result<?> seckillSpecialOffer(Long offerId, Long userId);

    // 预热秒杀数据到Redis
    void preheatSeckillData();

    // 处理秒杀订单
    void handleSeckillOrder(String orderNumber);
}
