package com.fashion.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B3 退款状态迁移 SQL 合约")
class RefundStateMigrationSqlTest {

    @Test
    @DisplayName("已有库脚本使用双 marker 并阻断不可证明的历史状态")
    void migrationUsesStrictDualMarkersAndHistoricalGates() throws Exception {
        String sql = readRequired("../../mysql/add_refund_review_state.sql");

        assertTrue(sql.contains("chk_refund_status_b3"));
        assertTrue(sql.contains("chk_refund_order_status_b3"));
        assertTrue(sql.contains("check (status in (0, 1, 2, 3))"));
        assertTrue(sql.contains("check (order_status in (3, 4))"));
        assertTrue(sql.contains("order_status tinyint not null"));
        assertTrue(sql.contains("information_schema.check_constraints"));
        assertTrue(sql.contains("information_schema.table_constraints"));
        assertTrue(sql.contains("enforced"));
        assertTrue(sql.contains("status in (1, 2)"));
        assertTrue(sql.contains("refund_time is not null"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertTrue(sql.contains("partial b3 refund migration"));
        assertFalse(sql.contains("update refund set status"));
    }

    @Test
    @DisplayName("干净建表脚本包含同名四状态与申请前状态约束")
    void cleanSchemaMatchesB3Constraints() throws Exception {
        String sql = readRequired("../../mysql/refund_table.sql");

        assertTrue(sql.contains("status tinyint not null default '0'"));
        assertTrue(sql.contains("order_status tinyint not null"));
        assertTrue(sql.contains("constraint chk_refund_status_b3 check (status in (0, 1, 2, 3))"));
        assertTrue(sql.contains("constraint chk_refund_order_status_b3 check (order_status in (3, 4))"));
        assertTrue(sql.contains("0待审核 1已同意/待外部退款 2退款完成 3已拒绝"));
    }

    private String readRequired(String path) throws Exception {
        Path file = Paths.get(path);
        assertTrue(Files.isRegularFile(file), "missing SQL file " + path);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
}
