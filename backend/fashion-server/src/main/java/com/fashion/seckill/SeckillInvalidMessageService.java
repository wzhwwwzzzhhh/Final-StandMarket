package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SeckillInvalidMessageService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SeckillInvalidMessageService.class);
    private final SeckillMessageLogMapper mapper;
    private final SeckillAfterCommitDispatcher afterCommit;

    public SeckillInvalidMessageService(SeckillMessageLogMapper mapper) {
        this(mapper, new SeckillAfterCommitDispatcher());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SeckillInvalidMessageService(SeckillMessageLogMapper mapper,
                                        SeckillAfterCommitDispatcher afterCommit) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.afterCommit = Objects.requireNonNull(afterCommit, "afterCommit");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Message envelope) {
        record(envelope, "ENVELOPE_INVALID");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Message envelope, String classification) {
        if (classification == null || !classification.matches("[A-Z_]{1,64}")) {
            throw new IllegalArgumentException("invalid quarantine classification");
        }
        String exchange = envelope.getMessageProperties().getReceivedExchange();
        String routingKey = envelope.getMessageProperties().getReceivedRoutingKey();
        String contentType = envelope.getMessageProperties().getContentType();
        String identity = SeckillMessageIdentity.quarantineKey(
                exchange, routingKey, contentType, envelope.getBody());
        String hash = identity.substring("INVALID:".length());
        String sourceId = envelope.getMessageProperties().getMessageId();
        LocalDateTime now = LocalDateTime.now();
        SeckillMessageLog source = new SeckillMessageLog();
        source.setMessageId(identity);
        source.setMessageType("INVALID_MESSAGE");
        source.setPublishPurpose("DEAD_LETTER");
        source.setBusinessKey(hash);
        if (sourceId != null) {
            source.setSourceMessageIdHash(SeckillMessageIdentity.sha256(sourceId));
            source.setSourceMessageIdPrefix(safePrefix(sourceId));
        }
        source.setBodySha256(hash);
        source.setBodySize((long) envelope.getBody().length);
        source.setPayload("{\"quarantineId\":\"" + identity + "\",\"bodySize\":"
                + envelope.getBody().length + "}");
        source.setPayloadSchemaVersion(1);
        source.setExchangeName(exchange == null ? "" : exchange);
        source.setRoutingKey(routingKey == null ? "" : routingKey);
        source.setStatus("CONSUME_EXHAUSTED");
        source.setDeadLetterStatus("PENDING");
        source.setLastError(classification);
        initialize(source, now);
        boolean sourceCreated = insertIdempotently(source, "invalid message not persisted");

        SeckillMessageLog deadLetter = new SeckillMessageLog();
        deadLetter.setMessageId("SECKILL_DEAD:" + identity);
        deadLetter.setMessageType("BUSINESS_DEAD_LETTER");
        deadLetter.setPublishPurpose("DEAD_LETTER");
        deadLetter.setBusinessKey(identity);
        deadLetter.setSourceMessageId(identity);
        deadLetter.setSourceMessageIdHash(SeckillMessageIdentity.sha256(identity));
        deadLetter.setSourceMessageIdPrefix(identity.substring(0, Math.min(64, identity.length())));
        deadLetter.setBodySha256(hash);
        deadLetter.setBodySize((long) envelope.getBody().length);
        deadLetter.setPayload("{\"sourceMessageIdHash\":\""
                + SeckillMessageIdentity.sha256(identity)
                + "\",\"messageType\":\"INVALID_MESSAGE\",\"attempt\":0}");
        deadLetter.setPayloadSchemaVersion(1);
        deadLetter.setExchangeName(DirectExchangeConfig.SECKILL_FAILURE_EXCHANGE);
        deadLetter.setRoutingKey(DirectExchangeConfig.SECKILL_INVALID_FAILURE_ROUTING_KEY);
        deadLetter.setStatus("PREPARED");
        deadLetter.setDeadLetterStatus("NONE");
        initialize(deadLetter, now);
        insertIdempotently(deadLetter, "invalid message dead letter not persisted");
        if (sourceCreated) {
            afterCommit.run(() -> LOG.error(
                    "SECKILL_MQ_DEAD_LETTER messageId={} type=INVALID_MESSAGE classification={}",
                    identity, classification));
        }
    }

    private void initialize(SeckillMessageLog log, LocalDateTime now) {
        log.setConfirmStatus("PENDING");
        log.setReturned(false);
        log.setPublishAttempt(0);
        log.setConsumeAttempt(0);
        log.setVersion(0L);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
    }

    private boolean insertIdempotently(SeckillMessageLog log, String failureMessage) {
        try {
            if (mapper.insert(log) != 1) throw new IllegalStateException(failureMessage);
            return true;
        } catch (DuplicateKeyException alreadyRecorded) {
            // Stable source and DLQ identities make repeated broker deliveries idempotent.
            return false;
        }
    }

    private String safePrefix(String sourceId) {
        StringBuilder safe = new StringBuilder(Math.min(64, sourceId.length()));
        for (int index = 0; index < sourceId.length() && safe.length() < 64; index++) {
            char value = sourceId.charAt(index);
            safe.append((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9') || value == ':' || value == '.'
                    || value == '_' || value == '-' ? value : '_');
        }
        return safe.toString();
    }
}
