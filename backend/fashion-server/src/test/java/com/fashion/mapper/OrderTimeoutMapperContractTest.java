package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 超时普通订单查询合约")
class OrderTimeoutMapperContractTest {

    @Test
    @DisplayName("查询覆盖无券订单并采用普通订单 keyset 限批")
    void selectsAllPendingOrdinaryOrdersByKeyset() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/mapper/OrderMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();

        int start = xml.indexOf("<select id=\"selecttimeoutorders\"");
        assertTrue(start >= 0);
        String sql = xml.substring(start, xml.indexOf("</select>", start));
        assertTrue(sql.contains("status = 1"));
        assertTrue(sql.contains("pay_status = 0"));
        assertTrue(sql.contains("is_seckill = 0"));
        assertTrue(sql.contains("id &gt; #{afterid}"));
        assertTrue(sql.contains("order by id asc"));
        assertTrue(sql.contains("limit #{limit}"));
        assertFalse(sql.contains("user_coupon_id is not null"));
    }
}
