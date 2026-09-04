package com.fashion.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B8ProductCacheMigrationSqlTest {

    @Test
    void migrationDefinesFourOrderedDurableFactsAndStrictGuards() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(
                "..", "..", "mysql", "add_product_cache_consistency.sql")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();

        int state = sql.indexOf("create table product_catalog_state");
        int revision = sql.indexOf("create table product_catalog_revision");
        int task = sql.indexOf("create table product_projection_task");
        int run = sql.indexOf("create table product_projection_reconcile_run");
        assertTrue(state > 0 && state < revision && revision < task && task < run);
        assertTrue(sql.contains("mysql 8.0.16"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertTrue(sql.contains("unique key uk_product_projection_fact (target,product_id,catalog_version)"));
        assertTrue(sql.contains("unique key uk_product_reconcile_active (active_slot)"));
        assertTrue(sql.contains("generated always as"));
        assertTrue(sql.contains("check (list_version between 1 and 9007199254740991)"));
        assertTrue(sql.contains("sales is null"));
        assertTrue(sql.contains("status not in (0,1)"));
        assertFalse(sql.contains("create table if not exists"));
        assertFalse(sql.contains("drop table product_"));
    }
}
