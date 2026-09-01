package com.fashion.mapper;

import org.apache.ibatis.annotations.Param;
import com.fashion.entity.Orders;
import java.util.List;
import java.util.Map;

/**
 * 订单Mapper
 */
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface OrderMapper {
    /**
     * 条件查询
     * @param params 查询参数
     * @return 订单列表
     */
    List<Orders> selectByCondition(Map<String, Object> params);
    
    /**
     * 根据订单号和状态查询订单列表
     * @param number 订单号
     * @param status 订单状态
     * @return 订单列表
     */
    List<Orders> listOrders(@Param("number") String number, @Param("status") Integer status);
    
    /**
     * 根据ID查询订单
     * @param id 订单ID
     * @return 订单
     */
    Orders getById(Long id);

    Orders getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Orders getByIdForUpdate(Long id);
    
    /**
     * 更新订单
     * @param orders 订单信息
     * @return 影响行数
     */
    int update(Orders orders);

    int updateAdminStatus(@Param("id") Long id, @Param("status") Integer status);

    int markPaid(@Param("id") Long id, @Param("payTime") java.time.LocalDateTime payTime);

    int cancelPending(@Param("id") Long id,
                      @Param("expectedStockDeducted") Integer expectedStockDeducted,
                      @Param("cancelTime") java.time.LocalDateTime cancelTime);

    int deliverPaidOrder(@Param("id") Long id,
                         @Param("trackingCompany") String trackingCompany,
                         @Param("trackingNumber") String trackingNumber,
                         @Param("deliveryTime") java.time.LocalDateTime deliveryTime);

    int confirmDeliveredOrder(@Param("id") Long id, @Param("userId") Long userId,
                              @Param("deliveryTime") java.time.LocalDateTime deliveryTime);

    int markRefunding(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("expectedStatus") Integer expectedStatus);

    int restoreRejectedRefundOrder(@Param("id") Long id,
                                   @Param("targetStatus") Integer targetStatus);
    
    /**
     * 统计订单总数
     * @return 订单总数
     */
    long count();
    
    /**
     * 查询已支付的订单列表
     * @return 已支付订单列表
     */
    List<Orders> listPaidOrders();
    
    /**
     * 插入订单
     * @param orders 订单信息
     * @return 影响行数
     */
    int insert(Orders orders);
    
    /**
     * 获取用户订单列表
     * @param userId 用户ID
     * @param status 订单状态
     * @return 订单列表
     */
    List<Orders> listUserOrders(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 获取最近订单
     * @param limit 数量
     * @return 订单列表
     */
    List<Orders> selectRecentOrders(@Param("limit") int limit);

    /**
     * 按主键游标分批查询所有超时待支付普通订单（含无券订单）。
     */
    List<Orders> selectTimeoutOrders(@Param("minutes") int minutes,
                                     @Param("afterId") long afterId,
                                     @Param("limit") int limit);
}
