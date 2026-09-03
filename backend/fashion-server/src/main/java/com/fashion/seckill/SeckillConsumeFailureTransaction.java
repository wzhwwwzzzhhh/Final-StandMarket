package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class SeckillConsumeFailureTransaction {
    public enum Outcome { RETRY, DEAD_LETTER, MANUAL_REQUIRED }

    private final SeckillMessageLogMapper mapper;
    private final SeckillBusinessDeadLetterService deadLetterService;

    public SeckillConsumeFailureTransaction(SeckillMessageLogMapper mapper,
                                            SeckillBusinessDeadLetterService deadLetterService) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.deadLetterService = Objects.requireNonNull(deadLetterService, "deadLetterService");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome record(String messageId, int incomingAttempt, String summary) {
        return record(messageId, incomingAttempt, summary, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome recordTimeout(String messageId, int incomingAttempt, String summary,
                                 String claimToken) {
        if (claimToken == null || claimToken.length() > 128) {
            throw new IllegalArgumentException("invalid timeout claim token");
        }
        return record(messageId, incomingAttempt, summary, claimToken);
    }

    private Outcome record(String messageId, int incomingAttempt, String summary,
                           String timeoutClaimToken) {
        if (messageId == null || messageId.length() > 128) {
            throw new IllegalArgumentException("invalid message identity");
        }
        if (incomingAttempt < 1 || incomingAttempt > 3) {
            throw new IllegalArgumentException("invalid consume attempt");
        }
        String safeSummary = summary != null && summary.matches("[A-Za-z0-9_]{1,64}")
                ? summary : "CONSUME_FAILURE";
        int updated = timeoutClaimToken == null
                ? mapper.recordConsumeFailure(messageId, incomingAttempt, safeSummary)
                : mapper.recordTimeoutConsumeFailure(messageId, incomingAttempt, safeSummary,
                        timeoutClaimToken);
        SeckillMessageLog log = mapper.selectByMessageId(messageId);
        if (updated != 1) {
            if (log != null && "CONSUME_EXHAUSTED".equals(log.getStatus())) {
                return Outcome.DEAD_LETTER;
            }
            if (log != null && log.getConsumeAttempt() != null
                    && log.getConsumeAttempt() >= incomingAttempt) {
                return Outcome.RETRY;
            }
            if (log != null && log.getConsumeAttempt() != null
                    && log.getConsumeAttempt() < incomingAttempt - 1
                    && mapper.markConsumeAttemptGap(messageId, incomingAttempt, safeSummary) == 1) {
                return Outcome.MANUAL_REQUIRED;
            }
            throw new IllegalStateException("message failure could not be persisted");
        }
        if (log == null || log.getConsumeAttempt() == null) {
            throw new IllegalStateException("message failure state is unavailable");
        }
        if (log.getConsumeAttempt() >= 3) {
            if ("ORDER_TIMEOUT".equals(log.getMessageType())) {
                deadLetterService.createForExhaustedTimeout(log);
            } else if ("ORDER_CREATE".equals(log.getMessageType())) {
                deadLetterService.createForExhaustedOrder(log);
            } else {
                throw new IllegalStateException("unsupported exhausted message type");
            }
            return Outcome.DEAD_LETTER;
        }
        return Outcome.RETRY;
    }
}
