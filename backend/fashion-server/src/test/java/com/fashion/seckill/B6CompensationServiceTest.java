package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 补偿身份与结果门禁")
class B6CompensationServiceTest {
    @Test
    @DisplayName("同订单号的 user/coupon 冲突必须转人工而非沿用旧身份")
    void identityConflictIsManualRequired() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillCompensationRecord existing = record("9001", 8L, 19L);
        when(mapper.upsertReleaseReservation("9001", 7L, 19L, "ORPHAN_RECONCILED", 8L))
                .thenReturn(2);
        when(mapper.selectByOrderNumber("9001")).thenReturn(existing);
        when(mapper.markIdentityConflict("9001", 7L, 19L)).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> new SeckillCompensationService(mapper).requestRelease(
                        "9001", 7L, 19L, "ORPHAN_RECONCILED", 8L));

        verify(mapper).markIdentityConflict("9001", 7L, 19L);
    }

    @Test
    @DisplayName("Redis 释放成功后收敛 initial 源消息为 COMPENSATED")
    void successfulRollbackConvergesInitialMessageState() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillCompensationRecord record = record("9001", 7L, 19L);
        record.setId(1L);
        record.setStatus("IN_PROGRESS");
        when(mapper.markRollbackResultOwned(record.getId(), "worker", "SUCCEEDED", null)).thenReturn(1);
        com.fashion.entity.SeckillMessageLog source = new com.fashion.entity.SeckillMessageLog();
        source.setMessageId("SECKILL_ORDER_CREATE:9001");
        source.setMessageType("ORDER_CREATE");
        source.setPublishPurpose("INITIAL");
        source.setStatus("COMPENSATION_PENDING");
        when(messages.selectByMessageId(source.getMessageId())).thenReturn(source);
        when(messages.markInitialCompensated("9001")).thenReturn(1);

        new SeckillCompensationService(mapper, messages).recordRollbackResult(
                record, "worker", SeckillReservationService.RollbackResult.APPLIED);

        verify(messages).markInitialCompensated("9001");
    }

    @Test
    @DisplayName("并发收敛 CAS 败者重读到 COMPENSATED 时按幂等成功")
    void concurrentConvergenceAcceptsEquivalentTerminalState() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        com.fashion.entity.SeckillMessageLog pending = new com.fashion.entity.SeckillMessageLog();
        pending.setMessageId("SECKILL_ORDER_CREATE:9001");
        pending.setMessageType("ORDER_CREATE");
        pending.setPublishPurpose("INITIAL");
        pending.setStatus("COMPENSATION_PENDING");
        com.fashion.entity.SeckillMessageLog compensated = new com.fashion.entity.SeckillMessageLog();
        compensated.setMessageId(pending.getMessageId());
        compensated.setMessageType("ORDER_CREATE");
        compensated.setPublishPurpose("INITIAL");
        compensated.setStatus("COMPENSATED");
        when(messages.selectByMessageId(pending.getMessageId())).thenReturn(pending, compensated);

        new SeckillCompensationService(mapper, messages).convergeInitialMessage("9001");

        verify(messages).markInitialCompensated("9001");
    }

    @Test
    @DisplayName("首次发现 Redis token 与账本均缺失不得冒充已完成")
    void firstAlreadyAppliedResultIsManualRequired() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillCompensationRecord record = record("9001", 7L, 19L);
        record.setStatus("IN_PROGRESS");
        when(mapper.markRollbackResultOwned(record.getId(), "worker", "MANUAL_REQUIRED",
                "reservation absent without durable applied evidence")).thenReturn(1);

        new SeckillCompensationService(mapper).recordRollbackResult(
                record, "worker", SeckillReservationService.RollbackResult.ALREADY_APPLIED);

        verify(mapper).markRollbackResultOwned(record.getId(), "worker", "MANUAL_REQUIRED",
                "reservation absent without durable applied evidence");
    }

    @Test
    @DisplayName("非对账路径发现其他账本不一致时与补偿成功一并留下 anomaly")
    void appliedWithLedgerInconsistencyPersistsAnomaly() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillMessageLogMapper messages = mock(SeckillMessageLogMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        SeckillCompensationRecord record = record("9001", 7L, 19L);
        record.setId(1L);
        record.setStatus("IN_PROGRESS");
        when(mapper.markRollbackResultOwned(1L, "worker", "SUCCEEDED",
                "reservation ledger inconsistent")).thenReturn(1);
        when(anomalies.upsert(org.mockito.ArgumentMatchers.eq("LEDGER_CARDINALITY_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        new SeckillCompensationService(mapper, messages, anomalies).recordRollbackResult(
                record, "worker", SeckillReservationService.RollbackResult.APPLIED_LEDGER_INCONSISTENT);

        verify(anomalies).upsert(org.mockito.ArgumentMatchers.eq("LEDGER_CARDINALITY_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("9001"),
                org.mockito.ArgumentMatchers.anyString());
    }

    private SeckillCompensationRecord record(String orderNumber, Long userId, Long couponId) {
        SeckillCompensationRecord record = new SeckillCompensationRecord();
        record.setOrderNumber(orderNumber);
        record.setUserId(userId);
        record.setCouponId(couponId);
        return record;
    }
}
