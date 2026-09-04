package com.fashion.service.impl;

import com.fashion.entity.Product;
import com.fashion.entity.ShoppingCart;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.BaseException;
import com.fashion.mapper.CouponTemplateMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.mapper.UserCouponMapper;
import com.fashion.vo.AvailableCouponVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B7 可用券服务端购物车计价")
class CouponAvailabilityServiceTest {

    private CouponServiceImpl service;
    private ShoppingCartMapper shoppingCartMapper;
    private ProductMapper productMapper;
    private UserCouponMapper userCouponMapper;

    @BeforeEach
    void setUp() {
        service = new CouponServiceImpl();
        shoppingCartMapper = mock(ShoppingCartMapper.class);
        productMapper = mock(ProductMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        ReflectionTestUtils.setField(service, "shoppingCartMapper", shoppingCartMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "userCouponMapper", userCouponMapper);
        ReflectionTestUtils.setField(service, "couponTemplateMapper", mock(CouponTemplateMapper.class));
        ReflectionTestUtils.setField(service, "redissonClient", mock(RedissonClient.class));
    }

    @Test
    @DisplayName("预览只按当前用户购物车数量和商品现价计算")
    void calculatesAvailabilityFromServerCartSnapshot() {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(11L);
        cart.setUserId(7L);
        cart.setProductId(100L);
        cart.setNumber(2);
        Product product = new Product();
        product.setId(100L);
        product.setPrice(new BigDecimal("30.00"));
        UserCoupon coupon = new UserCoupon();
        coupon.setId(55L);
        coupon.setTemplateType(1);
        coupon.setThreshold(new BigDecimal("50.00"));
        coupon.setDiscount(new BigDecimal("10.00"));
        coupon.setScopeType(0);
        when(shoppingCartMapper.findByIdsAndUserId(7L, Collections.singletonList(11L)))
                .thenReturn(Collections.singletonList(cart));
        when(productMapper.getById(100L)).thenReturn(product);
        when(userCouponMapper.listUsable(7L)).thenReturn(Collections.singletonList(coupon));

        List<AvailableCouponVO> result = service.listAvailable(7L, Collections.singletonList(11L));

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("10.00"), result.get(0).getDiscountAmount());
    }

    @Test
    @DisplayName("购物车项不能全部匹配当前用户时整体失败")
    void rejectsIncompleteOwnedCartSnapshot() {
        when(shoppingCartMapper.findByIdsAndUserId(7L, Collections.singletonList(11L)))
                .thenReturn(Collections.emptyList());

        assertThrows(BaseException.class,
                () -> service.listAvailable(7L, Collections.singletonList(11L)));
    }
}
