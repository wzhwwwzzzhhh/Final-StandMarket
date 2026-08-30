package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 订单取消 CAS Mapper 合约")
class OrderCancellationMapperContractTest {

    @Test
    @DisplayName("取消必须比较锁定快照中的库存标识并原子清零")
    void cancellationChecksExpectedInventoryFact() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/mapper/OrderMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();
        int start = xml.indexOf("<update id=\"cancelpending\"");
        String sql = xml.substring(start, xml.indexOf("</update>", start));

        assertTrue(sql.contains("stock_deducted = 0"));
        assertTrue(sql.contains("stock_deducted = #{expectedstockdeducted}"));
        assertTrue(sql.contains("status = 1"));
        assertTrue(sql.contains("pay_status = 0"));
    }
}
