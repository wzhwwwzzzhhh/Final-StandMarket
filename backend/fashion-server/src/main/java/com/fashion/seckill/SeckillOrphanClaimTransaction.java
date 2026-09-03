package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.entity.SeckillOrder;
import com.fashion.config.DirectExchangeConfig;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.time.LocalDateTime;

@Service
public class SeckillOrphanClaimTransaction {
    public enum Result { CLAIMED, MESSAGE_EXISTS, ORDER_EXISTS }

    private final SeckillMessageLogMapper messageMapper;
    private final SeckillOrderMapper orderMapper;
    private final SeckillCompensationService compensationService;

    public SeckillOrphanClaimTransaction(SeckillMessageLogMapper messageMapper,
                                         SeckillOrderMapper orderMapper,
                                         SeckillCompensationService compensationService) {
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Result claim(SeckillReservationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String messageId = "SECKILL_ORDER_CREATE:" + snapshot.getOrderNumber();
        SeckillOrder order = orderMapper.selectByOrderNumber(snapshot.getOrderNumber());
        if (order != null) {
            if (!Integer.valueOf(3).equals(order.getStatus())
                    || !Objects.equals(order.getUserId(), snapshot.getUserId())
                    || !Objects.equals(order.getCouponId(), snapshot.getCouponId())) {
                return Result.ORDER_EXISTS;
            }
            compensationService.requestRelease(snapshot.getOrderNumber(), snapshot.getUserId(),
                    snapshot.getCouponId(), "CANCELLED_ORDER_RECONCILED",
                    SeckillCompensationService.EVIDENCE_CANCELLED_ORDER_RECONCILED);
            return Result.CLAIMED;
        }
        SeckillMessageLog fence = orphanFence(snapshot, messageId);
        messageMapper.insertIfAbsent(fence);
        SeckillMessageLog winner = messageMapper.selectByMessageIdForUpdate(messageId);
        if (!isOrphanFence(winner, snapshot)) return Result.MESSAGE_EXISTS;
        compensationService.requestRelease(snapshot.getOrderNumber(), snapshot.getUserId(),
                snapshot.getCouponId(), "ORPHAN_RECONCILED",
                SeckillCompensationService.EVIDENCE_ORPHAN_RECONCILED);
        return Result.CLAIMED;
    }

    private boolean isOrphanFence(SeckillMessageLog log, SeckillReservationSnapshot snapshot) {
        return log != null && "ORDER_CREATE".equals(log.getMessageType())
                && "INITIAL".equals(log.getPublishPurpose())
                && "COMPENSATION_PENDING".equals(log.getStatus())
                && "ORPHAN_FENCE".equals(log.getLastError())
                && Objects.equals(log.getBusinessKey(), snapshot.getOrderNumber())
                && Objects.equals(log.getUserId(), snapshot.getUserId())
                && Objects.equals(log.getCouponId(), snapshot.getCouponId());
    }

    private SeckillMessageLog orphanFence(SeckillReservationSnapshot snapshot, String messageId) {
        LocalDateTime now = LocalDateTime.now();
        SeckillMessageLog fence = new SeckillMessageLog();
        fence.setMessageId(messageId);
        fence.setMessageType("ORDER_CREATE");
        fence.setPublishPurpose("INITIAL");
        fence.setBusinessKey(snapshot.getOrderNumber());
        fence.setUserId(snapshot.getUserId());
        fence.setCouponId(snapshot.getCouponId());
        fence.setPayload("{}");
        fence.setPayloadSchemaVersion(1);
        fence.setExchangeName(DirectExchangeConfig.SeckillExchange);
        fence.setRoutingKey(DirectExchangeConfig.SeckillRoutingKey);
        fence.setStatus("COMPENSATION_PENDING");
        fence.setDeadLetterStatus("NONE");
        fence.setConfirmStatus("PENDING");
        fence.setReturned(false);
        fence.setPublishAttempt(0);
        fence.setConsumeAttempt(0);
        fence.setVersion(0L);
        fence.setLastError("ORPHAN_FENCE");
        fence.setCreatedAt(now);
        fence.setUpdatedAt(now);
        return fence;
    }
}
