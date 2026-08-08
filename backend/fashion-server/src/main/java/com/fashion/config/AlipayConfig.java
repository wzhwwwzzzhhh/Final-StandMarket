package com.fashion.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝沙箱配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "fashion.alipay")
public class AlipayConfig {

    /** 应用ID（沙箱环境的 APP_ID） */
    private String appId;

    /** 应用私钥（使用支付宝开放平台助手生成） */
    private String appPrivateKey;

    /** 支付宝公钥（从沙箱应用详情获取） */
    private String alipayPublicKey;

    /** 网关地址（沙箱固定值） */
    private String gatewayUrl;

    /** 异步回调地址 */
    private String notifyUrl;

    /** 同步回跳地址 */
    private String returnUrl;

    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                appPrivateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
        );
    }
}
