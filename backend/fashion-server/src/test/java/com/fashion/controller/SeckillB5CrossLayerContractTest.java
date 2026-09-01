package com.fashion.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B5 秒杀前端、延迟和迁移合约")
class SeckillB5CrossLayerContractTest {

    @Test
    @DisplayName("秒杀超时关闭固定为三十分钟")
    void delayQueueUsesThirtyMinutes() throws Exception {
        String config = normalized("src/main/java/com/fashion/config/DirectExchangeConfig.java");

        assertTrue(config.contains("seckill_order_timeout_millis"));
        assertTrue(config.contains("30 * 60 * 1000"));
        assertTrue(config.contains("x-message-ttl"));
        assertTrue(config.contains("seckill_order_timeout_millis"));
        assertFalse(config.contains("900000"));
    }

    @Test
    @DisplayName("用户端调用真实取消接口并区分 Redis 待对账")
    void clientCallsRealCancellationApi() throws Exception {
        String api = read("../../frontend/fashion-client/src/api/seckill.js");
        String view = read("../../frontend/fashion-client/src/views/SeckillOrder.vue");

        assertTrue(api.contains("cancelSeckillOrder"));
        assertTrue(api.contains("/user/seckill/order/cancel/${orderNumber}"));
        assertTrue(view.contains("await seckillApi.cancelSeckillOrder(order.orderNumber)"));
        assertTrue(view.contains("REDIS_RECONCILIATION_PENDING"));
        assertTrue(view.contains("ElMessage.warning"));
        assertFalse(view.contains("// 这里调用取消订单接口"));
    }

    @Test
    @DisplayName("管理端只对待支付订单开放支付和取消且没有随机支付")
    void adminOnlyActsOnPendingOrdersWithoutRandomPayment() throws Exception {
        String view = read("../../frontend/fashion-admin/src/views/SeckillOrderList.vue")
                .replaceAll("\\s+", " ");

        assertFalse(view.contains("scope.row.status === 0 || scope.row.status === 1"));
        assertTrue(view.contains("v-if=\"scope.row.status === 1\""));
        assertFalse(view.contains("Math.random"));
    }

    @Test
    @DisplayName("干净库和升级脚本只允许取消态释放活动唯一约束")
    void schemaSupportsRepurchaseOnlyAfterCancellation() throws Exception {
        String baseline = normalized("../../mysql/final07.sql");
        String migration = normalizedRequired("../../mysql/add_seckill_state_inventory.sql");

        assertTrue(baseline.contains("status int not null default '1'"));
        assertTrue(baseline.contains("constraint chk_seckill_order_status_b5 check"));
        assertTrue(baseline.contains("status in (1,2,3)"));
        assertTrue(baseline.contains("case when (status = 3) then null else 1 end"));
        assertTrue(baseline.contains("unique key uk_seckill_order_active_user_coupon (user_id,coupon_id,active_marker)"));
        assertTrue(baseline.contains("insert into seckill_order (id,user_id,coupon_id,order_number,status,create_time,pay_time) values"));
        assertFalse(baseline.contains("insert into seckill_order values"));

        assertTrue(migration.contains("status is null or status not in (1, 2, 3)"));
        assertTrue(migration.contains("information_schema.check_constraints"));
        assertTrue(migration.contains("information_schema.statistics"));
        assertTrue(migration.contains("signal sqlstate '45000'"));
        assertTrue(migration.contains("case when status = 3 then null else 1 end"));
    }

    private String normalizedRequired(String path) throws Exception {
        Path file = Paths.get(path);
        assertTrue(Files.isRegularFile(file), "missing SQL file " + path);
        return normalized(path);
    }

    private String normalized(String path) throws Exception {
        return read(path).replace("`", "").replaceAll("\\s+", " ").toLowerCase();
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
