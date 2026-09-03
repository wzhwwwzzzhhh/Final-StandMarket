package com.fashion.seckill;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class SeckillPublishCallbackPolicy {

    public enum MessageType {
        ORDER_CREATE,
        ORDER_TIMEOUT,
        INVALID_MESSAGE,
        BUSINESS_DEAD_LETTER
    }

    public enum Purpose {
        INITIAL,
        CONSUME_RETRY,
        TIMEOUT_RECOVERY,
        TIMEOUT_FALLBACK,
        DEAD_LETTER
    }

    public enum Action {
        MARK_ACKED,
        COMPENSATE_RESERVATION,
        RETRY_ORDER,
        RETRY_TIMEOUT,
        RETRY_DEAD_LETTER,
        AUDIT_ONLY
    }

    public Action decide(MessageType messageType,
                         Purpose purpose,
                         String currentCorrelationId,
                         String callbackCorrelationId,
                         boolean terminal,
                         boolean returned,
                         boolean acknowledged) {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(purpose, "purpose");
        if (!Objects.equals(currentCorrelationId, callbackCorrelationId)) {
            return Action.AUDIT_ONLY;
        }
        if (terminal) {
            return Action.AUDIT_ONLY;
        }
        if (!returned && acknowledged) {
            return Action.MARK_ACKED;
        }
        if (messageType == MessageType.ORDER_CREATE && purpose == Purpose.INITIAL) {
            return Action.COMPENSATE_RESERVATION;
        }
        if (messageType == MessageType.ORDER_CREATE) {
            return Action.RETRY_ORDER;
        }
        if (messageType == MessageType.ORDER_TIMEOUT) {
            return Action.RETRY_TIMEOUT;
        }
        return Action.RETRY_DEAD_LETTER;
    }
}
