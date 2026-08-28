package com.fashion.controller.notify;

import com.alipay.api.internal.util.AlipaySignature;
import com.fashion.config.AlipayConfig;
import com.fashion.entity.Orders;
import com.fashion.entity.Payment;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付异步回调通知
 * 不加 /user 前缀，绕过登录拦截器
 */
@RestController
@RequestMapping("/notify")
public class PayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(PayNotifyController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayConfig alipayConfig;

    /**
     * 支付宝异步回调
     * 返回 "success" 或 "failure"
     */
    @PostMapping("/paySuccess")
    public String paySuccess(HttpServletRequest request) {
        try {
            // 1. 获取支付宝回调参数
            Map<String, String> params = getParamsFromRequest(request);

            // 2. 验签
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, alipayConfig.getAlipayPublicKey(), "UTF-8", "RSA2");

            if (!signVerified) {
                log.warn("支付宝回调验签失败 outTradeNo={}", params.get("out_trade_no"));
                return "failure";
            }

            // 3. 获取业务参数
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");

            if (isBlank(outTradeNo) || isBlank(tradeStatus)) {
                log.warn("支付宝回调缺少必要字段 outTradeNo={}, tradeStatus={}", outTradeNo, tradeStatus);
                return "failure";
            }

            if (!alipayConfig.getAppId().equals(params.get("app_id"))) {
                log.warn("支付宝回调 app_id 不匹配 outTradeNo={}", outTradeNo);
                return "failure";
            }

            // 已验签但尚未成功的通知确认收到，避免无意义重试，且不触碰业务数据。
            boolean successfulTrade = "TRADE_SUCCESS".equals(tradeStatus)
                    || "TRADE_FINISHED".equals(tradeStatus);
            if (!successfulTrade) {
                log.info("支付宝回调非成功状态 outTradeNo={}, tradeStatus={}", outTradeNo, tradeStatus);
                return "success";
            }
            if (isBlank(tradeNo) || isBlank(params.get("total_amount"))) {
                log.warn("支付宝成功回调缺少必要字段 outTradeNo={}", outTradeNo);
                return "failure";
            }

            // 5. 查询支付记录
            Payment payment = paymentService.getPaymentStatus(outTradeNo);
            if (payment == null || payment.getOrderType() == null || payment.getOrderType() != 0) {
                log.warn("普通订单支付记录不存在 outTradeNo={}", outTradeNo);
                return "failure";
            }

            // 5.1 比对回调金额与支付记录金额，防止金额被篡改
            String totalAmountStr = params.get("total_amount");
            BigDecimal callbackAmount;
            try {
                callbackAmount = new BigDecimal(totalAmountStr);
                if (payment.getAmount() == null || callbackAmount.compareTo(payment.getAmount()) != 0) {
                    log.warn("支付宝回调金额不匹配 outTradeNo={}", outTradeNo);
                    return "failure";
                }
            } catch (NumberFormatException e) {
                log.warn("支付宝回调金额格式非法 outTradeNo={}", outTradeNo);
                return "failure";
            }

            Orders order = orderService.getById(payment.getOrderId());
            if (order == null || !payment.getOrderId().equals(order.getId())
                    || order.getAmount() == null
                    || order.getAmount().compareTo(payment.getAmount()) != 0
                    || order.getAmount().compareTo(callbackAmount) != 0) {
                log.warn("支付宝回调订单归属或金额不匹配 outTradeNo={}, orderId={}",
                        outTradeNo, payment.getOrderId());
                return "failure";
            }

            // 幂等性和并发状态均在锁定记录后的事务服务中判定。
            orderService.handlePayCallback(payment.getOrderId(), payment.getId(), tradeNo, LocalDateTime.now());
            log.info("支付宝成功回调已处理 outTradeNo={}, tradeNo={}, tradeStatus={}",
                    outTradeNo, tradeNo, tradeStatus);
            return "success";
        } catch (Exception e) {
            log.error("支付宝回调处理失败 errorType={}", e.getClass().getSimpleName());
            return "failure";
        }
    }

    /**
     * 从 request 中提取参数
     */
    private Map<String, String> getParamsFromRequest(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        java.util.Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            params.put(paramName, request.getParameter(paramName));
        }
        return params;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
