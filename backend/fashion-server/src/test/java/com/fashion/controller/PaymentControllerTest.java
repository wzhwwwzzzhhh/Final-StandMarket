package com.fashion.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.fashion.config.AlipayConfig;
import com.fashion.context.BaseContext;
import com.fashion.controller.user.PaymentController;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 支付查询与同步回跳只读契约")
class PaymentControllerTest {

    private PaymentController controller;
    private PaymentService paymentService;
    private OrderService orderService;
    private String privateKey;
    private String appId;

    @BeforeEach
    void setUp() throws Exception {
        BaseContext.setUserId(7L);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        appId = "2026082800000001";
        AlipayConfig config = new AlipayConfig();
        config.setAppId(appId);
        config.setAlipayPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        paymentService = mock(PaymentService.class);
        orderService = mock(OrderService.class);
        controller = new PaymentController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "alipayConfig", config);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("支付状态只查询本人普通订单的 order_type=0 流水")
    void statusQueryIsOwnerScopedAndUsesNormalOrderType() {
        when(orderService.getById(100L)).thenReturn(order(100L, 7L));
        when(paymentService.getByOrderId(100L, 0)).thenReturn(payment(10L, 100L));

        Result<Map<String, Object>> result = controller.payStatus(100L);

        assertEquals(1, result.getCode());
        assertEquals(0, result.getData().get("payStatus"));
        verify(paymentService).getByOrderId(100L, 0);
    }

    @Test
    @DisplayName("他人订单支付状态不泄露且不查询支付流水")
    void statusQueryRejectsOtherUsersOrder() {
        when(orderService.getById(100L)).thenReturn(order(100L, 8L));

        Result<Map<String, Object>> result = controller.payStatus(100L);

        assertEquals(0, result.getCode());
        verify(paymentService, never()).getByOrderId(anyLong(), any());
    }

    @Test
    @DisplayName("已验签同步回跳只返回状态，不执行任何支付迁移")
    void verifiedReturnIsReadOnly() throws Exception {
        when(paymentService.getPaymentStatus("PAY-100")).thenReturn(payment(10L, 100L));
        when(orderService.getById(100L)).thenReturn(order(100L, 7L));

        Result<Map<String, Object>> result = controller.verifyReturn(signedReturn());

        assertEquals(1, result.getCode());
        assertEquals(0, result.getData().get("payStatus"));
        verify(paymentService, never()).updatePaySuccess(anyLong(), any(), any());
        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), any(), any());
    }

    private Map<String, String> signedReturn() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", appId);
        params.put("out_trade_no", "PAY-100");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        String content = AlipaySignature.getSignCheckContentV1(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2"));
        return params;
    }

    private static Orders order(Long id, Long userId) {
        Orders order = new Orders();
        order.setId(id);
        order.setUserId(userId);
        return order;
    }

    private static Payment payment(Long id, Long orderId) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setOrderType(0);
        payment.setPayNo("PAY-100");
        payment.setStatus(0);
        return payment;
    }
}
