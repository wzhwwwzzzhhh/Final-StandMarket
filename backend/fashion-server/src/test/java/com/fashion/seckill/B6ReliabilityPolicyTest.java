package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B6 可靠消息纯策略")
class B6ReliabilityPolicyTest {

    @Test
    @DisplayName("旧 publish attempt 的迟到回调只能审计")
    void stalePublisherCallbackIsAuditOnly() {
        SeckillPublishCallbackPolicy policy = new SeckillPublishCallbackPolicy();

        SeckillPublishCallbackPolicy.Action action = policy.decide(
                SeckillPublishCallbackPolicy.MessageType.ORDER_CREATE,
                SeckillPublishCallbackPolicy.Purpose.INITIAL,
                "SECKILL_ORDER_CREATE:9:P2",
                "SECKILL_ORDER_CREATE:9:P1",
                false,
                false,
                false);

        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY, action);
    }

    @Test
    @DisplayName("初始消息 return 优先于 ack 且已消费订单不得补偿")
    void initialReturnIsFailureUnlessOrderAlreadyConsumed() {
        SeckillPublishCallbackPolicy policy = new SeckillPublishCallbackPolicy();
        String correlation = "SECKILL_ORDER_CREATE:9:P1";

        assertEquals(SeckillPublishCallbackPolicy.Action.COMPENSATE_RESERVATION,
                policy.decide(SeckillPublishCallbackPolicy.MessageType.ORDER_CREATE,
                        SeckillPublishCallbackPolicy.Purpose.INITIAL,
                        correlation, correlation, false, true, true));
        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                policy.decide(SeckillPublishCallbackPolicy.MessageType.ORDER_CREATE,
                        SeckillPublishCallbackPolicy.Purpose.INITIAL,
                        correlation, correlation, true, true, true));
    }

    @Test
    @DisplayName("已消费订单的迟到 ack 不得把交易终态回退为 broker ack")
    void consumedMessageLateAckIsAuditOnly() {
        SeckillPublishCallbackPolicy policy = new SeckillPublishCallbackPolicy();
        String correlation = "SECKILL_ORDER_CREATE:9:P1";

        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                policy.decide(SeckillPublishCallbackPolicy.MessageType.ORDER_CREATE,
                        SeckillPublishCallbackPolicy.Purpose.INITIAL,
                        correlation, correlation, true, false, true));
    }

    @Test
    @DisplayName("timeout 与业务死信发布失败只恢复各自轨道")
    void nonInitialFailuresNeverReleaseReservation() {
        SeckillPublishCallbackPolicy policy = new SeckillPublishCallbackPolicy();
        String correlation = "M:P3";

        assertEquals(SeckillPublishCallbackPolicy.Action.RETRY_TIMEOUT,
                policy.decide(SeckillPublishCallbackPolicy.MessageType.ORDER_TIMEOUT,
                        SeckillPublishCallbackPolicy.Purpose.TIMEOUT_RECOVERY,
                        correlation, correlation, false, false, false));
        assertEquals(SeckillPublishCallbackPolicy.Action.RETRY_DEAD_LETTER,
                policy.decide(SeckillPublishCallbackPolicy.MessageType.BUSINESS_DEAD_LETTER,
                        SeckillPublishCallbackPolicy.Purpose.DEAD_LETTER,
                        correlation, correlation, false, true, true));
    }

    @Test
    @DisplayName("非法消息隔离身份由稳定 envelope 和 body 决定")
    void invalidMessageIdentityIsStableAndContentSensitive() {
        byte[] body = "bad-payload".getBytes(StandardCharsets.UTF_8);

        String first = SeckillMessageIdentity.quarantineKey(
                "market.direct", "seckillOrder", "application/json", body);
        String repeated = SeckillMessageIdentity.quarantineKey(
                "market.direct", "seckillOrder", "application/json", body);
        String different = SeckillMessageIdentity.quarantineKey(
                "market.direct", "seckillOrder", "application/json",
                "other-payload".getBytes(StandardCharsets.UTF_8));

        assertEquals(first, repeated);
        assertTrue(first.matches("INVALID:[0-9a-f]{64}"));
        assertNotEquals(first, different);
    }

    @Test
    @DisplayName("延迟恢复只使用原 dueAt 的剩余时间")
    void delayRecoveryNeverResetsBusinessDeadline() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");

        assertEquals(90_000L, SeckillDelayPolicy.remainingMillis(
                now.plusSeconds(90), now));
        assertEquals(0L, SeckillDelayPolicy.remainingMillis(now, now));
        assertEquals(0L, SeckillDelayPolicy.remainingMillis(
                now.minusSeconds(1), now));
    }
}
