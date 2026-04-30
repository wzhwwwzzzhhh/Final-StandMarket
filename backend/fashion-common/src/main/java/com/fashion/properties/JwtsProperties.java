package com.fashion.properties;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fashion.jwt")
@Data
public class JwtsProperties {
    /**
     * 管理员密钥
     */
    private String AdminSecretKey;
    private long AdminTtl;
    private String AdminTokenName;
    /**
     * 用户密钥
     */
    private String UserSecretKey;
    private long UserTtl;
    private String UserTokenName;

}
