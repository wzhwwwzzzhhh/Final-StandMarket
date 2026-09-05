package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SeckillBusinessDeadLetterService {
    private final SeckillMessageLogMapper mapper;
    private final SeckillCompensationService compensationService;
    private final SeckillAfterCommitDispatcher afterCommit;

    @Autowired
    public SeckillBusinessDeadLetterService(SeckillMessageLogMapper mapper,
                                            SeckillCompensationService compensationService) {
        this(mapper, compensationService, new SeckillAfterCommitDispatcher());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SeckillBusinessDeadLetterService(SeckillMessageLogMapper mapper,
                                            SeckillCompensationService compensationService,
                                            SeckillAfterCommitDispatcher afterCommit) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
        this.afterCommit = Objects.requireNonNull(afterCommit, "afterCommit");
    }

    @Transactional
    public void createForExhaustedOrder(SeckillMessageLog source) {
        boolean created = create(source, "ORDER_CREATE");
        compensationService.requestRelease(source.getBusinessKey(), source.getUserId(), source.getCouponId(),
                "CONSUME_EXHAUSTED", SeckillCompensationService.EVIDENCE_CONSUME_EXHAUSTED);
        if (created) afterCommit.run(() -> emitSignal(source, "ORDER_CREATE"));
    }

    @Transactional
    public void createForExhaustedTimeout(SeckillMessageLog source) {
        boolean created = create(source, "ORDER_TIMEOUT");
        if (created) afterCommit.run(() -> emitSignal(source, "ORDER_TIMEOUT"));
    }

    private boolean create(SeckillMessageLog source, String sourceType) {
        if (source == null || source.getMessageId() == null || source.getBusinessKey() == null) {
            throw new IllegalArgumentException("invalid exhausted message");
        }
        String hash = SeckillMessageIdentity.sha256(source.getMessageId());
        LocalDateTime now = LocalDateTime.now();
        SeckillMessageLog deadLetter = new SeckillMessageLog();
        deadLetter.setMessageId("SECKILL_DEAD:" + source.getMessageId());
        deadLetter.setMessageType("BUSINESS_DEAD_LETTER");
        deadLetter.setPublishPurpose("DEAD_LETTER");
        deadLetter.setBusinessKey(source.getMessageId());
        deadLetter.setSourceMessageId(source.getMessageId());
        deadLetter.setSourceMessageIdHash(hash);
        deadLetter.setSourceMessageIdPrefix(source.getMessageId().substring(0,
                Math.min(64, source.getMessageId().length())));
        deadLetter.setBodySha256(source.getBodySha256());
        deadLetter.setUserId(source.getUserId());
        deadLetter.setCouponId(source.getCouponId());
        int exhaustedAttempt = source.getConsumeAttempt() == null ? 0 : source.getConsumeAttempt();
        deadLetter.setPayload("{\"sourceMessageIdHash\":\"" + hash
                + "\",\"messageType\":\"" + sourceType + "\",\"attempt\":"
                + exhaustedAttempt + "}");
        deadLetter.setPayloadSchemaVersion(1);
        deadLetter.setExchangeName(DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE);
        deadLetter.setRoutingKey("ORDER_TIMEOUT".equals(sourceType)
                ? DirectExchangeConfig.SECKILL_TIMEOUT_FAILURE_ROUTING_KEY
                : DirectExchangeConfig.SECKILL_ORDER_FAILURE_ROUTING_KEY);
        deadLetter.setStatus("PREPARED");
        deadLetter.setDeadLetterStatus("NONE");
        deadLetter.setConfirmStatus("PENDING");
        deadLetter.setReturned(false);
        deadLetter.setPublishAttempt(0);
        deadLetter.setConsumeAttempt(0);
        deadLetter.setVersion(0L);
        deadLetter.setCreatedAt(now);
        deadLetter.setUpdatedAt(now);
        try {
            if (mapper.insert(deadLetter) != 1) {
                throw new IllegalStateException("failed to persist business dead letter");
            }
            return true;
        } catch (DuplicateKeyException alreadyRecorded) {
            // Deterministic DLQ identity makes repeated deliveries idempotent.
            return false;
        }
    }

    private void emitSignal(SeckillMessageLog source, String sourceType) {
        log.error("SECKILL_MQ_DEAD_LETTER messageId={} orderNumber={} type={} attempt={}",
                source.getMessageId(), source.getBusinessKey(), sourceType, source.getConsumeAttempt());
    }
}
