package com.fashion.service;

import com.fashion.dto.SeckillSubmitResult;
import com.fashion.entity.SeckillCoupon;
import com.fashion.entity.PageResult;
import com.fashion.entity.SeckillOrder;
import com.fashion.result.Result;

import java.util.List;

/**
 * 秒杀券服务接口
 */
public interface SeckillCouponService {
    /**
     * 新增秒杀券
     * @param seckillCoupon 秒杀券信息
     */
    void save(SeckillCoupon seckillCoupon);

    /**
     * 删除秒杀券
     * @param id 秒杀券id
     */
    void delete(Long id);

    /**
     * 更新秒杀券信息
     * @param seckillCoupon 秒杀券信息
     */
    void update(SeckillCoupon seckillCoupon);

    /**
     * 根据id查询秒杀券
     * @param id 秒杀券id
     * @return 秒杀券信息
     */
    SeckillCoupon getById(Long id);

    /**
     * 分页查询秒杀券
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 券名称
     * @return 分页结果
     */
    PageResult<SeckillCoupon> pageCoupons(int page, int pageSize, String name);

    /**
     * 查询秒杀券列表
     * @return 秒杀券列表
     */
    List<SeckillCoupon> listCoupons();

    /**
     * 秒杀券抢购
     * @param couponId
     * @return
     */
    Result<SeckillSubmitResult> seckillCoupon(Long couponId);

    /**
     * 创建秒杀订单
     * @param couponId
     * @return
     */
    Result<SeckillOrder> createSeckillOrder(Long couponId);

    /**
     * 数据预热
     * @param id
     */
    void preheat(Long id);

    /**
     * 批量数据预热
     * @param ids
     */
    void preheatBatch(List<Long> ids);
}