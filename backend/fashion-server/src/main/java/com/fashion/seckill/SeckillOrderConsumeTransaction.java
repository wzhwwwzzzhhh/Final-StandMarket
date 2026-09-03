package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.entity.SeckillMessage;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SeckillOrderConsumeTransaction {
    public enum Result { CREATED, DUPLICATE, IGNORED_TERMINAL }

    private final SeckillOrderMapper orderMapper;
    private final SeckillCouponMapper couponMapper;
    private final SeckillMessageLogMapper messageMapper;
    private final SeckillAfterCommitDispatcher afterCommit;
    private final SeckillReliablePublisher publisher;

    public SeckillOrderConsumeTransaction(SeckillOrderMapper orderMapper,
                                          SeckillCouponMapper couponMapper,
                                          SeckillMessageLogMapper messageMapper,
                                          SeckillAfterCommitDispatcher afterCommit,
                                          SeckillReliablePublisher publisher) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper");
        this.couponMapper = Objects.requireNonNull(couponMapper, "couponMapper");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
        this.afterCommit = Objects.requireNonNull(afterCommit, "afterCommit");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Transactional
    public Result consume(SeckillMessage message, String sourceMessageId, int incomingAttempt) {
        validate(message, sourceMessageId);
        if (incomingAttempt < 1 || incomingAttempt > 3) {
            throw new IllegalArgumentException("invalid consume attempt");
        }
        String claimToken = "order-" + UUID.randomUUID();
        if (messageMapper.claimConsumeAttempt(sourceMessageId, incomingAttempt,
                message.getOrderNumber(), message.getUserId(), message.getCouponId(), claimToken) != 1) {
            SeckillMessageLog source = messageMapper.selectByMessageId(sourceMessageId);
            if (isEquivalentTerminalOrStale(source, message, incomingAttempt)) {
                return Result.IGNORED_TERMINAL;
            }
            throw new IllegalStateException("ORDER_CREATE attempt could not be claimed");
        }
        SeckillOrder existing = orderMapper.selectByOrderNumber(message.getOrderNumber());
        if (existing != null) {
            if (!Objects.equals(existing.getUserId(), message.getUserId())
                    || !Objects.equals(existing.getCouponId(), message.getCouponId())) {
                throw new IllegalStateException("order number belongs to different seckill identity");
            }
            if (messageMapper.markConsumedAttempt(sourceMessageId, incomingAttempt, claimToken) != 1) {
                throw new IllegalStateException("duplicate consume result could not be persisted");
            }
            return Result.DUPLICATE;
        }

        LocalDateTime createdAt = LocalDateTime.now();
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber(message.getOrderNumber());
        order.setUserId(message.getUserId());
        order.setCouponId(message.getCouponId());
        order.setStatus(1);
        order.setCreateTime(createdAt);
        orderMapper.insert(order);
        if (couponMapper.reduceStock(message.getCouponId()) != 1) {
            throw new IllegalStateException("seckill database stock unavailable");
        }

        SeckillMessageLog timeout = timeoutLog(order, createdAt);
        if (messageMapper.insert(timeout) != 1
                || messageMapper.markConsumedAttempt(sourceMessageId, incomingAttempt, claimToken) != 1) {
            throw new IllegalStateException("failed to atomically persist consume result");
        }
        afterCommit.run(() -> publisher.publish(timeout.getMessageId(), "TIMEOUT_RECOVERY"));
        return Result.CREATED;
    }

    public void validateSourceIdentity(SeckillMessage message, String sourceMessageId) {
        try {
            validate(message, sourceMessageId);
        } catch (IllegalArgumentException invalid) {
            throw new SeckillPermanentEnvelopeException("invalid ORDER_CREATE identity");
        }
        SeckillMessageLog source = messageMapper.selectByMessageId(sourceMessageId);
        if (source == null || !"ORDER_CREATE".equals(source.getMessageType())
                || !Objects.equals(source.getBusinessKey(), message.getOrderNumber())
                || !Objects.equals(source.getUserId(), message.getUserId())
                || !Objects.equals(source.getCouponId(), message.getCouponId())) {
            throw new SeckillPermanentEnvelopeException("ORDER_CREATE source identity mismatch");
        }
    }

    private boolean isEquivalentTerminalOrStale(SeckillMessageLog source,
                                                 SeckillMessage message,
                                                 int incomingAttempt) {
        if (source == null || !"ORDER_CREATE".equals(source.getMessageType())
                || !Objects.equals(source.getBusinessKey(), message.getOrderNumber())
                || !Objects.equals(source.getUserId(), message.getUserId())
                || !Objects.equals(source.getCouponId(), message.getCouponId())) {
            return false;
        }
        String status = source.getStatus();
        boolean terminal = "CONSUMED".equals(status) || "CONSUME_EXHAUSTED".equals(status)
                || "COMPENSATION_PENDING".equals(status) || "COMPENSATED".equals(status)
                || "MANUAL_REQUIRED".equals(status);
        return terminal || (source.getConsumeAttempt() != null
                && source.getConsumeAttempt() >= incomingAttempt);
    }

    private SeckillMessageLog timeoutLog(SeckillOrder order, LocalDateTime createdAt) {
        SeckillMessageLog log = new SeckillMessageLog();
        log.setMessageId("SECKILL_ORDER_TIMEOUT:" + order.getOrderNumber());
        log.setMessageType("ORDER_TIMEOUT");
        log.setPublishPurpose("TIMEOUT_RECOVERY");
        log.setBusinessKey(order.getOrderNumber());
        log.setUserId(order.getUserId());
        log.setCouponId(order.getCouponId());
        log.setPayload(String.valueOf(order.getId()));
        log.setPayloadSchemaVersion(1);
        log.setExchangeName(DirectExchangeConfig.delayExchange);
        log.setRoutingKey(DirectExchangeConfig.delayRoutingKey);
        log.setStatus("PREPARED");
        log.setDeadLetterStatus("NONE");
        log.setConfirmStatus("PENDING");
        log.setReturned(false);
        log.setPublishAttempt(0);
        log.setConsumeAttempt(0);
        log.setDueAt(createdAt.plusMinutes(30));
        log.setVersion(0L);
        log.setCreatedAt(createdAt);
        log.setUpdatedAt(createdAt);
        return log;
    }

    private void validate(SeckillMessage message, String sourceMessageId) {
        if (message == null || message.getUserId() == null || message.getUserId() <= 0
                || message.getCouponId() == null || message.getCouponId() <= 0
                || message.getOrderNumber() == null
                || !message.getOrderNumber().matches("[0-9]{1,50}")
                || !Objects.equals("SECKILL_ORDER_CREATE:" + message.getOrderNumber(), sourceMessageId)) {
            throw new IllegalArgumentException("invalid seckill ORDER_CREATE envelope");
        }
    }
}
