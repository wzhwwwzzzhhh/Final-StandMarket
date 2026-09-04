package com.fashion.service.support;

import com.fashion.entity.Product;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("B7 优惠券统一计价策略")
class CouponPricingPolicyTest {

    private final CouponPricingPolicy policy = new CouponPricingPolicy();

    @Test
    @DisplayName("折扣券使用服务端原价并按 HALF_UP 保留两位")
    void roundsPercentageDiscountHalfUp() {
        UserCoupon coupon = coupon(2, "8.5", 0);

        BigDecimal discount = policy.calculateDiscount(coupon, new BigDecimal("10.10"),
                Collections.singleton(1L), Collections.singletonList(product(1L, 10L)));

        assertEquals(new BigDecimal("1.52"), discount);
    }

    @Test
    @DisplayName("全店券也必须有非空商品集合")
    void rejectsEmptyOrderForGlobalCoupon() {
        UserCoupon coupon = coupon(1, "10.00", 0);

        assertThrows(BaseException.class, () -> policy.calculateDiscount(
                coupon, new BigDecimal("100.00"), Collections.emptySet(), Collections.emptyList()));
    }

    @Test
    @DisplayName("分类券要求订单全部商品属于目标分类")
    void categoryCouponRequiresEveryProductInScope() {
        UserCoupon coupon = coupon(1, "10.00", 1);
        coupon.setApplyCategoryId(10L);

        assertThrows(BaseException.class, () -> policy.calculateDiscount(coupon, new BigDecimal("100.00"),
                new HashSet<>(Arrays.asList(1L, 2L)),
                Arrays.asList(product(1L, 10L), product(2L, 11L))));
    }

    @Test
    @DisplayName("商品券配置中混杂非法 token 时失败关闭")
    void rejectsMalformedProductScopeConfiguration() {
        UserCoupon coupon = coupon(3, "5.00", 2);
        coupon.setApplyProductIds("1,abc");

        assertThrows(BaseException.class, () -> policy.calculateDiscount(coupon, new BigDecimal("100.00"),
                Collections.singleton(1L), Collections.singletonList(product(1L, 10L))));
    }

    @Test
    @DisplayName("非法折扣率和负门槛均失败关闭")
    void rejectsIllegalNumericDomain() {
        UserCoupon rateCoupon = coupon(2, "10.01", 0);
        assertThrows(BaseException.class, () -> policy.calculateDiscount(rateCoupon, new BigDecimal("100.00"),
                Collections.singleton(1L), Collections.singletonList(product(1L, 10L))));

        UserCoupon thresholdCoupon = coupon(1, "5.00", 0);
        thresholdCoupon.setThreshold(new BigDecimal("-0.01"));
        assertThrows(BaseException.class, () -> policy.calculateDiscount(thresholdCoupon, new BigDecimal("100.00"),
                Collections.singleton(1L), Collections.singletonList(product(1L, 10L))));
    }

    private UserCoupon coupon(int type, String discount, int scopeType) {
        UserCoupon coupon = new UserCoupon();
        coupon.setTemplateType(type);
        coupon.setThreshold(BigDecimal.ZERO);
        coupon.setDiscount(new BigDecimal(discount));
        coupon.setScopeType(scopeType);
        return coupon;
    }

    private Product product(long id, long categoryId) {
        Product product = new Product();
        product.setId(id);
        product.setCategoryId(categoryId);
        return product;
    }
}
