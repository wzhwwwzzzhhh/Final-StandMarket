package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 订单库存事实持久化合约")
class OrderInventoryPersistenceContractTest {

    @Test
    @DisplayName("订单实体和插入 SQL 必须持久化完整普通订单事实")
    void persistsInventoryAndPricingFacts() throws Exception {
        String entity = new String(Files.readAllBytes(Paths.get(
                "../fashion-pojo/src/main/java/com/fashion/entity/Orders.java")), StandardCharsets.UTF_8);
        String mapper = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/mapper/OrderMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();

        assertTrue(entity.contains("private Integer stockDeducted;"));
        assertTrue(mapper.contains("original_price"));
        assertTrue(mapper.contains("seckill_activity_id"));
        assertTrue(mapper.contains("seckill_coupon_id"));
        assertTrue(mapper.contains("is_seckill"));
        assertTrue(mapper.contains("seckill_price"));
        assertTrue(mapper.contains("stock_deducted"));
        assertTrue(mapper.contains("#{stockdeducted}"));
    }
}
