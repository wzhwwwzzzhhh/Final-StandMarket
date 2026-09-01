package com.fashion.service.impl;

import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B5 秒杀支付 CAS")
class SeckillOrderServiceImplB5PaymentTest {

    private SeckillOrderServiceImpl service;
    private SeckillOrderMapper mapper;

    @BeforeEach
    void setUp() {
        service = new SeckillOrderServiceImpl();
        mapper = mock(SeckillOrderMapper.class);
        ReflectionTestUtils.setField(service, "seckillOrderMapper", mapper);
    }

    @Test
    @DisplayName("待支付 CAS 影响一行时支付成功且不再单独更新时间")
    void successfulPaymentUsesOneAtomicWrite() {
        when(mapper.markPaid(eq("SEC-1"), any())).thenReturn(1);

        assertTrue(service.confirmPayment("SEC-1"));

        verify(mapper).markPaid(eq("SEC-1"), any());
        verify(mapper, never()).updatePayTime(eq("SEC-1"), any());
    }

    @Test
    @DisplayName("待支付 CAS 零行时支付失败且没有后续写入")
    void lostPaymentRaceReturnsFalse() {
        when(mapper.markPaid(eq("SEC-2"), any())).thenReturn(0);

        assertFalse(service.confirmPayment("SEC-2"));

        verify(mapper).markPaid(eq("SEC-2"), any());
        verify(mapper, never()).updatePayTime(eq("SEC-2"), any());
    }
}
