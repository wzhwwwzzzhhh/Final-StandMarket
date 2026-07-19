package com.fashion.service.impl;

import com.fashion.entity.*;
import com.fashion.mapper.*;
import com.fashion.service.PaymentService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
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
    @Autowired
    private PaymentService paymentService;

    @Override
    public List<Orders> selectByCondition(Map<String, Object> params) {
        return orderMapper.selectByCondition(params);
    }

    @Override
    public List<Orders> listOrders(String number, Integer status) {
        return orderMapper.listOrders(number, status);
    }

    @Override
    public PageResult<Orders> pageOrders(int page, int pageSize, String number, Integer status) {
        PageHelper.startPage(page, pageSize);
        List<Orders> orders = orderMapper.listOrders(number, status);
        PageInfo<Orders> pageInfo = new PageInfo<>(orders);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public Orders getById(Long id) {
        Orders order = orderMapper.getById(id);
        if (order != null) {
            List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
            order.setItems(orderDetails);
        }
        return order;
    }

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

    @Transactional
    @Override
    public Orders create(OrderCreateDTO orderCreateDTO) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        Orders orders = new Orders();
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setStatus(1);
        orders.setPayStatus(0);
        orders.setPayMethod(orderCreateDTO.getPayMethod() != null ? orderCreateDTO.getPayMethod() : 1);
        orders.setNumber("ORD" + System.currentTimeMillis());
        orders.setDeliveryStatus(orderCreateDTO.getDeliveryStatus() != null ? orderCreateDTO.getDeliveryStatus() : 1);

        if (orderCreateDTO.getEstimatedDeliveryTime() != null) {
            orders.setEstimatedDeliveryTime(orderCreateDTO.getEstimatedDeliveryTime());
        }

        orders.setShippingFee(BigDecimal.ZERO);

        if (orderCreateDTO.getAddressId() != null) {
            AddressBook addressBook = addressBookMapper.getById(orderCreateDTO.getAddressId());
            if (addressBook != null) {
                orders.setConsignee(addressBook.getConsignee());
                orders.setPhone(addressBook.getPhone());
                orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName()
                        + addressBook.getDistrictName() + addressBook.getDetail());
                orders.setAddressBookId(addressBook.getId());
            }
        }

        orders.setAmount(orderCreateDTO.getAmount() != null ? orderCreateDTO.getAmount() : BigDecimal.ZERO);

        orderMapper.insert(orders);

        List<Long> productIds = orderCreateDTO.getProductIds();
        if (productIds != null && !productIds.isEmpty()) {
            List<OrderDetail> orderDetails = new ArrayList<>();
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
            if (!orderDetails.isEmpty()) {
                orderDetailMapper.batchInsert(orderDetails);
            }
        }
        return orders;
    }

    @Override
    public List<Orders> listUserOrders(Integer status) {
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        List<Orders> orders = orderMapper.listUserOrders(userId, status);
        for (Orders order : orders) {
            List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
            order.setItems(orderDetails);
        }
        return orders;
    }

    @Transactional
    @Override
    public void cancel(Long id) {
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        order.setStatus(5);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    @Transactional
    @Override
    public void pay(Long id) {
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        if (order.getStatus() != 1) {
            throw new RuntimeException("订单状态不是待支付，无法支付");
        }

        Payment payment = paymentService.createPayment(
                order.getId(), 0, order.getAmount(),
                order.getPayMethod() != null ? order.getPayMethod() : 1);

        boolean success = paymentService.processPayment(payment.getPayNo());
        if (!success) {
            throw new RuntimeException("支付失败，请重试");
        }

        order.setPayStatus(1);
        order.setCheckoutTime(LocalDateTime.now());
        order.setStatus(2);
        orderMapper.update(order);
    }

    @Transactional
    @Override
    public void confirm(Long id) {
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权操作");
        }
        order.setStatus(4);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    @Transactional
    @Override
    public void updatePaySuccess(Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        order.setPayStatus(1);
        order.setCheckoutTime(LocalDateTime.now());
        order.setStatus(2);
        orderMapper.update(order);
        log.info("订单支付成功更新 orderId={}, number={}", id, order.getNumber());
    }

    @Transactional
    @Override
    public void deliver(Long id, String trackingCompany, String trackingNumber) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != 2) {
            throw new RuntimeException("只有待发货订单可以发货，当前状态：" + order.getStatus());
        }
        order.setTrackingCompany(trackingCompany);
        order.setTrackingNumber(trackingNumber);
        order.setDeliveryTime(LocalDateTime.now());
        order.setStatus(3);
        orderMapper.update(order);
        log.info("订单发货成功 orderId={}, company={}, number={}", id, trackingCompany, trackingNumber);
    }

    @Transactional
    @Override
    public void handlePayCallback(Long orderId, Long paymentId, String tradeNo, LocalDateTime payTime) {
        // 更新支付记录
        paymentService.updatePaySuccess(paymentId, tradeNo, payTime);
        // 更新订单状态（同一个事务中）
        Orders order = orderMapper.getById(orderId);
        if (order != null) {
            order.setPayStatus(1);
            order.setCheckoutTime(payTime);
            order.setStatus(2);
            orderMapper.update(order);
            log.info("支付宝回调处理完成 orderId={}, tradeNo={}", orderId, tradeNo);
        } else {
            log.warn("支付宝回调订单不存在 orderId={}", orderId);
        }
    }
}
