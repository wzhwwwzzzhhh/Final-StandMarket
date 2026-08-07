package com.fashion.service.impl;

import com.fashion.entity.*;
import com.fashion.mapper.*;
import com.fashion.service.CouponService;
import com.fashion.service.PaymentService;
import com.fashion.constant.RedisKey;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    private SeckillActivityMapper seckillActivityMapper;
    @Autowired
    private SeckillCouponMapper seckillCouponMapper;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CouponService couponService;

    @Override
    public List<Orders> selectByCondition(Map<String, Object> params) {
        return orderMapper.selectByCondition(params);
    }

    /**
     * 服务端应用秒杀活动/秒杀券优惠（与结算页计算逻辑一致，且以服务端配置为准）
     */
    private BigDecimal applyDiscount(BigDecimal totalAmount, Long activityId, Long couponId) {
        BigDecimal discount = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        if (activityId != null && activityId > 0) {
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity != null
                    && (now.isAfter(activity.getStartTime()) && now.isBefore(activity.getEndTime()))) {
                BigDecimal discountRate = activity.getDiscount();
                if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0
                        || discountRate.compareTo(new BigDecimal("10")) > 0) {
                    discountRate = new BigDecimal("10");
                }
                BigDecimal activityDiscount = totalAmount.multiply(
                                BigDecimal.ONE.subtract(discountRate.divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP)))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                discount = discount.add(activityDiscount);
            }
        }

        if (couponId != null && couponId > 0) {
            SeckillCoupon coupon = seckillCouponMapper.selectById(couponId);
            if (coupon != null && coupon.getStatus() != null && coupon.getStatus() == 1
                    && (now.isAfter(coupon.getStartTime()) && now.isBefore(coupon.getEndTime()))
                    && coupon.getOriginalPrice() != null && coupon.getSeckillPrice() != null) {
                BigDecimal couponDiscount = BigDecimal.valueOf(coupon.getOriginalPrice())
                        .subtract(BigDecimal.valueOf(coupon.getSeckillPrice()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                discount = discount.add(couponDiscount);
            }
        }
        return discount;
    }

    /**
     * 生成订单号：ORD + 时间戳秒 + Redis按日自增序列
     * Redis INCR 原子自增保证同一秒内也不重复，避免原 System.currentTimeMillis 方案的碰撞
     */
    private String generateOrderNumber() {
        String date = LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = stringRedisTemplate.opsForValue().increment(RedisKey.ORDER_NUMBER_SEQ_KEY + ":" + date);
        if (seq == null) {
            throw new RuntimeException("订单号生成失败");
        }
        long timestamp = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
        String ts = String.valueOf(timestamp);
        // 截断后的 ts 保证拼接结果 ≤ varchar(50)，与现有"ORD1776833285875"格式兼容
        String suffix = ts.length() > 12 ? ts.substring(ts.length() - 12) : ts;
        return "ORD" + suffix + seq;
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
        if (orderCreateDTO == null || orderCreateDTO.getProductIds() == null || orderCreateDTO.getProductIds().isEmpty()) {
            throw new RuntimeException("请选择要结算的商品");
        }

        Orders orders = new Orders();
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setStatus(1);
        orders.setPayStatus(0);
        orders.setPayMethod(orderCreateDTO.getPayMethod() != null ? orderCreateDTO.getPayMethod() : 1);
        orders.setNumber(generateOrderNumber());
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

        // 服务端重算订单金额：基于购物车项，忽略前端传值，防止金额被篡改
        List<OrderDetail> orderDetails = new ArrayList<>();
        List<Long> orderProductIds = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Long cartItemId : orderCreateDTO.getProductIds()) {
            ShoppingCart cartItem = shoppingCartMapper.findById(cartItemId);
            if (cartItem == null) {
                throw new RuntimeException("购物车商品不存在");
            }
            // 校验购物车项归属当前用户
            if (!cartItem.getUserId().equals(userId)) {
                throw new RuntimeException("存在无权操作的商品");
            }
            Product product = productMapper.getById(cartItem.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            if (product.getStock() < cartItem.getNumber()) {
                throw new RuntimeException("商品库存不足：" + product.getName());
            }
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrderId(orders.getId());
            orderDetail.setProductId(cartItem.getProductId());
            orderDetail.setName(cartItem.getName());
            orderDetail.setImage(cartItem.getImage());
            orderDetail.setSkuInfo(cartItem.getSkuInfo());
            orderDetail.setNumber(cartItem.getNumber());
            // 金额以服务端商品价格重算，不使用购物车/前端携带的金额
            BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(cartItem.getNumber()));
            orderDetail.setAmount(itemAmount);
            orderDetails.add(orderDetail);
            totalAmount = totalAmount.add(itemAmount);
            orderProductIds.add(cartItem.getProductId());
        }
        if (orderDetails.isEmpty()) {
            throw new RuntimeException("购物车为空");
        }

        // 秒杀订单（秒杀活动/秒杀券）不可叠加通用优惠券，避免多重优惠；0 视为未选择
        Long userCouponId = orderCreateDTO.getUserCouponId();
        boolean seckillOrder = (orderCreateDTO.getActivityId() != null && orderCreateDTO.getActivityId() > 0)
                || (orderCreateDTO.getCouponId() != null && orderCreateDTO.getCouponId() > 0);
        if (userCouponId != null && userCouponId > 0 && seckillOrder) {
            throw new RuntimeException("秒杀订单不可叠加通用优惠券");
        }

        // 服务端重算订单金额（商品总价），并应用秒杀活动/秒杀券/通用券优惠，忽略前端传值
        BigDecimal discount = applyDiscount(totalAmount, orderCreateDTO.getActivityId(), orderCreateDTO.getCouponId());
        // 通用优惠券：锁券（status=3）并按券计算抵扣，与秒杀优惠互斥（已在上方校验）
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (userCouponId != null && userCouponId > 0) {
            couponDiscount = couponService.lockAndDiscount(userId, userCouponId, totalAmount, orderProductIds);
            discount = discount.add(couponDiscount);
        }
        orders.setAmount(totalAmount.subtract(discount).max(BigDecimal.ZERO));
        orders.setOriginalPrice(totalAmount);
        orders.setSeckillActivityId(orderCreateDTO.getActivityId() != null && orderCreateDTO.getActivityId() > 0 ? orderCreateDTO.getActivityId() : null);
        orders.setSeckillCouponId(orderCreateDTO.getCouponId() != null && orderCreateDTO.getCouponId() > 0 ? orderCreateDTO.getCouponId() : null);
        orders.setUserCouponId(userCouponId != null && userCouponId > 0 ? userCouponId : null);
        orders.setIsSeckill(seckillOrder ? 1 : 0);

        orderMapper.insert(orders);

        // 通用券绑定订单号（幂等核销/释放按订单 id 定位）
        if (userCouponId != null && userCouponId > 0) {
            couponService.bindUseOrder(userId, userCouponId, orders.getId());
        }

        // 回填订单id后批量插入明细
        for (OrderDetail detail : orderDetails) {
            detail.setOrderId(orders.getId());
        }
        orderDetailMapper.batchInsert(orderDetails);

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
        // 订单取消 → 释放已锁定的通用优惠券（幂等）
        couponService.release(order.getUserId(), id);
    }

    @Override
    @Transactional
    public void autoCancelTimeoutCouponOrders() {
        List<Orders> timeoutOrders = orderMapper.selectTimeoutCouponOrders(30);
        if (timeoutOrders == null || timeoutOrders.isEmpty()) {
            return;
        }
        for (Orders order : timeoutOrders) {
            order.setStatus(5);
            order.setCancelReason("超时未支付，系统自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderMapper.update(order);
            couponService.release(order.getUserId(), order.getId());
            log.info("超时未支付订单自动取消并释放券 orderId={}, userId={}", order.getId(), order.getUserId());
        }
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
            // 支付失败：支付记录随事务回滚，订单保持待支付、券保持锁定（绑定本单），
            // 用户可重试支付（成功则核销）或取消订单（释放券），避免同一张券出现两次优惠
            throw new RuntimeException("支付失败，请重试");
        }

        order.setPayStatus(1);
        order.setCheckoutTime(LocalDateTime.now());
        order.setStatus(2);
        orderMapper.update(order);
        // 支付成功 → 核销通用优惠券（幂等）
        couponService.markUsed(userId, id);
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
        couponService.markUsed(order.getUserId(), id);
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
            // 支付成功回调 → 核销通用优惠券（幂等）
            couponService.markUsed(order.getUserId(), orderId);
            log.info("支付宝回调处理完成 orderId={}, tradeNo={}", orderId, tradeNo);
        } else {
            log.warn("支付宝回调订单不存在 orderId={}", orderId);
        }
    }
}
