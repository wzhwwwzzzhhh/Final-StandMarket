package com.fashion.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B0 操作日志脱敏")
class OperationLogAspectTest {

    @Test
    @DisplayName("嵌套对象中的密码 Token 验证码和私钥全部脱敏")
    void nestedSensitiveValuesAreRedacted() {
        OperationLogAspect aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "objectMapper", new ObjectMapper());

        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("password", "raw-password");
        credentials.put("token", "raw-token");
        credentials.put("smsCode", "654321");
        credentials.put("privateKey", "-----BEGIN PRIVATE KEY-----");
        credentials.put("name", "safe-name");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("payload", Arrays.asList(credentials));

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});

        String params = ReflectionTestUtils.invokeMethod(aspect, "buildParams", joinPoint);

        assertFalse(params.contains("raw-password"));
        assertFalse(params.contains("raw-token"));
        assertFalse(params.contains("654321"));
        assertFalse(params.contains("BEGIN PRIVATE KEY"));
        assertTrue(params.contains("safe-name"));
        assertTrue(params.contains("***"));
    }

    @Test
    @DisplayName("序列化失败时不回退到可能泄密的 toString")
    void serializationFailureUsesSafeFallback() {
        OperationLogAspect aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "objectMapper", new ObjectMapper());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new UnserializableSecret()});

        String params = ReflectionTestUtils.invokeMethod(aspect, "buildParams", joinPoint);

        assertFalse(params.contains("leaked-fallback"));
        assertTrue(params.contains("unserializable"));
    }

    private static class UnserializableSecret {
        public UnserializableSecret getSelf() {
            return this;
        }

        @Override
        public String toString() {
            return "password=leaked-fallback";
        }
    }
}
