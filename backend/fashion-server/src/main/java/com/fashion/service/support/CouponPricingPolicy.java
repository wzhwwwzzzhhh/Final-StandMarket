package com.fashion.service.support;

import com.fashion.entity.Product;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.PublicBusinessException;
import com.fashion.exception.PublicBusinessException.Code;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fashion.exception.PublicBusinessException.Code.*;
import static com.fashion.exception.PublicBusinessException.of;

/**
 * 预览和订单创建共享的优惠券失败关闭规则与抵扣计算。
 */
public class CouponPricingPolicy {

    private static final BigDecimal TEN = new BigDecimal("10");

    public BigDecimal calculateDiscount(UserCoupon coupon, BigDecimal originalAmount,
                                        Set<Long> orderProductIds, List<Product> products) {
        require(coupon != null, COUPON_UNAVAILABLE);
        require(originalAmount != null && originalAmount.compareTo(BigDecimal.ZERO) >= 0,
                COUPON_ORDER_AMOUNT_INVALID);
        require(orderProductIds != null && !orderProductIds.isEmpty(), ORDER_PRODUCTS_REQUIRED);

        Integer type = coupon.getTemplateType();
        require(type != null && (type == 1 || type == 2 || type == 3), COUPON_TYPE_INVALID);
        BigDecimal threshold = coupon.getThreshold();
        require(threshold != null && threshold.compareTo(BigDecimal.ZERO) >= 0, COUPON_THRESHOLD_INVALID);
        require(originalAmount.compareTo(threshold) >= 0, COUPON_THRESHOLD_NOT_MET);

        BigDecimal configuredDiscount = coupon.getDiscount();
        require(configuredDiscount != null && configuredDiscount.compareTo(BigDecimal.ZERO) > 0,
                COUPON_DISCOUNT_INVALID);
        if (type == 2) {
            require(configuredDiscount.compareTo(TEN) <= 0, COUPON_DISCOUNT_INVALID);
        }

        validateScope(coupon, orderProductIds, products);

        BigDecimal discount = type == 2
                ? originalAmount.multiply(BigDecimal.ONE.subtract(
                        configuredDiscount.divide(TEN, 10, RoundingMode.HALF_UP)))
                : configuredDiscount;
        return discount.max(BigDecimal.ZERO)
                .min(originalAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateScope(UserCoupon coupon, Set<Long> orderProductIds, List<Product> products) {
        Integer scopeType = coupon.getScopeType();
        require(scopeType != null && (scopeType == 0 || scopeType == 1 || scopeType == 2),
                COUPON_SCOPE_INVALID);
        if (scopeType == 0) {
            return;
        }

        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products == null ? Collections.<Product>emptyList() : products) {
            if (product != null && product.getId() != null) {
                productsById.put(product.getId(), product);
            }
        }
        require(productsById.keySet().containsAll(orderProductIds), ORDER_PRODUCT_SNAPSHOT_INCOMPLETE);

        if (scopeType == 1) {
            Long categoryId = coupon.getApplyCategoryId();
            require(categoryId != null && categoryId > 0, COUPON_CATEGORY_SCOPE_INVALID);
            require(orderProductIds.stream()
                    .allMatch(id -> categoryId.equals(productsById.get(id).getCategoryId())),
                    COUPON_NOT_APPLICABLE);
            return;
        }

        Set<Long> configuredProductIds = parseProductScope(coupon.getApplyProductIds());
        require(configuredProductIds.containsAll(orderProductIds), COUPON_NOT_APPLICABLE);
    }

    private Set<Long> parseProductScope(String rawProductIds) {
        require(rawProductIds != null && !rawProductIds.trim().isEmpty(), COUPON_PRODUCT_SCOPE_INVALID);
        Set<Long> result = new HashSet<>();
        try {
            Arrays.stream(rawProductIds.split(",", -1)).forEach(token -> {
                String trimmed = token.trim();
                require(!trimmed.isEmpty(), COUPON_PRODUCT_SCOPE_INVALID);
                long id = Long.parseLong(trimmed);
                require(id > 0, COUPON_PRODUCT_SCOPE_INVALID);
                result.add(id);
            });
        } catch (NumberFormatException e) {
            throw of(COUPON_PRODUCT_SCOPE_INVALID);
        }
        require(!result.isEmpty(), COUPON_PRODUCT_SCOPE_INVALID);
        return result;
    }

    private void require(boolean condition, Code code) {
        if (!condition) {
            throw of(code);
        }
    }
}
