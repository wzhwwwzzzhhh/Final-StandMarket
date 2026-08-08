package com.fashion.controller.notify;

import com.alipay.api.AlipayApiException;
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
import java.util.Enumeration;
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
                log.error("支付宝回调验签失败：{}", params);
                return "failure";
            }

            // 3. 获取业务参数
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");

            log.info("支付宝回调成功 outTradeNo={}, tradeNo={}, tradeStatus={}",
                    outTradeNo, tradeNo, tradeStatus);

            // 4. 只处理支付成功的情况
            if (!"TRADE_SUCCESS".equals(tradeStatus)) {
                return "success";
            }

            // 5. 查询支付记录
            Payment payment = paymentService.getPaymentStatus(outTradeNo);
            if (payment == null) {
                log.warn("支付记录不存在 outTradeNo={}", outTradeNo);
                return "failure";
            }

            // 5.1 比对回调金额与支付记录金额，防止金额被篡改
            String totalAmountStr = params.get("total_amount");
            if (totalAmountStr == null) {
                log.error("支付宝回调缺少 total_amount outTradeNo={}", outTradeNo);
                return "failure";
            }
            try {
                BigDecimal callbackAmount = new BigDecimal(totalAmountStr);
                if (payment.getAmount() == null || callbackAmount.compareTo(payment.getAmount()) != 0) {
                    log.error("支付宝回调金额不匹配 outTradeNo={}, 回调金额={}, 应支付金额={}",
                            outTradeNo, totalAmountStr, payment.getAmount());
                    return "failure";
                }
            } catch (NumberFormatException e) {
                log.error("支付宝回调金额格式非法 outTradeNo={}, total_amount={}", outTradeNo, totalAmountStr);
                return "failure";
            }

            // 6. 验证 app_id 防止跨应用回调
            if (!alipayConfig.getAppId().equals(params.get("app_id"))) {
                log.error("支付宝回调 app_id 不匹配：{}", params.get("app_id"));
                return "failure";
            }

            // 7. 避免重复处理（非待支付状态直接跳过）
            if (payment.getStatus() != 0) {
                log.info("支付记录已处理 outTradeNo={}, status={}", outTradeNo, payment.getStatus());
                return "success";
            }

            // 8. 事务性更新支付记录 + 订单状态
            orderService.handlePayCallback(payment.getOrderId(), payment.getId(), tradeNo, LocalDateTime.now());
            return "success";
        } catch (AlipayApiException e) {
            log.error("支付宝回调处理异常：{}", e.getMessage(), e);
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
}
