package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("B6 悬空预扣对账分类")
class B6ReconciliationPolicyTest {
    private final SeckillReconciliationPolicy policy = new SeckillReconciliationPolicy(Duration.ofMinutes(5));

    @Test
    @DisplayName("完整旧 token 且无订单无消息时可幂等释放")
    void provenOrphanCanBeReleased() {
        assertEquals(SeckillReconciliationPolicy.Action.RELEASE,
                policy.decide(true, true, null, null, Duration.ofMinutes(6)));
    }

    @Test
    @DisplayName("broker 已 ack 但尚无订单时只能有限恢复告警不得猜测释放")
    void brokerOwnedMessageIsNotReleased() {
        assertEquals(SeckillReconciliationPolicy.Action.RETRY_DELIVERY,
                policy.decide(true, true, null, "BROKER_ACKED", Duration.ofHours(1)));
    }

    @Test
    @DisplayName("活动或已支付订单是 MySQL 最终事实，禁止释放")
    void activeOrderPreventsRelease() {
        assertEquals(SeckillReconciliationPolicy.Action.KEEP,
                policy.decide(true, true, 1, "CONSUMED", Duration.ofHours(1)));
        assertEquals(SeckillReconciliationPolicy.Action.KEEP,
                policy.decide(true, true, 2, "CONSUMED", Duration.ofHours(1)));
    }

    @Test
    @DisplayName("ZSET/HASH 单边损坏进入人工异常而非库存猜测")
    void partialLedgerNeedsManualReview() {
        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED,
                policy.decide(true, false, null, null, Duration.ofHours(1)));
        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED,
                policy.decide(false, true, null, null, Duration.ofHours(1)));
    }

    @Test
    @DisplayName("PREPARED/重发/PROCESSING 由消息恢复轨道拥有，绝不按悬空释放")
    void inFlightStatesNeverReleaseReservation() {
        assertEquals(SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY,
                policy.decide(true, true, null, "PREPARED", Duration.ofHours(1)));
        assertEquals(SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY,
                policy.decide(true, true, null, "RETRY_PUBLISH_PENDING", Duration.ofHours(1)));
        assertEquals(SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY,
                policy.decide(true, true, null, "PROCESSING", Duration.ofHours(1)));
    }

    @Test
    @DisplayName("无订单却出现 CONSUMED/MANUAL 等不变量破坏只能转人工")
    void inconsistentTerminalStatesNeedManualReview() {
        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED,
                policy.decide(true, true, null, "CONSUMED", Duration.ofHours(1)));
        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED,
                policy.decide(true, true, null, "MANUAL_REQUIRED", Duration.ofHours(1)));
    }
}
