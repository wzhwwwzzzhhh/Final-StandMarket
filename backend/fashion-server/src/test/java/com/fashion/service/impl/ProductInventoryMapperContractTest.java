package com.fashion.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 商品库存 Mapper 原子契约")
class ProductInventoryMapperContractTest {

    @Test
    @DisplayName("扣库存必须由数据库校验启用状态和充足库存")
    void deductStockIsConditionalAndAtomic() throws Exception {
        Path mapper = Paths.get("src", "main", "resources", "mapper", "ProductMapper.xml");
        String xml = new String(Files.readAllBytes(mapper), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(xml.contains("<update id=\"deductstock\">"));
        assertTrue(xml.contains("stock = stock - #{quantity}"));
        assertTrue(xml.contains("stock >= #{quantity}"));
        assertTrue(xml.contains("status = 1"));
    }
}
