package com.fashion.service.impl;

import com.fashion.mapper.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B2 有券订单严格变更合约")
class CouponStrictMutationTest {

    private CouponServiceImpl service;
    private UserCouponMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = mock(UserCouponMapper.class);
        service = new CouponServiceImpl();
        ReflectionTestUtils.setField(service, "userCouponMapper", mapper);
    }

    @Test
    @DisplayName("券绑定零影响行必须失败")
    void bindMustAffectExactlyOneRow() {
        when(mapper.setUseOrderId(55L, 7L, 100L)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.bindUseOrder(7L, 55L, 100L));
    }

    @Test
    @DisplayName("券核销零影响行必须失败")
    void useMustAffectExactlyOneRow() {
        when(mapper.useCoupon(100L, 7L)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.markUsed(7L, 100L));
    }

    @Test
    @DisplayName("券释放零影响行必须失败")
    void releaseMustAffectExactlyOneRow() {
        when(mapper.releaseCoupon(100L, 7L)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.release(7L, 100L));
    }
}
