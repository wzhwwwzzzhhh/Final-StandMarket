package com.fashion.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B7 评价完整性迁移 SQL 合约")
class ReviewIntegrityMigrationSqlTest {

    @Test
    @DisplayName("增量脚本在 DDL 前检查结构与五类脏数据且不删除业务对象")
    void migrationFailsClosedBeforeAddingUniqueConstraint() throws Exception {
        String sql = read("../../mysql/add_review_integrity.sql");
        assertTrue(sql.contains("information_schema.tables"));
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertTrue(sql.contains("index_type = 'btree'"));
        assertTrue(sql.contains("signal sqlstate '45000'"));
        assertTrue(sql.contains("group by order_id, product_id having count(*) > 1"));
        assertTrue(sql.contains("r.order_id is null or r.product_id is null"));
        assertTrue(sql.contains("left join orders"));
        assertTrue(sql.contains("left join product"));
        assertTrue(sql.contains("r.user_id <> o.user_id"));
        assertTrue(sql.contains("not exists"));
        assertTrue(sql.contains("uk_review_order_product"));
        assertTrue(sql.contains("order_id,product_id"));
        assertTrue(sql.indexOf("alter table review") < sql.lastIndexOf("definition mismatch"),
                "最终签名必须在可能的 ALTER 之后统一复核");
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("delete from review"));
        assertFalse(sql.contains("update review"));
    }

    @Test
    @DisplayName("新建库和独立建表基线都包含正确二元唯一键且不破坏重建")
    void baselinesContainUniqueReviewKey() throws Exception {
        String finalSql = read("../../mysql/final07.sql");
        String reviewSql = read("../../mysql/review_table.sql");
        assertTrue(finalSql.contains("unique key `uk_review_order_product` (`order_id`,`product_id`)"));
        assertTrue(reviewSql.contains("unique key `uk_review_order_product` (`order_id`,`product_id`)"));
        assertTrue(reviewSql.contains("`create_time` datetime(3) not null"));
        assertTrue(reviewSql.contains("`update_time` datetime(3) default null"));
        assertFalse(reviewSql.contains("drop table"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();
    }
}
