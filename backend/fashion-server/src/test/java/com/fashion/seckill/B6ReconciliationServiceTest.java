package com.fashion.seckill;

import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.entity.SeckillOrder;
import com.fashion.entity.SeckillMessageLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 对账发现与幂等修复")
class B6ReconciliationServiceTest {
    private SeckillOrderMapper orderMapper;
    private SeckillMessageLogMapper messageMapper;
    private SeckillCompensationService compensationService;
    private SeckillCompensationExecutor compensationExecutor;
    private SeckillOrphanClaimTransaction orphanClaimTransaction;
    private SeckillReconciliationAnomalyMapper anomalyMapper;
    private SeckillReconciliationService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(SeckillOrderMapper.class);
        messageMapper = mock(SeckillMessageLogMapper.class);
        compensationService = mock(SeckillCompensationService.class);
        compensationExecutor = mock(SeckillCompensationExecutor.class);
        orphanClaimTransaction = mock(SeckillOrphanClaimTransaction.class);
        anomalyMapper = mock(SeckillReconciliationAnomalyMapper.class);
        service = new SeckillReconciliationService(orderMapper, messageMapper,
                compensationExecutor, orphanClaimTransaction, anomalyMapper,
                new SeckillReconciliationPolicy(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("无订单无消息的旧完整 reservation 建唯一补偿并执行 token rollback")
    void orphanIsReleasedAndRecorded() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        when(compensationExecutor.execute("9001"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);
        when(orphanClaimTransaction.claim(snapshot)).thenReturn(SeckillOrphanClaimTransaction.Result.CLAIMED);

        assertEquals(SeckillReconciliationPolicy.Action.RELEASE, service.reconcile(snapshot));

        verify(orphanClaimTransaction).claim(snapshot);
        verify(compensationExecutor).execute("9001");
    }

    @Test
    @DisplayName("单边账本损坏只记幂等 anomaly，不猜测增加库存")
    void partialLedgerIsQuarantined() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, false, Duration.ofMinutes(10));

        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED, service.reconcile(snapshot));

        verify(anomalyMapper).upsert(org.mockito.ArgumentMatchers.eq("LEDGER_CARDINALITY_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"), org.mockito.ArgumentMatchers.anyString());
        verify(compensationExecutor, never()).execute("9001");
    }

    @Test
    @DisplayName("有效订单缺失 reservation 时留下专用高优先级异常且不盲目扣库存")
    void activeOrderWithoutReservationIsQuarantined() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", false, false, Duration.ZERO);
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber("9001");
        order.setUserId(7L);
        order.setCouponId(19L);
        order.setStatus(1);
        when(orderMapper.selectByOrderNumber("9001")).thenReturn(order);

        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED, service.reconcile(snapshot));

        verify(anomalyMapper).upsert(org.mockito.ArgumentMatchers.eq("ACTIVE_ORDER_RESERVATION_MISSING"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"), org.mockito.ArgumentMatchers.anyString());
        verify(compensationExecutor, never()).execute("9001");
    }

    @Test
    @DisplayName("订单号存在但 user/coupon 身份不一致时转人工且不得释放")
    void orderIdentityMismatchIsManualRequired() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber("9001");
        order.setUserId(8L);
        order.setCouponId(19L);
        order.setStatus(1);
        when(orderMapper.selectByOrderNumber("9001")).thenReturn(order);

        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED, service.reconcile(snapshot));

        verify(compensationExecutor, never()).execute("9001");
        verify(anomalyMapper).upsert(org.mockito.ArgumentMatchers.eq("ORDER_IDENTITY_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("broker ack 后超过窗口仍无订单时持久调度有限重发")
    void staleBrokerAckSchedulesFiniteRedelivery() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        SeckillMessageLog message = new SeckillMessageLog();
        message.setMessageId("SECKILL_ORDER_CREATE:9001");
        message.setStatus("BROKER_ACKED");
        when(messageMapper.selectByMessageId(message.getMessageId())).thenReturn(message);
        when(messageMapper.scheduleReconciliationRedelivery(message.getMessageId(), 5)).thenReturn(1);

        assertEquals(SeckillReconciliationPolicy.Action.RETRY_DELIVERY, service.reconcile(snapshot));

        verify(messageMapper).scheduleReconciliationRedelivery(message.getMessageId(), 5);
        verify(compensationExecutor, never()).execute("9001");
    }

    @Test
    @DisplayName("并发 ACKED 对账 CAS 败者观察到已进入恢复轨道时保持幂等")
    void concurrentRedeliveryClaimLoserIsNotManual() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        SeckillMessageLog first = new SeckillMessageLog();
        first.setMessageId("SECKILL_ORDER_CREATE:9001");
        first.setStatus("BROKER_ACKED");
        SeckillMessageLog recovered = new SeckillMessageLog();
        recovered.setMessageId(first.getMessageId());
        recovered.setStatus("RETRY_PUBLISH_PENDING");
        when(messageMapper.selectByMessageId(first.getMessageId())).thenReturn(first, recovered);

        assertEquals(SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY, service.reconcile(snapshot));

        verify(anomalyMapper, never()).upsert(
                org.mockito.ArgumentMatchers.eq("DELIVERY_RECOVERY_CONFLICT"),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("补偿 token 不匹配留下 durable anomaly 而非只写日志")
    void compensationTokenMismatchCreatesAnomaly() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        when(orphanClaimTransaction.claim(snapshot)).thenReturn(SeckillOrphanClaimTransaction.Result.CLAIMED);
        when(compensationExecutor.execute("9001"))
                .thenReturn(SeckillReservationService.RollbackResult.TOKEN_MISMATCH);

        assertEquals(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED, service.reconcile(snapshot));

        verify(anomalyMapper).upsert(
                org.mockito.ArgumentMatchers.eq("COMPENSATION_TOKEN_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("并发补偿 claim 败者观察到有效租约时等待收敛")
    void concurrentCompensationClaimLoserWaitsForOwner() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        when(orphanClaimTransaction.claim(snapshot)).thenReturn(SeckillOrphanClaimTransaction.Result.CLAIMED);
        when(compensationExecutor.execute("9001"))
                .thenReturn(SeckillReservationService.RollbackResult.INFRA_FAILURE);
        when(compensationExecutor.isClaimActiveOrSucceeded("9001")).thenReturn(true);

        assertEquals(SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY, service.reconcile(snapshot));
    }

    @Test
    @DisplayName("补偿基础设施失败时本轮对账失败而不是误报释放成功")
    void compensationInfrastructureFailureKeepsCycleUnclean() {
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        when(orphanClaimTransaction.claim(snapshot)).thenReturn(SeckillOrphanClaimTransaction.Result.CLAIMED);
        when(compensationExecutor.execute("9001"))
                .thenReturn(SeckillReservationService.RollbackResult.INFRA_FAILURE);

        assertThrows(IllegalStateException.class, () -> service.reconcile(snapshot));

        verify(anomalyMapper, never()).upsert(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
