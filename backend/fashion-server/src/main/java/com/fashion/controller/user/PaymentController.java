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
            // Service 在订单行锁内校验本人待支付订单，并只使用持久化金额创建/复用流水。
            Payment payment = paymentService.createAlipayPayment(orderId);
            Orders order = orderService.getById(orderId);
            if (order == null) {
                return Result.error("订单不存在");
            }

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
                    "    \"total_amount\":" + payment.getAmount() + "," +
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
        } catch (IllegalStateException e) {
            log.warn("支付宝支付创建被拒绝 orderId={}, reason={}", orderId, e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询订单支付状态
     */
    @GetMapping("/status/{orderId}")
    public Result<Map<String, Object>> payStatus(@PathVariable Long orderId) {
        Long userId = BaseContext.getUserId();
        Orders order = orderService.getById(orderId);
        if (userId == null || order == null || !userId.equals(order.getUserId())) {
            return Result.error("订单不存在或无权查看");
        }

        Payment payment = paymentService.getByOrderId(orderId, 0);
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
        log.info("支付宝回跳验签 outTradeNo={}", outTradeNo);

        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, alipayConfig.getAlipayPublicKey(), "UTF-8", "RSA2");
            if (!signVerified) {
                log.warn("支付宝回跳验签失败 outTradeNo={}", outTradeNo);
                return Result.error("验签失败");
            }
            if (!alipayConfig.getAppId().equals(params.get("app_id"))) {
                log.warn("支付宝回跳 app_id 不匹配 outTradeNo={}", outTradeNo);
                return Result.error("验签失败");
            }
            if (outTradeNo == null || outTradeNo.trim().isEmpty()) {
                return Result.error("支付流水号缺失");
            }

            Payment payment = paymentService.getPaymentStatus(outTradeNo);
            if (payment == null || payment.getOrderType() == null || payment.getOrderType() != 0) {
                return Result.error("支付记录不存在");
            }

            Long userId = BaseContext.getUserId();
            Orders order = orderService.getById(payment.getOrderId());
            if (userId == null || order == null || !userId.equals(order.getUserId())) {
                return Result.error("订单不存在或无权查看");
            }

            // 同步回跳仅用于展示支付状态；订单状态只接受服务器端异步通知更新。
            Map<String, Object> result = new HashMap<>();
            result.put("payStatus", payment.getStatus());
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
