package com.fashion.controller.user;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.fashion.config.AlipayConfig;
import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.result.Result;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端支付控制器
 */
@RestController
@RequestMapping("/user/pay")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayConfig alipayConfig;

    @Autowired
    private AlipayClient alipayClient;

    /**
     * 发起支付宝支付，返回支付表单HTML
     */
    @PostMapping("/alipay/{orderId}")
    public Result<Map<String, String>> alipayPay(@PathVariable Long orderId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        try {
            Orders order = orderService.getById(orderId);
            if (order == null) {
                return Result.error("订单不存在");
            }
            if (!order.getUserId().equals(userId)) {
                return Result.error("无权操作该订单");
            }
            if (order.getStatus() != 1) {
                return Result.error("订单状态不是待支付");
            }

            // 创建支付记录
            Payment payment = paymentService.createPayment(
                    order.getId(), 0, order.getAmount(), 2);

            // 构建支付宝跳转请求
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
            String returnUrl = alipayConfig.getReturnUrl();
            String separator = returnUrl.contains("?") ? "&" : "?";
            request.setReturnUrl(returnUrl + separator + "orderId=" + orderId);

            // 业务参数
            String bizContent = "{" +
                    "    \"out_trade_no\":\"" + payment.getPayNo() + "\"," +
                    "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                    "    \"total_amount\":" + order.getAmount() + "," +
                    "    \"subject\":\"末路衣橱-订单" + order.getNumber() + "\"" +
                    "}";
            request.setBizContent(bizContent);

            String form = alipayClient.pageExecute(request).getBody();

            Map<String, String> result = new HashMap<>();
            result.put("orderId", String.valueOf(orderId));
            result.put("payNo", payment.getPayNo());
            result.put("form", form);

            log.info("支付宝支付表单生成成功 orderId={}, payNo={}", orderId, payment.getPayNo());
            return Result.success(result);
        } catch (AlipayApiException e) {
            log.error("支付宝支付调用失败 orderId={}: {}", orderId, e.getMessage(), e);
            return Result.error("支付调用失败");
        }
    }

    /**
     * 查询订单支付状态
     */
    @GetMapping("/status/{orderId}")
    public Result<Map<String, Object>> payStatus(@PathVariable Long orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        Map<String, Object> result = new HashMap<>();
        if (payment != null) {
            result.put("payStatus", payment.getStatus());
            result.put("payNo", payment.getPayNo());
            result.put("payMethod", payment.getPayMethod());
        } else {
            result.put("payStatus", -1);
        }
        return Result.success(result);
    }

    /**
     * 支付宝同步回跳验签
     * 用户从支付宝沙箱跳回时携带支付结果参数，前端提交给本接口验签更新订单
     */
    @PostMapping("/alipay/verify")
    public Result<Map<String, Object>> verifyReturn(@RequestBody Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String orderIdStr = params.get("orderId");

        log.info("支付宝回跳验签 outTradeNo={}, tradeNo={}", outTradeNo, tradeNo);

        try {
            // 1. 验签
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, alipayConfig.getAlipayPublicKey(), "UTF-8", "RSA2");

            if (!signVerified && orderIdStr == null) {
                log.warn("支付宝回跳验签失败");
                Map<String, Object> result = new HashMap<>();
                result.put("payStatus", 3);
                result.put("msg", "验签失败");
                return Result.success(result);
            }

            // 2. 找到支付记录并更新
            Payment payment = null;
            if (outTradeNo != null) {
                payment = paymentService.getPaymentStatus(outTradeNo);
            }
            if (payment == null && orderIdStr != null) {
                payment = paymentService.getByOrderId(Long.valueOf(orderIdStr));
            }

            if (payment == null) {
                return Result.error("支付记录不存在");
            }

            // 3. 只有待支付状态才更新
            if (payment.getStatus() == 0) {
                orderService.handlePayCallback(payment.getOrderId(), payment.getId(), tradeNo, LocalDateTime.now());
                log.info("支付宝回跳验签成功，订单已更新 orderId={}", payment.getOrderId());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("payStatus", 2);
            result.put("payNo", payment.getPayNo());
            return Result.success(result);
        } catch (AlipayApiException e) {
            log.error("支付宝回跳验签异常: {}", e.getMessage(), e);
            return Result.error("验签失败");
        } catch (Exception e) {
            log.error("支付宝回跳处理异常: {}", e.getMessage(), e);
            return Result.error("处理失败");
        }
    }
}
