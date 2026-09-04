package com.fashion.service.impl;

import com.fashion.entity.Product;
import com.fashion.entity.CouponTemplate;
import com.fashion.entity.UserCoupon;
import com.fashion.exception.BaseException;
import com.fashion.mapper.CouponTemplateMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B7 优惠券规则严格校验")
class CouponServiceImplB7Test {

    private CouponServiceImpl service;
    private UserCouponMapper userCouponMapper;
    private ProductMapper productMapper;
    private CouponTemplateMapper couponTemplateMapper;
    private UserCoupon coupon;
    private CouponTemplate template;
    private LocalDateTime databaseTime;

    @BeforeEach
    void setUp() {
        userCouponMapper = mock(UserCouponMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new CouponServiceImpl();
        couponTemplateMapper = mock(CouponTemplateMapper.class);
        ReflectionTestUtils.setField(service, "couponTemplateMapper", couponTemplateMapper);
        ReflectionTestUtils.setField(service, "userCouponMapper", userCouponMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "redissonClient", mock(RedissonClient.class));

        coupon = new UserCoupon();
        coupon.setId(55L);
        coupon.setUserId(7L);
        coupon.setTemplateId(9L);
        coupon.setStatus(0);
        coupon.setExpireTime(LocalDateTime.now().plusDays(1));
        coupon.setThreshold(BigDecimal.ZERO);
        coupon.setDiscount(BigDecimal.ONE);
        coupon.setTemplateType(1);
        coupon.setScopeType(0);
        databaseTime = LocalDateTime.of(2026, 9, 3, 18, 0, 0);
        template = new CouponTemplate();
        template.setId(9L);
        template.setStatus(1);
        template.setType(1);
        template.setThreshold(BigDecimal.ZERO);
        template.setDiscount(BigDecimal.ONE);
        template.setScopeType(0);
        template.setValidType(2);
        template.setValidDays(7);
        when(userCouponMapper.selectById(55L)).thenReturn(coupon);
        when(userCouponMapper.selectByIdForUpdate(55L)).thenReturn(coupon);
        when(couponTemplateMapper.selectByIdForShare(9L)).thenReturn(template);
        when(userCouponMapper.selectDatabaseTime()).thenReturn(databaseTime);
        when(userCouponMapper.lockCouponAt(55L, 7L, 9L, databaseTime)).thenReturn(1);
    }

    @Test
    @DisplayName("未知优惠券类型必须拒绝而不是按满减券处理")
    void rejectsUnknownCouponType() {
        coupon.setTemplateType(99);
        template.setType(99);

        assertThrows(BaseException.class, () -> service.lockAndDiscount(
                7L, 55L, new BigDecimal("100.00"), Collections.singletonList(100L)));
    }

    @Test
    @DisplayName("未知适用范围必须拒绝而不是按全店券处理")
    void rejectsUnknownCouponScope() {
        coupon.setScopeType(99);
        template.setScopeType(99);
        Product product = new Product();
        product.setId(100L);
        when(productMapper.selectBatchByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(product));

        assertThrows(BaseException.class, () -> service.lockAndDiscount(
                7L, 55L, new BigDecimal("100.00"), Collections.singletonList(100L)));
    }

    @Test
    @DisplayName("锁券按持有券排他锁、模板共享锁、数据库时间、最终 CAS 的顺序执行")
    void locksSnapshotsBeforeReadingDatabaseTimeAndCas() {
        BigDecimal discount = service.lockAndDiscount(
                7L, 55L, new BigDecimal("100.00"), Collections.singletonList(100L));

        assertEquals(new BigDecimal("1.00"), discount);
        org.mockito.InOrder order = inOrder(userCouponMapper, couponTemplateMapper);
        order.verify(userCouponMapper).selectByIdForUpdate(55L);
        order.verify(couponTemplateMapper).selectByIdForShare(9L);
        order.verify(userCouponMapper).selectDatabaseTime();
        order.verify(userCouponMapper).lockCouponAt(55L, 7L, 9L, databaseTime);
    }

    @Test
    @DisplayName("模板停用时不能执行持有券 CAS")
    void rejectsDisabledTemplateBeforeCas() {
        template.setStatus(0);

        assertThrows(BaseException.class, () -> service.lockAndDiscount(
                7L, 55L, new BigDecimal("100.00"), Collections.singletonList(100L)));

        verify(userCouponMapper, never()).lockCouponAt(55L, 7L, 9L, databaseTime);
    }

    @Test
    @DisplayName("领券以模板共享锁后的数据库时间写入领取与到期时间")
    void claimUsesDatabaseTimeAfterTemplateLock() throws Exception {
        RLock lock = mock(RLock.class);
        when(((RedissonClient) ReflectionTestUtils.getField(service, "redissonClient"))
                .getLock("coupon:claim:9")).thenReturn(lock);
        when(lock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userCouponMapper.countByUserAndTemplate(7L, 9L)).thenReturn(0);
        when(userCouponMapper.countByTemplate(9L)).thenReturn(0);
        when(userCouponMapper.insertClaim(7L, 9L, databaseTime)).thenReturn(1);

        service.claim(7L, 9L);

        verify(lock).tryLock(2, java.util.concurrent.TimeUnit.SECONDS);
        org.mockito.InOrder order = inOrder(couponTemplateMapper, userCouponMapper);
        order.verify(couponTemplateMapper).selectByIdForShare(9L);
        order.verify(userCouponMapper).selectDatabaseTime();
        order.verify(userCouponMapper).insertClaim(7L, 9L, databaseTime);
    }
}
