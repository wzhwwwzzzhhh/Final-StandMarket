package com.fashion.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B0 初始化 SQL 密码安全")
class SecuritySeedSqlTest {

    @Test
    @DisplayName("用户和员工演示账号不包含明文密码")
    void seedPasswordsAreBcryptHashes() throws Exception {
        Path repositoryRoot = Paths.get(System.getProperty("user.dir"), "..", "..").normalize();
        Path sqlPath = repositoryRoot.resolve("mysql").resolve("final07.sql");
        assertTrue(Files.exists(sqlPath), "missing seed SQL: " + sqlPath);

        String sql = new String(Files.readAllBytes(sqlPath), StandardCharsets.UTF_8);
        assertFalse(sql.contains("'123456'"));
        assertFalse(sql.contains("'123456987'"));
    }
}
