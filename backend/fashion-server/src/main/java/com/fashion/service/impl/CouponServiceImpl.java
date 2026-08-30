package com.fashion.service.impl;

import com.fashion.entity.CouponTemplate;
import com.fashion.entity.PageResult;
import com.fashion.entity.Product;
import com.fashion.entity.UserCoupon;
import com.fashion.mapper.CouponTemplateMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.UserCouponMapper;
import com.fashion.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 通用优惠券服务实现类
 */
@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponTemplateMapper couponTemplateMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedissonClient redissonClient;

    // ==================== 管理端 ====================

    @Override
    public void saveTemplate(CouponTemplate template) {
        LocalDateTime now = LocalDateTime.now();
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        template.setCreateTime(now);
        template.setUpdateTime(now);
        couponTemplateMapper.insert(template);
    }

    @Override
    public void updateTemplate(CouponTemplate template) {
        template.setUpdateTime(LocalDateTime.now());
        couponTemplateMapper.update(template);
    }

    @Override
    public void deleteTemplate(Long id) {
        couponTemplateMapper.deleteById(id);
    }

    @Override
    public CouponTemplate getTemplate(Long id) {
        return couponTemplateMapper.selectById(id);
    }

    @Override
    public PageResult<CouponTemplate> pageTemplates(int page, int pageSize, String name, Integer status) {
        int offset = (page - 1) * pageSize;
        List<CouponTemplate> list = couponTemplateMapper.list(offset, pageSize, name, status);
        int total = couponTemplateMapper.count(name, status);
        return new PageResult<>(total, list);
    }

    @Override
    public PageResult<UserCoupon> pageUserCoupons(int page, int pageSize, Integer status, String keyword) {
        int offset = (page - 1) * pageSize;
        List<UserCoupon> list = userCouponMapper.adminPage(offset, pageSize, status, keyword);
        int total = userCouponMapper.adminCount(status, keyword);
        return new PageResult<>(total, list);
    }

    // ==================== 用户端 ====================

    @Override
    public List<CouponTemplate> listClaimableTemplates() {
        return couponTemplateMapper.listClaimable();
    }

    /**
     * 领取优惠券：校验模板状态/有效期/发行总量/每人限领（Redisson 锁串行化保证限量准确）
     */
    @Override
    @Transactional
    public void claim(Long userId, Long templateId) {
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null || template.getStatus() == null || template.getStatus() != 1) {
            throw new RuntimeException("优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getValidType() != null && template.getValidType() == 1) {
            if (template.getStartTime() != null && now.isBefore(template.getStartTime())) {
                throw new RuntimeException("优惠券未开始领取");
            }
            if (template.getEndTime() != null && now.isAfter(template.getEndTime())) {
                throw new RuntimeException("优惠券已过期");
            }
        }

        RLock lock = redissonClient.getLock("coupon:claim:" + templateId);
        try {
            if (!lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                throw new RuntimeException("领取人数过多，请稍后再试");
            }
            // 每人限领
            int perUserLimit = template.getPerUserLimit() == null ? 1 : template.getPerUserLimit();
            int claimed = userCouponMapper.countByUserAndTemplate(userId, templateId);
            if (claimed >= perUserLimit) {
                throw new RuntimeException("已达每人限领数量");
            }
            // 发行总量
            int totalCount = template.getTotalCount() == null ? 0 : template.getTotalCount();
            if (totalCount > 0) {
                int issued = userCouponMapper.countByTemplate(templateId);
                if (issued >= totalCount) {
                    throw new RuntimeException("优惠券已领完");
                }
            }
            // 计算有效期
            LocalDateTime expireTime;
            if (template.getValidType() != null && template.getValidType() == 1) {
                expireTime = template.getEndTime();
            } else {
                int validDays = template.getValidDays() == null ? 7 : template.getValidDays();
                expireTime = now.plusDays(validDays);
            }
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setTemplateId(templateId);
            userCoupon.setStatus(0);
            userCoupon.setObtainTime(now);
            userCoupon.setExpireTime(expireTime);
            userCouponMapper.insert(userCoupon);
            log.info("用户领券成功 userId={}, templateId={}, userCouponId={}", userId, templateId, userCoupon.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("领取优惠券失败");
        } finally {
            // 事务提交/回滚完成后再释放锁，避免并发领取在计数校验时读到未提交数据
            if (lock.isHeldByCurrentThread()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
            }
        }
    }

    @Override
    public List<UserCoupon> listMyCoupons(Long userId, Integer status) {
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        // 懒处理过期
        markExpired();
        return userCouponMapper.listByUserId(userId, status);
    }

    @Override
    public List<UserCoupon> listAvailable(Long userId, BigDecimal totalAmount, List<Long> productIds) {
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        markExpired();
        List<UserCoupon> candidates = userCouponMapper.listUsable(userId);
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        // 商品范围校验所需数据
        Set<Long> orderProductIds = productIds == null ? new HashSet<>() : new HashSet<>(productIds);
        List<Product> products = orderProductIds.isEmpty() ? new java.util.ArrayList<>() : productMapper.selectBatchByIds(new java.util.ArrayList<>(orderProductIds));
        BigDecimal amount = totalAmount == null ? BigDecimal.ZERO : totalAmount;

        return candidates.stream()
                .filter(c -> {
                    BigDecimal threshold = c.getThreshold() == null ? BigDecimal.ZERO : c.getThreshold();
                    return amount.compareTo(threshold) >= 0 && inScope(c, orderProductIds, products);
                })
                .collect(Collectors.toList());
    }

    // ==================== 下单集成 ====================

    @Override
    @Transactional
    public BigDecimal lockAndDiscount(Long userId, Long userCouponId, BigDecimal totalAmount, List<Long> productIds) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new RuntimeException("优惠券不存在");
        }
        if (!userCoupon.getUserId().equals(userId)) {
            throw new RuntimeException("无权使用该优惠券");
        }
        if (userCoupon.getStatus() == null || userCoupon.getStatus() != 0) {
            throw new RuntimeException("优惠券已使用或已失效");
        }
        if (userCoupon.getExpireTime() == null || userCoupon.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("优惠券已过期");
        }

        BigDecimal amount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal threshold = userCoupon.getThreshold() == null ? BigDecimal.ZERO : userCoupon.getThreshold();
        if (amount.compareTo(threshold) < 0) {
            throw new RuntimeException("未达到优惠券使用门槛");
        }

        // 商品范围校验
        Set<Long> orderProductIds = productIds == null ? new HashSet<>() : new HashSet<>(productIds);
        List<Product> products = orderProductIds.isEmpty() ? new java.util.ArrayList<>() : productMapper.selectBatchByIds(new java.util.ArrayList<>(orderProductIds));
        if (!inScope(userCoupon, orderProductIds, products)) {
            throw new RuntimeException("优惠券不适用当前商品");
        }

        // 乐观锁锁定：仅 status=0 可锁，影响行数=0 说明已被并发占用
        int rows = userCouponMapper.lockCoupon(userCouponId, userId);
        if (rows != 1) {
            throw new RuntimeException("优惠券正在被使用，请刷新后重试");
        }
        return calculateDiscount(userCoupon, amount);
    }

    @Override
    @Transactional
    public void bindUseOrder(Long userId, Long userCouponId, Long orderId) {
        if (userCouponId != null && orderId != null) {
            if (userCouponMapper.setUseOrderId(userCouponId, userId, orderId) != 1) {
                throw new IllegalStateException("优惠券绑定订单失败");
            }
        }
    }

    @Override
    @Transactional
    public void markUsed(Long userId, Long orderId) {
        if (orderId == null) {
            return;
        }
        int rows = userCouponMapper.useCoupon(orderId, userId);
        if (rows != 1) {
            throw new IllegalStateException("优惠券核销失败");
        }
        log.info("优惠券核销成功 orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional
    public void release(Long userId, Long orderId) {
        if (orderId == null) {
            return;
        }
        int rows = userCouponMapper.releaseCoupon(orderId, userId);
        if (rows != 1) {
            throw new IllegalStateException("优惠券释放失败");
        }
        log.info("优惠券释放成功 orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional
    public void markExpired() {
        int rows = userCouponMapper.markExpired();
        if (rows > 0) {
            log.info("优惠券批量置过期 {}", rows);
        }
    }

    // ==================== 私有工具 ====================

    /**
     * 计算券抵扣金额（按类型），结果不高于订单金额
     */
    private BigDecimal calculateDiscount(UserCoupon userCoupon, BigDecimal totalAmount) {
        BigDecimal discountValue = userCoupon.getDiscount() == null ? BigDecimal.ZERO : userCoupon.getDiscount();
        BigDecimal result;
        Integer type = userCoupon.getTemplateType();
        if (type != null && type == 2) {
            // 折扣券：discount=8.5 表示 85 折，抵扣 = 金额 * (1 - 折扣/10)
            BigDecimal rate = discountValue.compareTo(BigDecimal.ZERO) <= 0
                    ? new BigDecimal("10")
                    : discountValue.min(new BigDecimal("10"));
            result = totalAmount.multiply(BigDecimal.ONE.subtract(rate.divide(new BigDecimal("10"), 4, BigDecimal.ROUND_HALF_UP)))
                    .setScale(2, BigDecimal.ROUND_HALF_UP);
        } else {
            // 满减/现金券：抵扣 = 券面金额
            result = discountValue;
        }
        return result.min(totalAmount).max(BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 券适用范围校验：0全店 1指定分类（订单全部商品属于该分类） 2指定商品（订单全部商品在指定集合内）
     */
    private boolean inScope(UserCoupon userCoupon, Set<Long> orderProductIds, List<Product> products) {
        if (orderProductIds.isEmpty()) {
            return true;
        }
        Integer scopeType = userCoupon.getScopeType() == null ? 0 : userCoupon.getScopeType();
        if (scopeType == 0) {
            return true;
        }
        if (scopeType == 1) {
            Long categoryId = userCoupon.getApplyCategoryId();
            return products != null && !products.isEmpty()
                    && products.stream().allMatch(p -> categoryId != null && categoryId.equals(p.getCategoryId()));
        }
        if (scopeType == 2) {
            Set<String> applyIds = new HashSet<>();
            if (userCoupon.getApplyProductIds() != null && !userCoupon.getApplyProductIds().isEmpty()) {
                Arrays.stream(userCoupon.getApplyProductIds().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).forEach(applyIds::add);
            }
            return orderProductIds.stream()
                    .allMatch(id -> applyIds.contains(String.valueOf(id)));
        }
        return true;
    }
}
