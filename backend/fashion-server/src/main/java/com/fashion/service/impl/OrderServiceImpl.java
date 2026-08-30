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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CouponService couponService;
    @Autowired
    private OrderCancellationService orderCancellationService;

    @Override
    public List<Orders> selectByCondition(Map<String, Object> params) {
        return orderMapper.selectByCondition(params);
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
    public void updateAdminStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new RuntimeException("订单ID和状态不能为空");
        }
        throw new IllegalStateException("订单状态必须通过支付、发货、确认收货或取消专用流程更新");
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
        if (new HashSet<>(orderCreateDTO.getProductIds()).size() != orderCreateDTO.getProductIds().size()) {
            throw new IllegalArgumentException("购物车项不能重复");
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
        List<ShoppingCart> selectedCartItems = shoppingCartMapper.findByIdsAndUserId(
                userId, orderCreateDTO.getProductIds());
        if (selectedCartItems == null || selectedCartItems.size() != orderCreateDTO.getProductIds().size()) {
            throw new IllegalStateException("购物车商品不存在或无权操作");
        }
        Map<Long, ShoppingCart> cartItemsById = new HashMap<>();
        for (ShoppingCart selectedCartItem : selectedCartItems) {
            if (selectedCartItem == null || selectedCartItem.getId() == null
                    || cartItemsById.put(selectedCartItem.getId(), selectedCartItem) != null) {
                throw new IllegalStateException("购物车快照无效");
            }
        }
        for (Long cartItemId : orderCreateDTO.getProductIds()) {
            ShoppingCart cartItem = cartItemsById.get(cartItemId);
            if (cartItem == null) {
                throw new RuntimeException("购物车商品不存在");
            }
            // 校验购物车项归属当前用户
            if (!Objects.equals(cartItem.getUserId(), userId)) {
                throw new RuntimeException("存在无权操作的商品");
            }
            if (cartItem.getNumber() == null || cartItem.getNumber() <= 0) {
                throw new IllegalArgumentException("购物车商品数量必须大于零");
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

        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (OrderDetail orderDetail : orderDetails) {
            quantityByProductId.merge(orderDetail.getProductId(), orderDetail.getNumber(), Math::addExact);
        }
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            int affectedRows = productMapper.deductStock(entry.getKey(), entry.getValue());
            if (affectedRows != 1) {
                throw new IllegalStateException("商品库存不足，商品ID：" + entry.getKey());
            }
        }

        // 普通订单不信任客户端携带的秒杀活动/券标识，只允许使用服务端校验的通用券。
        Long userCouponId = orderCreateDTO.getUserCouponId();
        BigDecimal discount = BigDecimal.ZERO;
        // 通用优惠券：锁券（status=3）并按券计算抵扣。
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (userCouponId != null && userCouponId > 0) {
            couponDiscount = couponService.lockAndDiscount(userId, userCouponId, totalAmount, orderProductIds);
            discount = discount.add(couponDiscount);
        }
        orders.setAmount(totalAmount.subtract(discount).max(BigDecimal.ZERO));
        orders.setOriginalPrice(totalAmount);
        orders.setSeckillActivityId(null);
        orders.setSeckillCouponId(null);
        orders.setSeckillPrice(null);
        orders.setUserCouponId(userCouponId != null && userCouponId > 0 ? userCouponId : null);
        orders.setIsSeckill(0);
        orders.setStockDeducted(1);

        if (orderMapper.insert(orders) != 1) {
            throw new IllegalStateException("订单写入失败");
        }

        // 通用券绑定订单号（幂等核销/释放按订单 id 定位）
        if (userCouponId != null && userCouponId > 0) {
            couponService.bindUseOrder(userId, userCouponId, orders.getId());
        }

        // 回填订单id后批量插入明细
        for (OrderDetail detail : orderDetails) {
            detail.setOrderId(orders.getId());
        }
        if (orderDetailMapper.batchInsert(orderDetails) != orderDetails.size()) {
            throw new IllegalStateException("订单明细写入不完整");
        }

        return orders;
    }

    @Override
    public List<Orders> listUserOrders(Integer status) {
        Long userId = requireCurrentUserId();
        List<Orders> orders = orderMapper.listUserOrders(userId, status);
        for (Orders order : orders) {
            List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
            order.setItems(orderDetails);
        }
        return orders;
    }

    @Override
    public void cancel(Long id) {
        Long userId = requireCurrentUserId();
        orderCancellationService.cancelForUser(id, userId);
    }

    @Override
    public void autoCancelTimeoutOrders() {
        final int batchSize = 100;
        long afterId = 0L;
        while (true) {
            List<Orders> timeoutOrders = orderMapper.selectTimeoutOrders(30, afterId, batchSize);
            if (timeoutOrders == null || timeoutOrders.isEmpty()) {
                return;
            }
            for (Orders order : timeoutOrders) {
                afterId = order.getId();
                try {
                    if (orderCancellationService.cancelTimeout(order.getId())) {
                        log.info("超时未支付普通订单自动取消 orderId={}, userId={}", order.getId(), order.getUserId());
                    }
                } catch (RuntimeException failure) {
                    log.error("超时订单取消失败，保留待重试 orderId={}", order.getId(), failure);
                }
            }
            if (timeoutOrders.size() < batchSize) {
                return;
            }
        }
    }

    @Transactional
    @Override
    public void confirm(Long id) {
        Long userId = requireCurrentUserId();
        if (orderMapper.confirmDeliveredOrder(id, userId, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("订单不存在、无权操作或当前状态不能确认收货");
        }
    }

    @Transactional
    @Override
    public void deliver(Long id, String trackingCompany, String trackingNumber) {
        if (orderMapper.deliverPaidOrder(id, trackingCompany, trackingNumber, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("订单不存在或当前状态不能发货");
        }
        log.info("订单发货成功 orderId={}, company={}, number={}", id, trackingCompany, trackingNumber);
    }

    @Transactional
    @Override
    public void handlePayCallback(Long orderId, Long paymentId, String tradeNo, LocalDateTime payTime) {
        // 所有支付路径统一按订单 -> 支付记录的顺序加锁，避免与取消/重复回调交叉写入。
        Orders order = orderMapper.getByIdForUpdate(orderId);
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        validatePaymentOwnership(orderId, paymentId, order, payment);

        if (payment.getStatus() != null && payment.getStatus() == 2) {
            if (!Objects.equals(payment.getTradeNo(), tradeNo)) {
                throw new IllegalStateException("支付流水号与已确认记录不一致");
            }
            if (!isAcknowledgedPaidOrder(order)) {
                throw new IllegalStateException("支付记录与订单状态不一致");
            }
            log.info("支付宝重复回调幂等返回 orderId={}, tradeNo={}", orderId, tradeNo);
            return;
        }

        if (!Objects.equals(order.getStockDeducted(), 1)) {
            throw new IllegalStateException("订单库存尚未成功扣减");
        }

        if (payment.getStatus() == null || (payment.getStatus() != 0 && payment.getStatus() != 1)) {
            throw new IllegalStateException("支付记录当前状态不允许确认成功");
        }
        if (order.getStatus() == null || order.getStatus() != 1
                || order.getPayStatus() == null || order.getPayStatus() != 0) {
            throw new IllegalStateException("订单当前状态不允许确认支付");
        }

        if (!paymentService.updatePaySuccess(paymentId, tradeNo, payTime)) {
            throw new IllegalStateException("支付记录状态已变化");
        }
        int rows = orderMapper.markPaid(orderId, payTime);
        if (rows == 0) {
            throw new IllegalStateException("订单状态已变化，无法确认支付");
        }
        if (order.getUserCouponId() != null) {
            couponService.markUsed(order.getUserId(), orderId);
        }
        log.info("支付宝回调处理完成 orderId={}, tradeNo={}", orderId, tradeNo);
    }

    private void validatePaymentOwnership(Long orderId, Long paymentId, Orders order, Payment payment) {
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }
        if (payment == null) {
            throw new IllegalStateException("支付记录不存在");
        }
        if (!Objects.equals(order.getId(), orderId)
                || !Objects.equals(payment.getId(), paymentId)
                || !Objects.equals(payment.getOrderId(), orderId)
                || payment.getOrderType() == null || payment.getOrderType() != 0) {
            throw new IllegalStateException("支付记录与订单不匹配");
        }
        if (order.getAmount() == null || payment.getAmount() == null
                || order.getAmount().compareTo(payment.getAmount()) != 0) {
            throw new IllegalStateException("支付记录与订单金额不一致");
        }
    }

    private boolean isAcknowledgedPaidOrder(Orders order) {
        if (order.getStatus() == null || order.getPayStatus() == null) {
            return false;
        }
        if (order.getPayStatus() == 1) {
            return order.getStatus() == 2 || order.getStatus() == 3
                    || order.getStatus() == 4 || order.getStatus() == 6;
        }
        return order.getPayStatus() == 2 && order.getStatus() == 6;
    }

    private Long requireCurrentUserId() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        return userId;
    }
}
