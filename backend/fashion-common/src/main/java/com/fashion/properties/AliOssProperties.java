package com.fashion.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fashion.alioss")
public class AliOssProperties {

    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;

}
