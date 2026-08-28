package com.fashion.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B1 活动支付唯一约束 SQL")
class PaymentActiveUniqueSqlTest {

    @Test
    @DisplayName("增量脚本预检冲突、拒绝部分 schema 并原子添加生成列和唯一索引")
    void migrationContainsRequiredSafetyGuards() throws Exception {
        Path migration = Paths.get("..", "..", "mysql", "add_payment_active_unique.sql");
        String sql = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertTrue(sql.contains("status in (0, 1)"));
        assertTrue(sql.contains("group by order_id, order_type"));
        assertTrue(sql.contains("active_order_id"));
        assertTrue(sql.contains("active_order_type"));
        assertTrue(sql.contains("generated always"));
        assertTrue(sql.contains("unique index uk_payment_active_order"));
        assertTrue(sql.contains("alter table payment"));
    }
}
