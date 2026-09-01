package com.fashion.service;

import com.fashion.dto.OrderCreateDTO;
import com.fashion.entity.Orders;
import com.fashion.entity.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService {
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
    List<Orders> listOrders(String number, Integer status);
    
    /**
     * 分页查询订单
     * @param page 页码
     * @param pageSize 每页大小
     * @param number 订单号
     * @param status 订单状态
     * @return 分页后的订单列表
     */
    PageResult<Orders> pageOrders(int page, int pageSize, String number, Integer status);
    
    /**
     * 根据ID查询订单
     * @param id 订单ID
     * @return 订单
     */
    Orders getById(Long id);

    /**
     * 按服务端当前登录用户查询订单。
     */
    Orders getCurrentUserOrderById(Long id);
    
    void updateAdminStatus(Long id, Integer status);
    
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
     * 创建订单
     * @param orderCreateDTO 订单创建参数
     * @return 创建的订单
     */
    Orders create(OrderCreateDTO orderCreateDTO);

    
    /**
     * 获取用户订单列表
     * @param status 订单状态
     * @return 订单列表
     */
    List<Orders> listUserOrders(Integer status);
    
    /**
     * 取消订单
     * @param id 订单ID
     */
    void cancel(Long id);
    
    /**
     * 定时任务：自动取消超时未支付且绑定了通用优惠券的订单并释放券
     */
    void autoCancelTimeoutOrders();
    
    /**
     * 确认收货
     * @param id 订单ID
     */
    void confirm(Long id);

    /**
     * 支付宝回调处理（支付记录 + 订单状态在一个事务中）
     * @param orderId 订单ID
     * @param paymentId 支付记录ID
     * @param tradeNo 支付宝交易号
     * @param payTime 支付时间
     */
    void handlePayCallback(Long orderId, Long paymentId, String tradeNo, java.time.LocalDateTime payTime);

    /**
     * 发货
     * @param id 订单ID
     * @param trackingCompany 快递公司
     * @param trackingNumber 快递单号
     */
    void deliver(Long id, String trackingCompany, String trackingNumber);
}
