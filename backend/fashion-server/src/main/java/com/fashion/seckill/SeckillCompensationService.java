package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.entity.SeckillMessageLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class SeckillCompensationService {
    public static final long EVIDENCE_INITIAL_DELIVERY_FAILED = 1L;
    public static final long EVIDENCE_CONSUME_EXHAUSTED = 1L << 1;
    public static final long EVIDENCE_CANCEL_COMMITTED = 1L << 2;
    public static final long EVIDENCE_ORPHAN_RECONCILED = 1L << 3;
    public static final long EVIDENCE_CANCELLED_ORDER_RECONCILED = 1L << 4;

    private final SeckillCompensationRecordMapper mapper;
    private final SeckillMessageLogMapper messageMapper;
    private final SeckillReconciliationAnomalyMapper anomalyMapper;

    public SeckillCompensationService(SeckillCompensationRecordMapper mapper) {
        this(mapper, null, null);
    }

    public SeckillCompensationService(SeckillCompensationRecordMapper mapper,
                                      SeckillMessageLogMapper messageMapper) {
        this(mapper, messageMapper, null);
    }

    @Autowired
    public SeckillCompensationService(SeckillCompensationRecordMapper mapper,
                                      SeckillMessageLogMapper messageMapper,
                                      SeckillReconciliationAnomalyMapper anomalyMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.messageMapper = messageMapper;
        this.anomalyMapper = anomalyMapper;
    }

    public void requestRelease(String orderNumber, Long userId, Long couponId,
                               String reason, long evidenceMask) {
        requireIdentity(orderNumber, userId, couponId);
        if (reason == null || reason.trim().isEmpty() || evidenceMask <= 0) {
            throw new IllegalArgumentException("invalid compensation evidence");
        }
        if (mapper.upsertReleaseReservation(orderNumber, userId, couponId, reason, evidenceMask) < 1) {
            throw new IllegalStateException("failed to persist compensation request");
        }
        SeckillCompensationRecord persisted = mapper.selectByOrderNumber(orderNumber);
        if (persisted == null || !Objects.equals(persisted.getUserId(), userId)
                || !Objects.equals(persisted.getCouponId(), couponId)) {
            mapper.markIdentityConflict(orderNumber, userId, couponId);
            throw new IllegalStateException("compensation identity conflict");
        }
    }

    @Transactional
    public void recordRollbackResult(SeckillCompensationRecord record,
                                     String worker,
                                     SeckillReservationService.RollbackResult result) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(worker, "worker");
        RollbackState state = rollbackState(record, result);
        if (mapper.markRollbackResultOwned(record.getId(), worker, state.status, state.error) != 1) {
            throw new IllegalStateException("compensation lease is no longer owned");
        }
        if (result == SeckillReservationService.RollbackResult.APPLIED_LEDGER_INCONSISTENT) {
            recordLedgerAnomaly(record);
        }
        if ("SUCCEEDED".equals(state.status)) convergeInitialMessage(record.getOrderNumber());
    }

    private void recordLedgerAnomaly(SeckillCompensationRecord record) {
        if (anomalyMapper == null) return;
        String raw = "LEDGER_CARDINALITY_MISMATCH:" + record.getCouponId() + ":"
                + record.getUserId() + ":" + record.getOrderNumber();
        String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .substring("INVALID:".length());
        if (anomalyMapper.upsert("LEDGER_CARDINALITY_MISMATCH", record.getCouponId(),
                record.getUserId(), record.getOrderNumber(), hash) < 1) {
            throw new IllegalStateException("compensation ledger anomaly was not persisted");
        }
    }

    public void convergeInitialMessage(String orderNumber) {
        if (messageMapper == null) return;
        SeckillMessageLog source = messageMapper.selectByMessageId("SECKILL_ORDER_CREATE:" + orderNumber);
        if (source == null || !"ORDER_CREATE".equals(source.getMessageType())
                || !"INITIAL".equals(source.getPublishPurpose())
                || !"COMPENSATION_PENDING".equals(source.getStatus())) {
            return;
        }
        if (messageMapper.markInitialCompensated(orderNumber) != 1) {
            SeckillMessageLog latest = messageMapper.selectByMessageId(
                    "SECKILL_ORDER_CREATE:" + orderNumber);
            if (latest != null && "ORDER_CREATE".equals(latest.getMessageType())
                    && "INITIAL".equals(latest.getPublishPurpose())
                    && "COMPENSATED".equals(latest.getStatus())) {
                return;
            }
            throw new IllegalStateException("initial compensation message state did not converge");
        }
    }

    private RollbackState rollbackState(SeckillCompensationRecord record,
                                        SeckillReservationService.RollbackResult result) {
        Objects.requireNonNull(result, "result");
        String status;
        String error = null;
        switch (result) {
            case APPLIED:
                status = "SUCCEEDED";
                break;
            case ALREADY_APPLIED:
                if ("SUCCEEDED".equals(record.getStatus())) {
                    status = "SUCCEEDED";
                } else {
                    status = "MANUAL_REQUIRED";
                    error = "reservation absent without durable applied evidence";
                }
                break;
            case APPLIED_LEDGER_INCONSISTENT:
                status = "SUCCEEDED";
                error = "reservation ledger inconsistent";
                break;
            case INFRA_FAILURE:
                status = "RETRY_PENDING";
                error = "reservation store unavailable";
                break;
            default:
                status = "MANUAL_REQUIRED";
                error = "reservation identity or ledger mismatch";
                break;
        }
        return new RollbackState(status, error);
    }

    private void requireIdentity(String orderNumber, Long userId, Long couponId) {
        if (orderNumber == null || !orderNumber.matches("[0-9]{1,50}")
                || userId == null || userId <= 0 || couponId == null || couponId <= 0) {
            throw new IllegalArgumentException("invalid compensation identity");
        }
    }

    private static final class RollbackState {
        private final String status;
        private final String error;

        private RollbackState(String status, String error) {
            this.status = status;
            this.error = error;
        }
    }
}
