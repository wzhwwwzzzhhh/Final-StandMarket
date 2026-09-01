package com.fashion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B5 秒杀支付与取消状态合约")
class SeckillOrderStateContractTest {

    @Test
    @DisplayName("支付时间更新不改变状态且支付使用固定待支付 CAS")
    void paymentUsesDedicatedPendingCas() throws Exception {
        String xml = normalized("src/main/resources/mapper/SeckillOrderMapper.xml");
        String updatePayTime = statement(xml, "<update id=\"updatepaytime\">", "</update>");

        assertFalse(updatePayTime.contains("status"));
        assertTrue(xml.contains("<update id=\"markpaid\">"));
        assertTrue(xml.contains("set status = 2, pay_time = #{paytime}"));
        assertTrue(xml.contains("where order_number = #{ordernumber} and status = 1"));
        assertFalse(xml.contains("<update id=\"updatestatus\">"),
                "generic caller-supplied seckill status writes must not remain public");
        assertFalse(xml.contains("<select id=\"selectbyuseridandcouponid\""),
                "single-row lookup is invalid after cancelled histories become repeatable");
    }

    @Test
    @DisplayName("取消 SQL 固定从待支付迁移且用户入口保留归属条件")
    void cancellationUsesDedicatedOwnedAndTrustedCas() throws Exception {
        String xml = normalized("src/main/resources/mapper/SeckillOrderMapper.xml");
        String trusted = statement(xml, "<update id=\"cancelpending\">", "</update>");
        String owned = statement(xml, "<update id=\"cancelpendingbyordernumberanduserid\">", "</update>");

        assertTrue(trusted.contains("set status = 3"));
        assertTrue(trusted.contains("status = 1"));
        assertTrue(owned.contains("set status = 3"));
        assertTrue(owned.contains("user_id = #{userid}"));
        assertTrue(owned.contains("status = 1"));
    }

    @Test
    @DisplayName("MySQL 取消核心是独立 REQUIRES_NEW 事务且回补库存影响行数受检")
    void cancellationCoreHasIndependentTransactionBoundary() throws Exception {
        Path sourcePath = Paths.get("src/main/java/com/fashion/service/impl/SeckillCancellationTransaction.java");
        assertTrue(Files.isRegularFile(sourcePath), "missing independent cancellation transaction bean");
        String source = read(sourcePath).replaceAll("\\s+", " ");

        assertTrue(source.contains("Propagation.REQUIRES_NEW"));
        assertTrue(source.contains("cancelPendingByOrderNumberAndUserId"));
        assertTrue(source.contains("cancelPending"));
        assertTrue(source.contains("restoreStock"));
        assertTrue(source.contains("!= 1"));
    }

    private String statement(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing statement " + start);
        int to = source.indexOf(end, from);
        assertTrue(to >= 0, "unterminated statement " + start);
        return source.substring(from, to + end.length());
    }

    private String normalized(String path) throws Exception {
        return read(Paths.get(path))
                .replace("'", "\"")
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
