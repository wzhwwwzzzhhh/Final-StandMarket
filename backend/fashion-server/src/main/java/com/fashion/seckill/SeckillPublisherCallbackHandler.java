package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class SeckillPublisherCallbackHandler {
    private final SeckillMessageLogMapper mapper;
    private final SeckillPublishCallbackPolicy policy;
    private final SeckillCompensationService compensationService;

    public SeckillPublisherCallbackHandler(SeckillMessageLogMapper mapper,
                                           SeckillPublishCallbackPolicy policy,
                                           SeckillCompensationService compensationService) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
    }

    @Transactional
    public SeckillPublishCallbackPolicy.Action handleConfirm(String correlationId,
                                                             boolean acknowledged,
                                                             String cause) {
        String messageId = logicalMessageId(correlationId);
        SeckillMessageLog log = requireLog(messageId);
        if (Objects.equals(log.getCurrentCorrelationId(), correlationId)
                && !"PENDING".equals(log.getConfirmStatus())) {
            mapper.appendCallbackAudit(messageId, correlationId, cause);
            return SeckillPublishCallbackPolicy.Action.AUDIT_ONLY;
        }
        SeckillPublishCallbackPolicy.Action action = policy.decide(
                messageType(log), purpose(log), log.getCurrentCorrelationId(), correlationId,
                isTerminal(log.getStatus()), Boolean.TRUE.equals(log.getReturned()), acknowledged);
        persist(log, correlationId, action, cause);
        return action;
    }

    @Transactional
    public SeckillPublishCallbackPolicy.Action handleReturn(String messageId,
                                                            int publishAttempt,
                                                            int replyCode,
                                                            String replyText,
                                                            String exchange,
                                                            String routingKey) {
        String correlationId = messageId + ":P" + publishAttempt;
        if (mapper.recordReturn(messageId, correlationId, replyCode, replyText, exchange, routingKey) != 1) {
            mapper.appendCallbackAudit(messageId, correlationId, replyText);
            return SeckillPublishCallbackPolicy.Action.AUDIT_ONLY;
        }
        SeckillMessageLog log = requireLog(messageId);
        SeckillPublishCallbackPolicy.Action action = policy.decide(
                messageType(log), purpose(log), log.getCurrentCorrelationId(), correlationId,
                isTerminal(log.getStatus()), true, true);
        persist(log, correlationId, action, replyText);
        return action;
    }

    private void persist(SeckillMessageLog log,
                         String correlationId,
                         SeckillPublishCallbackPolicy.Action action,
                         String summary) {
        if (action == SeckillPublishCallbackPolicy.Action.AUDIT_ONLY) {
            mapper.appendCallbackAudit(log.getMessageId(), correlationId, summary);
            return;
        }
        if (mapper.applyCallbackAction(log.getMessageId(), correlationId, action.name(), summary) != 1) {
            mapper.appendCallbackAudit(log.getMessageId(), correlationId, summary);
            return;
        }
        if ("BUSINESS_DEAD_LETTER".equals(log.getMessageType())) {
            String sourceStatus = action == SeckillPublishCallbackPolicy.Action.MARK_ACKED
                    ? "ACKED" : "PENDING";
            if (log.getSourceMessageId() == null
                    || mapper.updateSourceDeadLetterStatus(log.getSourceMessageId(), sourceStatus) != 1) {
                throw new IllegalStateException("business dead letter source status is unavailable");
            }
        }
        if (action == SeckillPublishCallbackPolicy.Action.COMPENSATE_RESERVATION) {
            compensationService.requestRelease(log.getBusinessKey(), log.getUserId(), log.getCouponId(),
                    "INITIAL_DELIVERY_FAILED",
                    SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        }
    }

    private String logicalMessageId(String correlationId) {
        if (correlationId == null) throw new IllegalArgumentException("missing correlationId");
        int marker = correlationId.lastIndexOf(":P");
        if (marker < 1 || marker + 2 >= correlationId.length()
                || !correlationId.substring(marker + 2).matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("invalid correlationId");
        }
        return correlationId.substring(0, marker);
    }

    private SeckillMessageLog requireLog(String messageId) {
        SeckillMessageLog log = mapper.selectByMessageId(messageId);
        if (log == null) throw new IllegalStateException("unknown messageId");
        return log;
    }

    private SeckillPublishCallbackPolicy.MessageType messageType(SeckillMessageLog log) {
        return SeckillPublishCallbackPolicy.MessageType.valueOf(log.getMessageType());
    }

    private SeckillPublishCallbackPolicy.Purpose purpose(SeckillMessageLog log) {
        return SeckillPublishCallbackPolicy.Purpose.valueOf(log.getPublishPurpose());
    }

    private boolean isTerminal(String status) {
        return "PROCESSING".equals(status) || "CONSUMED".equals(status)
                || "CONSUME_EXHAUSTED".equals(status)
                || "COMPENSATION_PENDING".equals(status)
                || "COMPENSATED".equals(status) || "MANUAL_REQUIRED".equals(status);
    }
}
