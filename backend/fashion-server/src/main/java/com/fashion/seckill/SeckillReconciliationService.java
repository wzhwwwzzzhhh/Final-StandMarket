package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

@Service
public class SeckillReconciliationService {
    private final SeckillOrderMapper orderMapper;
    private final SeckillMessageLogMapper messageMapper;
    private final SeckillCompensationExecutor compensationExecutor;
    private final SeckillOrphanClaimTransaction orphanClaimTransaction;
    private final SeckillReconciliationAnomalyMapper anomalyMapper;
    private final SeckillReconciliationPolicy policy;

    public SeckillReconciliationService(SeckillOrderMapper orderMapper,
                                        SeckillMessageLogMapper messageMapper,
                                        SeckillCompensationExecutor compensationExecutor,
                                        SeckillOrphanClaimTransaction orphanClaimTransaction,
                                        SeckillReconciliationAnomalyMapper anomalyMapper) {
        this(orderMapper, messageMapper, compensationExecutor,
                orphanClaimTransaction, anomalyMapper,
                new SeckillReconciliationPolicy(Duration.ofMinutes(5)));
    }

    SeckillReconciliationService(SeckillOrderMapper orderMapper,
                                 SeckillMessageLogMapper messageMapper,
                                 SeckillCompensationExecutor compensationExecutor,
                                 SeckillOrphanClaimTransaction orphanClaimTransaction,
                                 SeckillReconciliationAnomalyMapper anomalyMapper,
                                 SeckillReconciliationPolicy policy) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
        this.compensationExecutor = Objects.requireNonNull(compensationExecutor, "compensationExecutor");
        this.orphanClaimTransaction = Objects.requireNonNull(orphanClaimTransaction, "orphanClaimTransaction");
        this.anomalyMapper = Objects.requireNonNull(anomalyMapper, "anomalyMapper");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public SeckillReconciliationPolicy.Action reconcile(SeckillReservationSnapshot snapshot) {
        Integer orderStatus = null;
        String messageStatus = null;
        if (snapshot.getOrderNumber() != null) {
            SeckillOrder order = orderMapper.selectByOrderNumber(snapshot.getOrderNumber());
            if (order != null && (!Objects.equals(order.getUserId(), snapshot.getUserId())
                    || !Objects.equals(order.getCouponId(), snapshot.getCouponId()))) {
                anomaly(snapshot, "ORDER_IDENTITY_MISMATCH");
                return SeckillReconciliationPolicy.Action.MANUAL_REQUIRED;
            }
            orderStatus = order == null ? null : order.getStatus();
            SeckillMessageLog message = messageMapper.selectByMessageId(
                    "SECKILL_ORDER_CREATE:" + snapshot.getOrderNumber());
            messageStatus = message == null ? null : message.getStatus();
        }
        SeckillReconciliationPolicy.Action action = policy.decide(
                snapshot.isHashTokenPresent(), snapshot.isZsetMemberPresent(),
                orderStatus, messageStatus, snapshot.getAge());
        if (action == SeckillReconciliationPolicy.Action.RELEASE) {
            if (orphanClaimTransaction.claim(snapshot) != SeckillOrphanClaimTransaction.Result.CLAIMED) {
                return SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY;
            }
            SeckillReservationService.RollbackResult result =
                    compensationExecutor.execute(snapshot.getOrderNumber());
            if (result == SeckillReservationService.RollbackResult.INFRA_FAILURE) {
                if (compensationExecutor.isClaimActiveOrSucceeded(snapshot.getOrderNumber())) {
                    return SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY;
                }
                throw new IllegalStateException("reservation compensation is temporarily unavailable");
            } else if (result == SeckillReservationService.RollbackResult.APPLIED_LEDGER_INCONSISTENT) {
                anomaly(snapshot, "LEDGER_CARDINALITY_MISMATCH");
                return SeckillReconciliationPolicy.Action.MANUAL_REQUIRED;
            } else if (result == SeckillReservationService.RollbackResult.TOKEN_MISMATCH
                    || result == SeckillReservationService.RollbackResult.LEDGER_CORRUPT
                    || result == SeckillReservationService.RollbackResult.INVALID) {
                anomaly(snapshot, "COMPENSATION_TOKEN_MISMATCH");
                return SeckillReconciliationPolicy.Action.MANUAL_REQUIRED;
            }
        } else if (action == SeckillReconciliationPolicy.Action.RETRY_DELIVERY) {
            String messageId = "SECKILL_ORDER_CREATE:" + snapshot.getOrderNumber();
            if (messageMapper.scheduleReconciliationRedelivery(messageId, 5) != 1) {
                SeckillMessageLog current = messageMapper.selectByMessageId(messageId);
                if (current != null && ("RETRY_PUBLISH_PENDING".equals(current.getStatus())
                        || "SENT".equals(current.getStatus()) || "BROKER_ACKED".equals(current.getStatus())
                        || "PROCESSING".equals(current.getStatus()) || "CONSUMED".equals(current.getStatus()))) {
                    return SeckillReconciliationPolicy.Action.WAIT_FOR_DELIVERY;
                }
                anomaly(snapshot, "DELIVERY_RECOVERY_CONFLICT");
                return SeckillReconciliationPolicy.Action.MANUAL_REQUIRED;
            }
        } else if (action == SeckillReconciliationPolicy.Action.MANUAL_REQUIRED) {
            String anomalyType = !snapshot.isHashTokenPresent() && !snapshot.isZsetMemberPresent()
                    && orderStatus != null && (orderStatus == 1 || orderStatus == 2)
                    ? "ACTIVE_ORDER_RESERVATION_MISSING" : "LEDGER_CARDINALITY_MISMATCH";
            anomaly(snapshot, anomalyType);
        }
        return action;
    }

    private void anomaly(SeckillReservationSnapshot snapshot, String type) {
        String raw = type + ":" + snapshot.getCouponId() + ":" + snapshot.getUserId()
                + ":" + snapshot.getOrderNumber();
        String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                raw.getBytes(StandardCharsets.UTF_8)).substring("INVALID:".length());
        anomalyMapper.upsert(type, snapshot.getCouponId(), snapshot.getUserId(),
                snapshot.getOrderNumber(), hash);
    }
}
