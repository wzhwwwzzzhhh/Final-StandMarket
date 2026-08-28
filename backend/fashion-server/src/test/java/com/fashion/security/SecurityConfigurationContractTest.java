package com.fashion.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B0 配置与支付日志安全")
class SecurityConfigurationContractTest {

    @Test
    @DisplayName("JWT 密钥必须外部注入且 Mapper 不启用参数 DEBUG 日志")
    void jwtSecretsAreExternalAndMapperDebugIsDisabled() throws Exception {
        String yaml = readResource("/application.yml");

        assertFalse(yaml.contains("admin-secret-key: itcast"));
        assertFalse(yaml.contains("user-secret-key: itheima"));
        assertTrue(yaml.contains("admin-secret-key: ${FASHION_JWT_ADMIN_SECRET_KEY}"));
        assertTrue(yaml.contains("user-secret-key: ${FASHION_JWT_USER_SECRET_KEY}"));
        assertFalse(yaml.contains("mapper: debug"));
    }

    @Test
    @DisplayName("支付回调验签失败不记录完整参数 Map")
    void payNotifyDoesNotLogFullCallbackParameters() throws Exception {
        Path source = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "com", "fashion",
                "controller", "notify", "PayNotifyController.java");
        assertTrue(Files.exists(source));
        String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

        Pattern fullParamsLog = Pattern.compile(
                "log\\.(?:trace|debug|info|warn|error)\\s*\\([^;]*[,\\s]params\\s*(?:,|\\))",
                Pattern.DOTALL
        );
        assertFalse(fullParamsLog.matcher(code).find());
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing resource: " + path);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
