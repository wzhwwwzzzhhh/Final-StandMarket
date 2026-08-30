package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 下单购物车快照查询合约")
class ShoppingCartOrderSelectionContractTest {

    @Test
    @DisplayName("所选购物车项必须按当前用户一次批量读取")
    void selectedItemsAreLoadedForCurrentUserInOneQuery() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/mapper/ShoppingCartMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();
        int start = xml.indexOf("<select id=\"findbyidsanduserid\"");
        assertTrue(start >= 0);
        String sql = xml.substring(start, xml.indexOf("</select>", start));
        assertTrue(sql.contains("user_id = #{userid}"));
        assertTrue(sql.contains("id in"));
        assertTrue(sql.contains("collection=\"ids\""));
    }
}
