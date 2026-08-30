package com.fashion.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B3 两端退款状态文案契约")
class RefundUiContractTest {

    @Test
    @DisplayName("管理端区分待外部退款与退款完成")
    void adminUsesFourPreciseRefundStates() throws Exception {
        String source = read("../../frontend/fashion-admin/src/views/RefundList.vue");

        assertTrue(source.contains("label=\"已同意，等待退款处理\" :value=\"1\""));
        assertTrue(source.contains("label=\"退款完成\" :value=\"2\""));
        assertTrue(source.contains("1: '已同意，等待退款处理'"));
        assertTrue(source.contains("2: '退款完成'"));
        assertFalse(source.contains("this.$message.success('退款成功')"));
    }

    @Test
    @DisplayName("用户端区分待外部退款与退款完成")
    void clientUsesFourPreciseRefundStates() throws Exception {
        String source = read("../../frontend/fashion-client/src/views/RefundList.vue");

        assertTrue(source.contains("1: '已同意，等待退款处理'"));
        assertTrue(source.contains("2: '退款完成'"));
        assertTrue(source.contains(".status-1"));
        assertFalse(source.contains("2: '已退款'"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
