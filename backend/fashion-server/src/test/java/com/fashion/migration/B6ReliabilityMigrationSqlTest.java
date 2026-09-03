package com.fashion.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B6 可靠消息迁移合约")
class B6ReliabilityMigrationSqlTest {

    @Test
    @DisplayName("迁移提供三表、严格唯一键和显式异常阻断")
    void migrationDefinesDurableReliabilityState() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_seckill_mq_reliability.sql");
        assertTrue(Files.isRegularFile(path), "missing B6 migration");
        String sql = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("create table seckill_message_log"));
        assertTrue(sql.contains("create table seckill_compensation_record"));
        assertTrue(sql.contains("create table seckill_reconciliation_anomaly"));
        assertTrue(sql.contains("unique key uk_seckill_message_id"));
        assertTrue(sql.contains("unique key uk_seckill_message_business"));
        assertTrue(sql.contains("unique key uk_seckill_compensation_action_order"));
        assertTrue(sql.contains("unique key uk_seckill_anomaly_type_coupon"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertFalse(sql.contains("create table if not exists"));
    }

    @Test
    @DisplayName("clean baseline 与升级迁移具有相同 B6 表和非空订单号")
    void baselineCarriesB6Schema() throws Exception {
        String baseline = new String(Files.readAllBytes(
                Paths.get("..", "..", "mysql", "final07.sql")), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(baseline.contains("create table `seckill_message_log`"));
        assertTrue(baseline.contains("create table `seckill_compensation_record`"));
        assertTrue(baseline.contains("create table `seckill_reconciliation_anomaly`"));
        assertTrue(baseline.contains("`order_number` varchar(50)")
                && baseline.contains("`order_number` varchar(50) character set utf8mb3 collate utf8mb3_bin not null"));
    }
}
