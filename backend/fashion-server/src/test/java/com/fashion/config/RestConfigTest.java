package com.fashion.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B1 共享 HTTP 客户端超时")
class RestConfigTest {

    @Test
    @DisplayName("默认连接与读取超时均为有限正值")
    void finiteDefaultTimeouts() {
        RestConfig config = new RestConfig();
        ReflectionTestUtils.setField(config, "connectTimeoutMs", 3000);
        ReflectionTestUtils.setField(config, "readTimeoutMs", 30000);

        RestTemplate template = config.restTemplate();
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) template.getRequestFactory();

        int connectTimeout = (int) ReflectionTestUtils.getField(factory, "connectTimeout");
        int readTimeout = (int) ReflectionTestUtils.getField(factory, "readTimeout");
        assertTrue(connectTimeout > 0);
        assertTrue(readTimeout > 0);
        assertEquals(3000, connectTimeout);
        assertEquals(30000, readTimeout);
    }

    @Test
    @DisplayName("外部配置值可覆盖默认超时")
    void configurableTimeouts() {
        RestConfig config = new RestConfig();
        ReflectionTestUtils.setField(config, "connectTimeoutMs", 1250);
        ReflectionTestUtils.setField(config, "readTimeoutMs", 9750);

        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory)
                config.restTemplate().getRequestFactory();

        assertEquals(1250, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(9750, ReflectionTestUtils.getField(factory, "readTimeout"));
    }
}
