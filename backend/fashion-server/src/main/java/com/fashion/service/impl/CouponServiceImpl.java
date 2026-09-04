package com.fashion.service.impl;

import com.fashion.entity.CouponTemplate;
import com.fashion.entity.PageResult;
import com.fashion.entity.Product;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.PublicBusinessException;
import com.fashion.mapper.CouponTemplateMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.mapper.UserCouponMapper;
import com.fashion.service.CouponService;
import com.fashion.service.support.CartSelectionValidator;
import com.fashion.service.support.CouponPricingPolicy;
import com.fashion.vo.AvailableCouponVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.fashion.exception.PublicBusinessException.Code.*;
import static com.fashion.exception.PublicBusinessException.of;

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
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private RedissonClient redissonClient;

    private final CouponPricingPolicy couponPricingPolicy = new CouponPricingPolicy();

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
        if (userId == null || templateId == null || templateId <= 0) {
            throw of(COUPON_UNAVAILABLE);
        }

        RLock lock = redissonClient.getLock("coupon:claim:" + templateId);
        try {
            // Omit a fixed lease so Redisson's watchdog keeps the lock alive until
            // transaction completion, including slow commits and rollbacks.
            if (!lock.tryLock(2, TimeUnit.SECONDS)) {
                throw of(CLAIM_BUSY);
            }
            CouponTemplate template = couponTemplateMapper.selectByIdForShare(templateId);
            LocalDateTime eligibilityTime = userCouponMapper.selectDatabaseTime();
            validateClaimTemplateAt(template, eligibilityTime);
            // 每人限领
            int perUserLimit = template.getPerUserLimit() == null ? 1 : template.getPerUserLimit();
            int claimed = userCouponMapper.countByUserAndTemplate(userId, templateId);
            if (claimed >= perUserLimit) {
                throw of(CLAIM_LIMIT_REACHED);
            }
            // 发行总量
            int totalCount = template.getTotalCount() == null ? 0 : template.getTotalCount();
            if (totalCount > 0) {
                int issued = userCouponMapper.countByTemplate(templateId);
                if (issued >= totalCount) {
                    throw of(COUPON_SOLD_OUT);
                }
            }
            if (userCouponMapper.insertClaim(userId, templateId, eligibilityTime) != 1) {
                throw of(COUPON_UNAVAILABLE);
            }
            log.info("用户领券成功 userId={}, templateId={}", userId, templateId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw of(CLAIM_FAILED);
        } finally {
            // 事务提交/回滚完成后再释放锁，避免并发领取在计数校验时读到未提交数据
            if (lock.isHeldByCurrentThread() && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
            } else if (lock.isHeldByCurrentThread()) {
                lock.unlock();
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
    public List<AvailableCouponVO> listAvailable(Long userId, List<Long> cartItemIds) {
        if (userId == null) {
            throw of(USER_NOT_LOGGED_IN);
        }
        CartSelectionValidator.validate(cartItemIds);
        List<com.fashion.entity.ShoppingCart> cartItems =
                shoppingCartMapper.findByIdsAndUserId(userId, cartItemIds);
        if (cartItems == null || cartItems.size() != cartItemIds.size()) {
            throw of(CART_FORBIDDEN);
        }
        Map<Long, com.fashion.entity.ShoppingCart> cartsById = new HashMap<>();
        for (com.fashion.entity.ShoppingCart cart : cartItems) {
            if (cart == null || cart.getId() == null || !Objects.equals(userId, cart.getUserId())
                    || cart.getNumber() == null || cart.getNumber() <= 0
                    || cartsById.put(cart.getId(), cart) != null) {
                throw of(CART_SNAPSHOT_INVALID);
            }
        }
        BigDecimal originalAmount = BigDecimal.ZERO;
        Set<Long> orderProductIds = new HashSet<>();
        Map<Long, Product> productsById = new HashMap<>();
        for (Long cartItemId : cartItemIds) {
            com.fashion.entity.ShoppingCart cart = cartsById.get(cartItemId);
            if (cart == null || cart.getProductId() == null) {
                throw of(CART_SNAPSHOT_INVALID);
            }
            Product product = productsById.computeIfAbsent(cart.getProductId(), productMapper::getById);
            if (product == null || product.getPrice() == null
                    || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw of(PRODUCT_PRICE_INVALID);
            }
            originalAmount = originalAmount.add(
                    product.getPrice().multiply(BigDecimal.valueOf(cart.getNumber())));
            orderProductIds.add(product.getId());
        }
        markExpired();
        List<UserCoupon> candidates = userCouponMapper.listUsable(userId);
        if (candidates == null || candidates.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Product> products = new ArrayList<>(productsById.values());
        List<AvailableCouponVO> result = new ArrayList<>();
        for (UserCoupon candidate : candidates) {
            try {
                BigDecimal discount = couponPricingPolicy.calculateDiscount(
                        candidate, originalAmount, orderProductIds, products);
                result.add(toAvailableCoupon(candidate, discount));
            } catch (PublicBusinessException ignored) {
                // 候选列表中的非法/不适用规则失败关闭，不暴露到可用列表。
            }
        }
        return result;
    }

    // ==================== 下单集成 ====================

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal lockAndDiscount(Long userId, Long userCouponId, BigDecimal totalAmount, List<Long> productIds) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        UserCoupon userCoupon = userCouponMapper.selectByIdForUpdate(userCouponId);
        if (userCoupon == null || userId == null || !userId.equals(userCoupon.getUserId())
                || userCoupon.getStatus() == null || userCoupon.getStatus() != 0
                || userCoupon.getUseOrderId() != null || userCoupon.getTemplateId() == null) {
            throw of(COUPON_UNAVAILABLE);
        }
        CouponTemplate template = couponTemplateMapper.selectByIdForShare(userCoupon.getTemplateId());
        LocalDateTime eligibilityTime = userCouponMapper.selectDatabaseTime();
        validateEligibilityAt(userCoupon, template, eligibilityTime);
        applyTemplateSnapshot(userCoupon, template);

        Set<Long> orderProductIds = productIds == null ? new HashSet<>() : new HashSet<>(productIds);
        List<Product> products = requiresProductSnapshot(template)
                ? productMapper.selectBatchByIds(new java.util.ArrayList<>(orderProductIds))
                : java.util.Collections.emptyList();
        BigDecimal discount = couponPricingPolicy.calculateDiscount(
                userCoupon, totalAmount, orderProductIds, products);

        int rows = userCouponMapper.lockCouponAt(
                userCouponId, userId, userCoupon.getTemplateId(), eligibilityTime);
        if (rows != 1) {
            throw of(COUPON_UNAVAILABLE);
        }
        return discount;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
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

    private void validateEligibilityAt(UserCoupon userCoupon, CouponTemplate template,
                                       LocalDateTime eligibilityTime) {
        if (template == null || eligibilityTime == null || template.getStatus() == null
                || template.getStatus() != 1 || userCoupon.getExpireTime() == null
                || !userCoupon.getExpireTime().isAfter(eligibilityTime)) {
            throw of(COUPON_UNAVAILABLE);
        }
        Integer validType = template.getValidType();
        if (validType == null) {
            throw of(COUPON_UNAVAILABLE);
        }
        if (validType == 1) {
            if (template.getStartTime() == null || template.getEndTime() == null
                    || !template.getStartTime().isBefore(template.getEndTime())
                    || eligibilityTime.isBefore(template.getStartTime())
                    || !eligibilityTime.isBefore(template.getEndTime())) {
                throw of(COUPON_UNAVAILABLE);
            }
        } else if (validType == 2) {
            if (template.getValidDays() == null || template.getValidDays() <= 0) {
                throw of(COUPON_UNAVAILABLE);
            }
        } else {
            throw of(COUPON_UNAVAILABLE);
        }
    }

    private void validateClaimTemplateAt(CouponTemplate template, LocalDateTime eligibilityTime) {
        UserCoupon syntheticHolder = new UserCoupon();
        syntheticHolder.setExpireTime(LocalDateTime.MAX);
        validateEligibilityAt(syntheticHolder, template, eligibilityTime);
    }

    private void applyTemplateSnapshot(UserCoupon userCoupon, CouponTemplate template) {
        userCoupon.setTemplateName(template.getName());
        userCoupon.setTemplateType(template.getType());
        userCoupon.setThreshold(template.getThreshold());
        userCoupon.setDiscount(template.getDiscount());
        userCoupon.setScopeType(template.getScopeType());
        userCoupon.setApplyCategoryId(template.getApplyCategoryId());
        userCoupon.setApplyProductIds(template.getApplyProductIds());
    }

    private boolean requiresProductSnapshot(CouponTemplate template) {
        return template != null && template.getScopeType() != null && template.getScopeType() != 0;
    }

    private AvailableCouponVO toAvailableCoupon(UserCoupon coupon, BigDecimal discountAmount) {
        AvailableCouponVO vo = new AvailableCouponVO();
        vo.setId(coupon.getId());
        vo.setTemplateId(coupon.getTemplateId());
        vo.setTemplateName(coupon.getTemplateName());
        vo.setTemplateType(coupon.getTemplateType());
        vo.setThreshold(coupon.getThreshold());
        vo.setDiscount(coupon.getDiscount());
        vo.setScopeType(coupon.getScopeType());
        vo.setExpireTime(coupon.getExpireTime());
        vo.setDiscountAmount(discountAmount);
        return vo;
    }

}
