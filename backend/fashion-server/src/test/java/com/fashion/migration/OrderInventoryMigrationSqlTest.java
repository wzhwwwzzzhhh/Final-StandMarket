package com.fashion.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 订单库存迁移 SQL 合约")
class OrderInventoryMigrationSqlTest {

    @Test
    @DisplayName("已有库脚本必须校验字段、CHECK 与超时索引定义且可重复执行")
    void migrationValidatesExistingDefinitions() throws Exception {
        String sql = read("../../mysql/add_order_inventory_state.sql");
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertTrue(sql.contains("information_schema.check_constraints"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertTrue(sql.contains("stock_deducted tinyint(1) not null default 0"));
        assertTrue(sql.contains("user_coupon_id bigint null"));
        assertTrue(sql.contains("check (stock_deducted in (0, 1))"));
        assertTrue(sql.contains("idx_orders_timeout"));
        assertTrue(sql.contains("status,pay_status,order_time"));
        assertTrue(sql.contains("status in (2, 3) and stock_deducted = 0"));
        assertTrue(sql.contains("historical fulfillment orders must be completed"));
        assertFalse(sql.contains("update orders set stock_deducted = 1"));
    }

    @Test
    @DisplayName("新建库基线同步库存事实、通用券列、CHECK 和超时索引")
    void baselineContainsB2OrderSchema() throws Exception {
        String sql = read("../../mysql/final07.sql");
        String orders = tableDefinition(sql, "orders");
        String details = tableDefinition(sql, "order_detail");
        assertTrue(orders.contains("`user_coupon_id` bigint default null"));
        assertTrue(orders.contains("`stock_deducted` tinyint(1) not null default '0'"));
        assertTrue(orders.contains("constraint `chk_orders_stock_deducted` check ((`stock_deducted` in (0,1)))"));
        assertTrue(orders.contains("key `idx_orders_timeout` (`status`,`pay_status`,`order_time`)"));
        assertFalse(details.contains("user_coupon_id"));
        assertFalse(details.contains("stock_deducted"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();
    }

    private String tableDefinition(String sql, String table) {
        int start = sql.indexOf("create table `" + table + "`");
        assertTrue(start >= 0);
        return sql.substring(start, sql.indexOf("engine=", start));
    }
}
