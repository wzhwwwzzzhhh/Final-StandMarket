package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderAmountCalculateDTO;
import com.fashion.dto.UserCouponDto;
import com.fashion.entity.PageResult;
import com.fashion.entity.SeckillActivity;
import com.fashion.entity.SeckillCoupon;
import com.fashion.entity.SeckillOrder;
import com.fashion.entity.User;
import com.fashion.mapper.SeckillActivityMapper;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.UserMapper;
import com.fashion.result.Result;
import com.fashion.service.SeckillOrderService;
import com.fashion.vo.OrderAmountVO;
import com.fashion.vo.SeckillOrderStatisticsVO;
import com.fashion.vo.SeckillOrderVo;
import com.fasterxml.jackson.databind.util.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SeckillOrderServiceImpl implements SeckillOrderService {

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SeckillCouponMapper seckillCouponMapper;
    @Autowired
    private SeckillActivityMapper seckillActivityMapper;


    @Override
    public SeckillOrder getOrderByNumber(String orderNumber) {
        try {
            return seckillOrderMapper.selectByOrderNumber(orderNumber);
        } catch (Exception e) {
            log.error("查询秒杀订单失败，订单号：{}", orderNumber, e);
            return null;
        }
    }

    @Override
    public List<SeckillOrder> getOrdersByUserId(Long userId) {
        try {
            return seckillOrderMapper.selectByUserId(userId);
        } catch (Exception e) {
            log.error("查询用户{}的秒杀订单失败", userId, e);
            return null;
        }
    }

    @Override
    public List<UserCouponDto> getUserCoupons(Long userId, Integer status) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("status", status);
            
            List<UserCouponDto> coupons = seckillOrderMapper.selectUserCoupons(params);
            
            // 计算优惠券状态
            LocalDateTime now = LocalDateTime.now();
            for (UserCouponDto coupon : coupons) {
                if (coupon.getStatus() == 2) {
                    // 已支付
                    coupon.setCouponStatus(2);
                    coupon.setCouponStatusText("已使用");
                } else if (coupon.getStatus() == 3) {
                    // 已取消
                    coupon.setCouponStatus(3);
                    coupon.setCouponStatusText("已过期");
                } else if (now.isAfter(coupon.getEndTime())) {
                    // 已过期
                    coupon.setCouponStatus(3);
                    coupon.setCouponStatusText("已过期");
                } else {
                    // 可用
                    coupon.setCouponStatus(1);
                    coupon.setCouponStatusText("可用");
                }
            }
            
            return coupons;
        } catch (Exception e) {
            log.error("查询用户{}的优惠券列表失败", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public boolean updateOrderStatus(String orderNumber, Integer status) {
        try {
            SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
            if (order == null) {
                log.warn("订单不存在，订单号：{}", orderNumber);
                return false;
            }
            
            seckillOrderMapper.updateStatus(orderNumber, status);
            log.info("更新订单状态成功，订单号：{}，新状态：{}", orderNumber, status);
            return true;
            
        } catch (Exception e) {
            log.error("更新订单状态失败，订单号：{}", orderNumber, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean updatePayTime(String orderNumber, LocalDateTime payTime) {
        try {
            SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
            if (order == null) {
                log.warn("订单不存在，订单号：{}", orderNumber);
                return false;
            }
            
            seckillOrderMapper.updatePayTime(orderNumber, payTime);
            log.info("更新支付时间成功，订单号：{}，支付时间：{}", orderNumber, payTime);
            return true;
            
        } catch (Exception e) {
            log.error("更新支付时间失败，订单号：{}", orderNumber, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean cancelOrder(String orderNumber) {
        try {
            SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
            if (order == null) {
                log.warn("订单不存在，订单号：{}", orderNumber);
                return false;
            }
            
            // 只能取消待支付的订单
            if (order.getStatus() != 1) {
                log.warn("订单{}状态不是待支付，不能取消", orderNumber);
                return false;
            }
            
            seckillOrderMapper.updateStatus(orderNumber, 3); // 已取消
            log.info("取消订单成功，订单号：{}", orderNumber);
            return true;
            
        } catch (Exception e) {
            log.error("取消订单失败，订单号：{}", orderNumber, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean completePayment(String orderNumber) {
        try {
            SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
            if (order == null) {
                log.warn("订单不存在，订单号：{}", orderNumber);
                return false;
            }
            
            // 只能支付待支付的订单
            if (order.getStatus() != 1) {
                log.warn("订单{}状态不是待支付，不能完成支付", orderNumber);
                return false;
            }
            
            LocalDateTime payTime = LocalDateTime.now();
            seckillOrderMapper.updatePayTime(orderNumber, payTime);
            log.info("完成订单支付成功，订单号：{}，支付时间：{}", orderNumber, payTime);
            return true;
            
        } catch (Exception e) {
            log.error("完成订单支付失败，订单号：{}", orderNumber, e);
            return false;
        }
    }

    @Override
    public PageResult getSeckillOrderPage(int page, int pageSize, String search, Integer status, Long activityId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            // 计算偏移量
            int offset = (page - 1) * pageSize;
            
            // 构建查询参数
            Map<String, Object> params = new HashMap<>();
            params.put("offset", offset);
            params.put("pageSize", pageSize);
            params.put("search", search);
            params.put("status", status);
            params.put("activityId", activityId);
            params.put("startTime", startTime);
            params.put("endTime", endTime);
            
            // 查询数据列表
            List<SeckillOrder> orders = seckillOrderMapper.selectSeckillOrderPage(params);
            
            // 查询总数
            Long total = seckillOrderMapper.countSeckillOrderPage(params);
            
            // 构建分页结果
            PageResult pageResult = new PageResult();
            pageResult.setTotal(total);
            pageResult.setRecords(orders);
            
            return pageResult;
            
        } catch (Exception e) {
            log.error("分页查询秒杀订单失败", e);
            return new PageResult();
        }
    }

    @Override
    @Transactional
    public boolean confirmPayment(String orderNumber) {
        try {
            SeckillOrder order = seckillOrderMapper.selectByOrderNumber(orderNumber);
            if (order == null) {
                log.warn("订单不存在，订单号：{}", orderNumber);
                return false;
            }
            
            // 只能确认待支付的订单
            if (order.getStatus() != 1) {
                log.warn("订单{}状态不是待支付，不能确认支付", orderNumber);
                return false;
            }
            
            // 更新状态为已支付
            seckillOrderMapper.updateStatus(orderNumber, 2);
            
            // 更新支付时间为当前时间
            seckillOrderMapper.updatePayTime(orderNumber, LocalDateTime.now());
            
            log.info("确认订单支付成功，订单号：{}", orderNumber);
            return true;
            
        } catch (Exception e) {
            log.error("确认订单支付失败，订单号：{}", orderNumber, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteOrder(Long id) {
        try {
            SeckillOrder order = seckillOrderMapper.selectById(id);
            if (order == null) {
                log.warn("订单不存在，订单ID：{}", id);
                return false;
            }
            
            // 只能删除已取消的订单
            if (order.getStatus() != 3) {
                log.warn("订单{}状态不是已取消，不能删除", id);
                return false;
            }
            
            seckillOrderMapper.deleteById(id);
            log.info("删除订单成功，订单ID：{}", id);
            return true;
            
        } catch (Exception e) {
            log.error("删除订单失败，订单ID：{}", id, e);
            return false;
        }
    }

    @Override
    public SeckillOrderStatisticsVO getSeckillOrderStatistics() {
        try {
            SeckillOrderStatisticsVO statistics = new SeckillOrderStatisticsVO();
            
            // 获取总订单数
            Long totalOrders = seckillOrderMapper.countAllSeckillOrders();
            statistics.setTotalOrders(totalOrders);
            
            // 获取待支付订单数
            Long pendingOrders = seckillOrderMapper.countSeckillOrdersByStatus(1);
            statistics.setPendingOrdersCount(pendingOrders);
            
            // 获取已支付订单数
            Long paidOrders = seckillOrderMapper.countSeckillOrdersByStatus(2);
            statistics.setPaidOrdersCount(paidOrders);
            
            // 获取已取消订单数
            Long canceledOrders = seckillOrderMapper.countSeckillOrdersByStatus(3);
            statistics.setCanceledOrdersCount(canceledOrders);
            
            // 获取总销售额
            BigDecimal totalAmount = seckillOrderMapper.getTotalSeckillSalesAmount();
            statistics.setTotalAmount(totalAmount);
            
            // 获取今日销售额
            BigDecimal todayAmount = seckillOrderMapper.getTodaySeckillSalesAmount();
            statistics.setTodayAmount(todayAmount);
            
            // 设置默认值
            statistics.setAvgResponseTime(156);
            statistics.setSuccessOrdersCount(paidOrders);
            statistics.setFailedOrdersCount(canceledOrders);
            
            // 计算成功率
            if (totalOrders > 0) {
                BigDecimal successRate = BigDecimal.valueOf(paidOrders)
                        .divide(BigDecimal.valueOf(totalOrders), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                statistics.setSuccessRate(successRate);
            } else {
                statistics.setSuccessRate(BigDecimal.ZERO);
            }
            
            // 计算平均订单金额
            if (paidOrders > 0 && totalAmount != null) {
                BigDecimal avgOrderAmount = totalAmount.divide(BigDecimal.valueOf(paidOrders), 2, BigDecimal.ROUND_HALF_UP);
                statistics.setAvgOrderAmount(avgOrderAmount);
            } else {
                statistics.setAvgOrderAmount(BigDecimal.ZERO);
            }
            
            return statistics;
            
        } catch (Exception e) {
            log.error("获取秒杀订单统计信息失败", e);
            return null;
        }
    }

    @Override
    public Long getTotalSeckillOrderCount() {
        try {
            return seckillOrderMapper.countAllSeckillOrders();
        } catch (Exception e) {
            log.error("获取秒杀订单总数失败", e);
            return 0L;
        }
    }

    @Override
    public Long getPendingOrderCount() {
        try {
            return seckillOrderMapper.countSeckillOrdersByStatus(1);
        } catch (Exception e) {
            log.error("获取待支付订单数失败", e);
            return 0L;
        }
    }

    @Override
    public Double getTotalSalesAmount() {
        try {
            BigDecimal amount = seckillOrderMapper.getTotalSeckillSalesAmount();
            return amount != null ? amount.doubleValue() : 0.0;
        } catch (Exception e) {
            log.error("获取总销售额失败", e);
            return 0.0;
        }
    }

    @Override
    public Result<List<SeckillOrderVo>> listAll() {
        Long userId = BaseContext.getUserId();
        List<SeckillOrderVo> orders = seckillOrderMapper.selectListByUserId(userId);
        List<SeckillOrderVo> voList = new ArrayList<>();
        User user = userMapper.selectById(userId);

        for (SeckillOrderVo order : orders) {
            SeckillCoupon coupon = seckillCouponMapper.selectById(order.getCouponId());
            SeckillOrderVo vo = new SeckillOrderVo();
            BeanUtils.copyProperties(order, vo);
            vo.setUserName(user.getName());
            vo.setUserPhone(user.getPhone());
            vo.setCouponName(coupon.getName());
            vo.setOriginalPrice(coupon.getOriginalPrice());
            vo.setSeckillPrice(coupon.getSeckillPrice());
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<OrderAmountVO> calculateOrderAmount(OrderAmountCalculateDTO calculateDTO) {
        try {
            OrderAmountVO amountVO = new OrderAmountVO();
            BigDecimal totalAmount = calculateDTO.getTotalAmount();

            if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.error("商品总金额无效");
            }

            amountVO.setTotalAmount(totalAmount);
            BigDecimal activityDiscount = BigDecimal.ZERO;
            BigDecimal couponDiscount = BigDecimal.ZERO;

            BigDecimal currentAmount = totalAmount; // 当前金额（用于叠加计算）

            if (calculateDTO.getActivityId() != null && calculateDTO.getActivityId() > 0) {
                SeckillActivity activity = seckillActivityMapper.selectById(calculateDTO.getActivityId());
                if (activity != null) {
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isAfter(activity.getStartTime()) && now.isBefore(activity.getEndTime())) {
                        BigDecimal discountRate = activity.getDiscount();
                        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0 ||
                            discountRate.compareTo(new BigDecimal("10")) > 0) {
                            discountRate = new BigDecimal("10"); // 默认不打折
                        }
                        activityDiscount = currentAmount.multiply(
                                BigDecimal.ONE.subtract(discountRate.divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP)))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        amountVO.setActivityName(activity.getName());
                        amountVO.setActivityDiscountText(discountRate + "折优惠");
                        currentAmount = currentAmount.subtract(activityDiscount); // 更新当前金额
                    }
                }
            }

            if (calculateDTO.getCouponId() != null && calculateDTO.getCouponId() > 0) {
                SeckillCoupon coupon = seckillCouponMapper.selectById(calculateDTO.getCouponId());
                if (coupon != null && coupon.getStatus() == 1) {
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isAfter(coupon.getStartTime()) && now.isBefore(coupon.getEndTime())) {
                        BigDecimal originalPrice = BigDecimal.valueOf(coupon.getOriginalPrice());
                        BigDecimal seckillPrice = BigDecimal.valueOf(coupon.getSeckillPrice());
                        couponDiscount = originalPrice.subtract(seckillPrice)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        amountVO.setCouponName(coupon.getName());
                        amountVO.setCouponDiscountText(
                                String.format("¥%.2f - ¥%.2f", coupon.getOriginalPrice(), coupon.getSeckillPrice()));
                    }
                }
            }

            amountVO.setActivityDiscount(activityDiscount);
            amountVO.setCouponDiscount(couponDiscount);

            BigDecimal finalAmount = totalAmount.subtract(activityDiscount).subtract(couponDiscount);
            amountVO.setFinalAmount(finalAmount.max(BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP));

            log.info("订单金额计算完成：总金额={}, 活动优惠={}, 券优惠={}, 实付={}",
                    totalAmount, activityDiscount, couponDiscount, amountVO.getFinalAmount());

            return Result.success(amountVO);

        } catch (Exception e) {
            log.error("计算订单金额失败", e);
            return Result.error("计算订单金额失败：" + e.getMessage());
        }
    }
}