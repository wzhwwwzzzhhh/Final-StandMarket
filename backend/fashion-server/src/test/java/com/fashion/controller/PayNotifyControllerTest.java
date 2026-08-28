package com.fashion.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.fashion.config.AlipayConfig;
import com.fashion.controller.notify.PayNotifyController;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B1 支付宝异步通知")
class PayNotifyControllerTest {

    private PayNotifyController controller;
    private PaymentService paymentService;
    private OrderService orderService;
    private String privateKey;
    private String publicKey;
    private String appId;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        appId = "2026082800000001";
        AlipayConfig config = new AlipayConfig();
        config.setAppId(appId);
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        config.setAlipayPublicKey(publicKey);

        paymentService = mock(PaymentService.class);
        orderService = mock(OrderService.class);
        controller = new PayNotifyController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "alipayConfig", config);
    }

    @Test
    @DisplayName("TRADE_FINISHED 与 TRADE_SUCCESS 一样触发可信迁移")
    void tradeFinishedIsProcessedAsSuccess() throws Exception {
        Payment payment = payment(10L, 100L, "10.00", 0);
        Orders order = order(100L, "10.00", 1, 0);
        when(paymentService.getPaymentStatus("PAY-100")).thenReturn(payment);
        when(orderService.getById(100L)).thenReturn(order);

        String response = controller.paySuccess(signedRequest("TRADE_FINISHED", "10.00"));

        assertEquals("success", response);
        verify(orderService).handlePayCallback(eq(100L), eq(10L), eq("TRADE-100"), any());
    }

    @Test
    @DisplayName("支付记录金额与订单持久化金额不一致时拒绝通知")
    void persistedOrderAmountMustMatchPayment() throws Exception {
        Payment payment = payment(10L, 100L, "10.00", 0);
        Orders order = order(100L, "11.00", 1, 0);
        when(paymentService.getPaymentStatus("PAY-100")).thenReturn(payment);
        when(orderService.getById(100L)).thenReturn(order);

        String response = controller.paySuccess(signedRequest("TRADE_SUCCESS", "10.00"));

        assertEquals("failure", response);
        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("其他已验签交易状态确认收到但不写库")
    void nonSuccessStatusIsAcknowledgedWithoutMutation() throws Exception {
        String response = controller.paySuccess(signedRequest("WAIT_BUYER_PAY", "10.00"));

        assertEquals("success", response);
        verify(paymentService, never()).getPaymentStatus(anyString());
        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("回调事务异常时返回 failure 以便支付宝重试")
    void transactionFailureReturnsFailure() throws Exception {
        Payment payment = payment(10L, 100L, "10.00", 0);
        Orders order = order(100L, "10.00", 1, 0);
        when(paymentService.getPaymentStatus("PAY-100")).thenReturn(payment);
        when(orderService.getById(100L)).thenReturn(order);
        doThrow(new IllegalStateException("rollback"))
                .when(orderService).handlePayCallback(anyLong(), anyLong(), anyString(), any());

        String response = assertDoesNotThrow(
                () -> controller.paySuccess(signedRequest("TRADE_SUCCESS", "10.00")));

        assertEquals("failure", response);
    }

    @Test
    @DisplayName("验签失败时返回 failure 且不查询支付记录")
    void invalidSignatureIsRejectedWithoutDatabaseAccess() throws Exception {
        MockHttpServletRequest request = signedRequest("TRADE_SUCCESS", "10.00");
        request.setParameter("total_amount", "0.01");

        assertEquals("failure", controller.paySuccess(request));

        verify(paymentService, never()).getPaymentStatus(anyString());
    }

    @Test
    @DisplayName("已验签但 app_id 不匹配时返回 failure")
    void mismatchedAppIdIsRejected() throws Exception {
        AlipayConfig mismatched = new AlipayConfig();
        mismatched.setAppId("another-app");
        mismatched.setAlipayPublicKey(publicKey);
        ReflectionTestUtils.setField(controller, "alipayConfig", mismatched);

        assertEquals("failure", controller.paySuccess(signedRequest("TRADE_SUCCESS", "10.00")));

        verify(paymentService, never()).getPaymentStatus(anyString());
    }

    @Test
    @DisplayName("回调金额与支付记录不一致时返回 failure")
    void callbackAmountMustMatchPayment() throws Exception {
        when(paymentService.getPaymentStatus("PAY-100"))
                .thenReturn(payment(10L, 100L, "10.00", 0));

        assertEquals("failure", controller.paySuccess(signedRequest("TRADE_SUCCESS", "9.99")));

        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("秒杀支付记录不能进入普通订单回调事务")
    void wrongOrderTypeIsRejected() throws Exception {
        when(paymentService.getPaymentStatus("PAY-100"))
                .thenReturn(payment(10L, 100L, "10.00", 1));

        assertEquals("failure", controller.paySuccess(signedRequest("TRADE_SUCCESS", "10.00")));

        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("支付记录与订单关联不一致时返回 failure")
    void paymentOrderAssociationMustMatch() throws Exception {
        when(paymentService.getPaymentStatus("PAY-100"))
                .thenReturn(payment(10L, 100L, "10.00", 0));
        when(orderService.getById(100L)).thenReturn(order(101L, "10.00", 1, 0));

        assertEquals("failure", controller.paySuccess(signedRequest("TRADE_SUCCESS", "10.00")));

        verify(orderService, never()).handlePayCallback(anyLong(), anyLong(), anyString(), any());
    }

    private MockHttpServletRequest signedRequest(String tradeStatus, String amount)
            throws AlipayApiException {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", appId);
        params.put("out_trade_no", "PAY-100");
        params.put("trade_no", "TRADE-100");
        params.put("trade_status", tradeStatus);
        params.put("total_amount", amount);
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        String signContent = AlipaySignature.getSignCheckContentV1(params);
        params.put("sign", AlipaySignature.rsaSign(signContent, privateKey, "UTF-8", "RSA2"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        params.forEach(request::setParameter);
        return request;
    }

    private static Payment payment(Long id, Long orderId, String amount, Integer orderType) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setPayNo("PAY-100");
        payment.setAmount(new BigDecimal(amount));
        payment.setOrderType(orderType);
        payment.setStatus(0);
        return payment;
    }

    private static Orders order(Long id, String amount, Integer status, Integer payStatus) {
        Orders order = new Orders();
        order.setId(id);
        order.setUserId(7L);
        order.setAmount(new BigDecimal(amount));
        order.setStatus(status);
        order.setPayStatus(payStatus);
        return order;
    }
}
