package com.fashion.mapper;

import com.fashion.entity.SeckillOrder;
import com.fashion.dto.UserCouponDto;
import com.fashion.vo.SeckillOrderVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 秒杀订单Mapper
 */
@Mapper
public interface SeckillOrderMapper {
    
    /**
     * 新增秒杀订单
     * @param seckillOrder 秒杀订单信息
     */
    void insert(SeckillOrder seckillOrder);
    
    /**
     * 根据id删除秒杀订单
     * @param id 秒杀订单id
     */
    void deleteById(Long id);
    
    /**
     * 更新秒杀订单信息
     * @param seckillOrder 秒杀订单信息
     */
    void update(SeckillOrder seckillOrder);
    
    /**
     * 根据id查询秒杀订单
     * @param id 秒杀订单id
     * @return 秒杀订单信息
     */
    SeckillOrder selectById(Long id);
    
    /**
     * 根据订单号查询秒杀订单
     * @param orderNumber 订单号
     * @return 秒杀订单信息
     */
    SeckillOrder selectByOrderNumber(String orderNumber);
    
    /**
     * 根据用户id查询秒杀订单列表
     * @param userId 用户id
     * @return 秒杀订单列表
     */
    List<SeckillOrder> selectByUserId(Long userId);
    
    /**
     * 根据用户id和秒杀券id查询秒杀订单
     * @param userId 用户id
     * @param couponId 秒杀券id
     * @return 秒杀订单信息
     */
    SeckillOrder selectByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);
    
    /**
     * 查询用户拥有的优惠券列表（多表查询）
     * @param params 查询参数
     * @return 用户优惠券列表
     */
    List<UserCouponDto> selectUserCoupons(java.util.Map<String, Object> params);
    
    /**
     * 更新订单状态
     * @param orderNumber 订单号
     * @param status 订单状态
     */
    boolean updateStatus(@Param("orderNumber") String orderNumber, @Param("status") Integer status);
    
    /**
     * 更新支付时间
     * @param orderNumber 订单号
     * @param payTime 支付时间
     */
    void updatePayTime(@Param("orderNumber") String orderNumber, @Param("payTime") java.time.LocalDateTime payTime);
    
    /**
     * 管理端：分页查询秒杀订单列表
     * @param params 查询参数
     * @return 秒杀订单列表
     */
    List<SeckillOrder> selectSeckillOrderPage(java.util.Map<String, Object> params);
    
    /**
     * 管理端：统计分页查询的订单总数
     * @param params 查询参数
     * @return 订单总数
     */
    Long countSeckillOrderPage(java.util.Map<String, Object> params);
    
    /**
     * 管理端：统计所有秒杀订单数量
     * @return 订单总数
     */
    Long countAllSeckillOrders();
    
    /**
     * 管理端：根据状态统计秒杀订单数量
     * @param status 订单状态
     * @return 订单数量
     */
    Long countSeckillOrdersByStatus(Integer status);
    
    /**
     * 管理端：获取秒杀总销售额
     * @return 总销售额
     */
    java.math.BigDecimal getTotalSeckillSalesAmount();
    
    /**
     * 管理端：获取今日秒杀销售额
     * @return 今日销售额
     */
    java.math.BigDecimal getTodaySeckillSalesAmount();

    /**
     * 根据用户id查询秒杀订单列表
     * @param userId 用户id
     * @return 秒杀订单列表
     */
    @Select("SELECT * FROM seckill_order WHERE user_id = #{userId}")
    List<SeckillOrderVo> selectListByUserId(Long userId);
}