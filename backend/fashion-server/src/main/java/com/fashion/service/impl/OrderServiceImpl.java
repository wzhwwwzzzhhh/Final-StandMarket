package com.fashion.service.impl;

import com.fashion.entity.*;
import com.fashion.entity.*;
import com.fashion.mapper.*;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private ProductMapper productMapper;
    
    @Override
    public List<Orders> selectByCondition(Map<String, Object> params) {
        return orderMapper.selectByCondition(params);
    }
    
    /**
     * 根据订单号和状态查询订单列表
     * @param number 订单号
     * @param status 订单状态
     * @return 订单列表
     */
    @Override
    public List<Orders> listOrders(String number, Integer status) {
        return orderMapper.listOrders(number, status);
    }
    
    /**
     * 分页查询订单
     * @param page 页码
     * @param pageSize 每页大小
     * @param number 订单号
     * @param status 订单状态
     * @return 分页后的订单列表
     */
    @Override
    public PageResult<Orders> pageOrders(int page, int pageSize, String number, Integer status) {
        System.out.println("OrderServiceImpl.pageOrders() called with page=" + page + ", pageSize=" + pageSize + ", number=" + number + ", status=" + status);
        // 开始分页
        PageHelper.startPage(page, pageSize);
        // 执行查询
        System.out.println("OrderServiceImpl.pageOrders() calling orderMapper.listOrders()");
        List<Orders> orders = orderMapper.listOrders(number, status);
        System.out.println("OrderServiceImpl.pageOrders() orderMapper.listOrders() returned " + orders.size() + " orders");
        // 包装成PageInfo
        PageInfo<Orders> pageInfo = new PageInfo<>(orders);
        System.out.println("OrderServiceImpl.pageOrders() pageInfo.getTotal()=" + pageInfo.getTotal() + ", pageInfo.getList().size()=" + pageInfo.getList().size());
        // 构造PageResult返回
        PageResult<Orders> pageResult = new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
        System.out.println("OrderServiceImpl.pageOrders() returning pageResult");
        return pageResult;
    }
    
    /**
     * 根据ID查询订单
     * @param id 订单ID
     * @return 订单
     */
    @Override
    public Orders getById(Long id) {
        Orders order = orderMapper.getById(id);
        if (order != null) {
            List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
            order.setItems(orderDetails);
        }
        return order;
    }
    
    /**
     * 更新订单
     * @param orders 订单信息
     * @return 是否成功
     */
    @Override
    public boolean update(Orders orders) {
        return orderMapper.update(orders) > 0;
    }
    
    @Override
    public long count() {
        return orderMapper.count();
    }
    
    @Override
    public List<Orders> listPaidOrders() {
        return orderMapper.listPaidOrders();
    }
    
    /**
     * 创建订单
     */
    @Transactional
    @Override
    public Orders create(OrderCreateDTO orderCreateDTO) {
        List<Long> productIds = orderCreateDTO.getProductIds();
        
        // 创建订单对象
        Orders orders = new Orders();
        
        // 获取当前用户ID
        Long userId = BaseContext.getUserId();
        if(userId == null) {
            throw new RuntimeException("用户未登录");
        }
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setStatus(1); // 1待付款
        orders.setPayStatus(0); // 0未支付
        orders.setPayMethod(orderCreateDTO.getPayMethod() != null ? orderCreateDTO.getPayMethod() : 1);
        
        // 生成订单号
        String orderNumber = "ORD" + System.currentTimeMillis();
        orders.setNumber(orderNumber);
        
        // 设置配送状态（使用前端传递的值，默认为1：立即送出）
        orders.setDeliveryStatus(orderCreateDTO.getDeliveryStatus() != null ? orderCreateDTO.getDeliveryStatus() : 1);
        
        // 设置预计配送时间
        if (orderCreateDTO.getEstimatedDeliveryTime() != null) {
            orders.setEstimatedDeliveryTime(orderCreateDTO.getEstimatedDeliveryTime());
        }
        
        // 设置运费（默认为0）
        orders.setShippingFee(BigDecimal.ZERO);
        
        // 设置收货地址
        if (orderCreateDTO.getAddressId() != null) {
            AddressBook addressBook = addressBookMapper.getById(orderCreateDTO.getAddressId());
            if (addressBook != null) {
                orders.setConsignee(addressBook.getConsignee());
                orders.setPhone(addressBook.getPhone());
                orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + 
                                addressBook.getDistrictName() + addressBook.getDetail());
                orders.setAddressBookId(addressBook.getId());
            }
        }
        
        // 设置订单金额（使用前端传递的总金额）
        if (orderCreateDTO.getAmount() != null) {
            orders.setAmount(orderCreateDTO.getAmount());
        } else {
            // 如果前端没有传递金额，则根据购物车商品计算
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderDetail> orderDetails = new ArrayList<>();
            
            if (productIds != null && !productIds.isEmpty()) {
                for (Long cartItemId : productIds) {
                    ShoppingCart cartItem = shoppingCartMapper.findById(cartItemId);
                    if (cartItem != null) {
                        totalAmount = totalAmount.add(cartItem.getAmount());
                        
                        OrderDetail orderDetail = new OrderDetail();
                        orderDetail.setOrderId(orders.getId());
                        orderDetail.setProductId(cartItem.getProductId());
                        orderDetail.setName(cartItem.getName());
                        orderDetail.setImage(cartItem.getImage());
                        orderDetail.setNumber(cartItem.getNumber());
                        orderDetail.setAmount(cartItem.getAmount());
                        orderDetails.add(orderDetail);
                    }
                }
            }
            orders.setAmount(totalAmount);
            
            // 保存订单
            orderMapper.insert(orders);
            
            // 保存订单详情
            if (!orderDetails.isEmpty()) {
                orderDetailMapper.batchInsert(orderDetails);
            }
            
            return orders;
        }
        
        // 保存订单
        orderMapper.insert(orders);

        // 保存订单详情（从购物车表查询商品信息）
        List<OrderDetail> orderDetails = new ArrayList<>();
        
        if (productIds != null && !productIds.isEmpty()) {
            for (Long cartItemId : productIds) {
                ShoppingCart cartItem = shoppingCartMapper.findById(cartItemId);
                if (cartItem != null) {
                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setOrderId(orders.getId());
                    orderDetail.setProductId(cartItem.getProductId());
                    orderDetail.setName(cartItem.getName());
                    orderDetail.setImage(cartItem.getImage());
                    orderDetail.setSkuInfo(cartItem.getSkuInfo());
                    orderDetail.setNumber(cartItem.getNumber());
                    orderDetail.setAmount(cartItem.getAmount());
                    orderDetails.add(orderDetail);
                }
            }
        }
        
        if (!orderDetails.isEmpty()) {
            orderDetailMapper.batchInsert(orderDetails);
        }
        // 返回创建的订单对象
        return orders;
    }

    
    /**
     * 获取用户订单列表
     */
    @Override
    public List<Orders> listUserOrders(Integer status) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        List<Orders> orders = orderMapper.listUserOrders(userId, status);
        // 为每个订单添加明细
        for (Orders order : orders) {
            List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
            order.setItems(orderDetails);
        }
        return orders;
    }
    
    /**
     * 取消订单
     */
    @Transactional
    @Override
    public void cancel(Long id) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        
        // 检查订单是否存在且属于当前用户
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        
        // 更新订单状态
        order.setStatus(5); // 5已取消
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }
    
    /**
     * 支付订单
     */
    @Transactional
    @Override
    public void pay(Long id) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        
        // 检查订单是否存在且属于当前用户
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        
        // 更新订单状态
        order.setPayStatus(1); // 1已支付
        order.setCheckoutTime(LocalDateTime.now());
        order.setStatus(2); // 2待发货
        orderMapper.update(order);
    }
    
    /**
     * 确认收货
     */
    @Transactional
    @Override
    public void confirm(Long id) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        
        // 检查订单是否存在且属于当前用户
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        
        // 更新订单状态
        order.setStatus(4); // 4已完成
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(order);
    }
}