package com.fashion.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B6 秒杀取消 API 与编排合约")
class SeckillCancellationApiContractTest {

    @Test
    @DisplayName("取消响应区分完整成功与 Redis 待对账且二者都是业务成功")
    void responseExposesStableCancellationOutcome() throws Exception {
        Path response = Paths.get("../fashion-pojo/src/main/java/com/fashion/dto/SeckillCancelResponse.java");
        assertTrue(Files.isRegularFile(response), "missing SeckillCancelResponse");
        String dto = read(response);

        assertTrue(dto.contains("CANCELLED"));
        assertTrue(dto.contains("REDIS_RECONCILIATION_PENDING"));
        assertTrue(dto.contains("orderStatus"));
        assertTrue(dto.contains("outcome"));
        assertTrue(dto.contains("message"));
    }

    @Test
    @DisplayName("外层取消编排不持有事务且超时监听复用同一服务")
    void orchestrationCommitsMysqlBeforeRedisAndIsSharedByTimeout() throws Exception {
        String service = normalized("src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java");
        String timeoutConsumer = normalized("src/main/java/com/fashion/seckill/SeckillTimeoutConsumer.java");
        String userCancel = method(service, "public seckillcancelresponse cancelcurrentuserorder", "@override");
        String adminCancel = method(service, "public seckillcancelresponse cancelorder", "@override");

        assertTrue(service.contains("seckillcancellationtransaction"));
        assertTrue(service.contains("seckillreservationservice"));
        assertTrue(service.contains("seckillreservationservice.rollback"));
        assertFalse(userCancel.contains("@transactional"));
        assertFalse(adminCancel.contains("@transactional"));
        assertTrue(timeoutConsumer.contains("orderservice.canceltimeoutorder"));
        assertTrue(timeoutConsumer.contains("seckillmanualackcontainerfactory"));
    }

    @Test
    @DisplayName("用户与管理接口对冲突返回失败而对待对账返回响应 DTO")
    void controllersUseCancellationResponseInsteadOfBoolean() throws Exception {
        String user = read(Paths.get("src/main/java/com/fashion/controller/user/UserSeckillOrderController.java"));
        String admin = read(Paths.get("src/main/java/com/fashion/controller/admin/SeckillOrderController.java"));

        assertTrue(user.contains("SeckillCancelResponse"));
        assertTrue(admin.contains("SeckillCancelResponse"));
        assertTrue(user.contains("订单不存在或状态已变化，无法取消"));
        assertTrue(admin.contains("订单不存在或状态已变化，无法取消"));
        assertTrue(user.contains("Result.success(response)"));
        assertTrue(admin.contains("Result.success(response)"));
    }

    private String method(String source, String signature, String nextMethodMarker) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing method " + signature);
        int end = source.indexOf(nextMethodMarker, start + signature.length());
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }

    private String normalized(String path) throws Exception {
        return read(Paths.get(path)).replaceAll("\\s+", " ").toLowerCase();
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
