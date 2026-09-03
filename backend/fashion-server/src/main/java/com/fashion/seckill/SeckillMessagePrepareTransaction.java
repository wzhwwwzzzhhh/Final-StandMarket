package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SeckillMessagePrepareTransaction {
    private final SeckillMessageLogMapper mapper;
    private final SeckillCompensationRecordMapper compensationMapper;

    public SeckillMessagePrepareTransaction(SeckillMessageLogMapper mapper,
                                            SeckillCompensationRecordMapper compensationMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.compensationMapper = Objects.requireNonNull(compensationMapper, "compensationMapper");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public SeckillMessageLog prepareOrderCreate(String orderNumber,
                                                Long userId,
                                                Long couponId,
                                                String payload) {
        if (orderNumber == null || !orderNumber.matches("[0-9]{1,50}")
                || userId == null || couponId == null || payload == null) {
            throw new IllegalArgumentException("invalid ORDER_CREATE message");
        }
        String messageId = "SECKILL_ORDER_CREATE:" + orderNumber;
        if (compensationMapper.selectByOrderNumber(orderNumber) != null) {
            throw new IllegalStateException("reservation has already entered compensation");
        }
        LocalDateTime now = LocalDateTime.now();
        SeckillMessageLog log = new SeckillMessageLog();
        log.setMessageId(messageId);
        log.setMessageType("ORDER_CREATE");
        log.setPublishPurpose("INITIAL");
        log.setBusinessKey(orderNumber);
        log.setUserId(userId);
        log.setCouponId(couponId);
        log.setPayload(payload);
        log.setPayloadSchemaVersion(1);
        log.setExchangeName(DirectExchangeConfig.SeckillExchange);
        log.setRoutingKey(DirectExchangeConfig.SeckillRoutingKey);
        log.setStatus("PREPARED");
        log.setDeadLetterStatus("NONE");
        log.setConfirmStatus("PENDING");
        log.setReturned(false);
        log.setPublishAttempt(0);
        log.setConsumeAttempt(0);
        log.setVersion(0L);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        if (mapper.insert(log) != 1) {
            throw new IllegalStateException("failed to persist ORDER_CREATE PREPARED");
        }
        return log;
    }
}
