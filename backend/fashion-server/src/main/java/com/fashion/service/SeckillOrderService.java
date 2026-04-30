package com.fashion.service;

import com.fashion.dto.OrderAmountCalculateDTO;
import com.fashion.dto.UserCouponDto;
import com.fashion.entity.PageResult;
import com.fashion.entity.SeckillOrder;
import com.fashion.result.Result;
import com.fashion.vo.OrderAmountVO;
import com.fashion.vo.SeckillOrderStatisticsVO;
import com.fashion.vo.SeckillOrderVo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀订单服务接口
 */
public interface SeckillOrderService {

    /**
     * 根据订单号查询秒杀订单
     * @param orderNumber 订单号
     * @return 秒杀订单信息
     */
    SeckillOrder getOrderByNumber(String orderNumber);
    
    /**
     * 根据用户ID查询秒杀订单列表
     * @param userId 用户ID
     * @return 秒杀订单列表
     */
    List<SeckillOrder> getOrdersByUserId(Long userId);
    
    /**
     * 更新订单状态
     * @param orderNumber 订单号
     * @param status 订单状态
     * @return 是否成功
     */
    boolean updateOrderStatus(String orderNumber, Integer status);
    
    /**
     * 更新支付时间
     * @param orderNumber 订单号
     * @param payTime 支付时间
     * @return 是否成功
     */
    boolean updatePayTime(String orderNumber, LocalDateTime payTime);
    
    /**
     * 取消订单
     * @param orderNumber 订单号
     * @return 是否成功
     */
    boolean cancelOrder(String orderNumber);
    
    /**
     * 获取用户拥有的优惠券列表
     * @param userId 用户ID
     * @param status 优惠券状态（1:可用 2:已使用 3:已过期，为空表示全部）
     * @return 用户优惠券列表
     */
    List<UserCouponDto> getUserCoupons(Long userId, Integer status);
    
    /**
     * 完成订单支付
     * @param orderNumber 订单号
     * @return 是否成功
     */
    boolean completePayment(String orderNumber);
    
    /**
     * 管理端：分页查询秒杀订单列表
     * @param page 页码
     * @param pageSize 页大小
     * @param search 搜索条件
     * @param status 订单状态
     * @param activityId 活动ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    PageResult getSeckillOrderPage(int page, int pageSize, String search, Integer status, Long activityId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 管理端：确认订单支付
     * @param orderNumber 订单号
     * @return 是否成功
     */
    boolean confirmPayment(String orderNumber);
    
    /**
     * 管理端：删除订单
     * @param id 订单ID
     * @return 是否成功
     */
    boolean deleteOrder(Long id);
    
    /**
     * 管理端：获取秒杀订单统计信息
     * @return 统计信息
     */
    SeckillOrderStatisticsVO getSeckillOrderStatistics();
    
    /**
     * 管理端：获取秒杀订单总数
     * @return 订单总数
     */
    Long getTotalSeckillOrderCount();
    
    /**
     * 管理端：获取待支付订单数
     * @return 待支付订单数
     */
    Long getPendingOrderCount();
    
    /**
     * 管理端：获取总销售额
     * @return 总销售额
     */
    Double getTotalSalesAmount();

    /**
     * 用户端：获取所有秒杀订单
     * @return
     */
    Result<List<SeckillOrderVo>> listAll();

    /**
     * 计算订单金额（根据秒杀活动和秒杀券）
     * @param calculateDTO 计算参数
     * @return 金额计算结果
     */
    Result<OrderAmountVO> calculateOrderAmount(OrderAmountCalculateDTO calculateDTO);
}