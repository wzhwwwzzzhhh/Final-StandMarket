package com.fashion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B6 Redis 秒杀取消回补 Lua 合约")
class SeckillRollbackLuaContractTest {

    @Test
    @DisplayName("Lua 在任何写入前校验类型参数整数和上溢")
    void validatesEveryPredictableFailureBeforeWriting() throws Exception {
        Path scriptPath = Paths.get("src/main/resources/lua/seckill_rollback.lua");
        assertTrue(Files.isRegularFile(scriptPath), "missing seckill_rollback.lua");
        String script = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8).toLowerCase();

        int firstWrite = Math.min(script.indexOf("redis.call('incrby'"), script.indexOf("redis.call('zrem'"));
        assertTrue(firstWrite > 0, "missing rollback writes");
        assertTrue(script.indexOf("redis.call('type'") < firstWrite);
        assertTrue(script.indexOf("2147483646") < firstWrite);
        assertTrue(script.indexOf("string.match") < firstWrite);
        assertTrue(script.indexOf("redis.call('zscore'") < firstWrite);
        assertTrue(script.indexOf("redis.call('incrby'") < script.indexOf("redis.call('zrem'"));
    }

    @Test
    @DisplayName("Lua 为幂等、令牌冲突、账本损坏和成功返回稳定结果码")
    void exposesStableResultCodes() throws Exception {
        Path scriptPath = Paths.get("src/main/resources/lua/seckill_rollback.lua");
        assertTrue(Files.isRegularFile(scriptPath), "missing seckill_rollback.lua");
        String script = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        assertTrue(script.contains("RETURN_APPLIED"));
        assertTrue(script.contains("RETURN_APPLIED_LEDGER_INCONSISTENT"));
        assertTrue(script.contains("RETURN_ALREADY_APPLIED"));
        assertTrue(script.contains("RETURN_TOKEN_MISMATCH"));
        assertTrue(script.contains("RETURN_LEDGER_CORRUPT"));
        assertTrue(script.contains("RETURN_INVALID_VALUE"));
    }
}
