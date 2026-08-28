package com.fashion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fashion.mapper.PaymentMapper;

@DisplayName("B1 支付创建服务契约")
class PaymentCreationContractTest {

    @Test
    @DisplayName("普通订单支付创建不再接收订单类型、金额和支付方式")
    void onlyExposesOrderScopedAlipayCreation() {
        assertTrue(Arrays.stream(PaymentService.class.getMethods())
                .anyMatch(method -> method.getName().equals("createAlipayPayment")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{Long.class})));
        assertFalse(Arrays.stream(PaymentService.class.getMethods())
                .anyMatch(method -> method.getName().equals("createPayment")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{
                        Long.class, Integer.class, BigDecimal.class, Integer.class})));
    }

    @Test
    @DisplayName("首次活动流水查询与唯一冲突后的锁定 current-read 使用不同 Mapper 契约")
    void separatesInitialReadFromConflictCurrentRead() {
        assertTrue(Arrays.stream(PaymentMapper.class.getMethods())
                .anyMatch(method -> method.getName().equals("getActiveByOrderIdAndType")));
        assertTrue(Arrays.stream(PaymentMapper.class.getMethods())
                .anyMatch(method -> method.getName().equals("getActiveByOrderIdAndTypeForUpdate")));
    }
}
