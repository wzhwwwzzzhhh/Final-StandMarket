package com.fashion.mapper;

import com.fashion.entity.SeckillCoupon;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀券Mapper
 */
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillCouponMapper {
    /**
     * 新增秒杀券
     * @param seckillCoupon 秒杀券信息
     */
    void insert(SeckillCoupon seckillCoupon);

    /**
     * 根据id删除秒杀券
     * @param id 秒杀券id
     */
    void deleteById(Long id);

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
    SeckillCoupon selectById(Long id);

    /**
     * 分页查询秒杀券
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 券名称
     * @return 秒杀券列表
     */
    List<SeckillCoupon> list(@Param("page") int page, @Param("pageSize") int pageSize, @Param("name") String name);

    /**
     * 查询秒杀券总数
     * @param name 券名称
     * @return 总数
     */
    int count(@Param("name") String name);

    /**
     * 查询秒杀券列表（用户端）
     * @return 秒杀券列表
     */
    List<SeckillCoupon> listCoupons();

    /**
     *
     * @param couponId
     */
    @Update("update seckill_coupon set stock = stock - 1 where id = #{couponId} and stock > 0")
    void reduceStock(Long couponId);

    @Update("update seckill_coupon set stock = stock + 1 where id = #{couponId}")
    void addStock(Long couponId);
}