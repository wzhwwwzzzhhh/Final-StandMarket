package com.fashion.seckill;

import java.time.Duration;
import java.util.Objects;

public final class SeckillReconciliationPolicy {
    public enum Action { KEEP, RELEASE, WAIT_FOR_DELIVERY, RETRY_DELIVERY, MANUAL_REQUIRED }

    private final Duration orphanGrace;

    public SeckillReconciliationPolicy(Duration orphanGrace) {
        if (orphanGrace == null || orphanGrace.isNegative()) {
            throw new IllegalArgumentException("orphanGrace must not be negative");
        }
        this.orphanGrace = orphanGrace;
    }

    public Action decide(boolean hashTokenPresent,
                         boolean zsetMemberPresent,
                         Integer orderStatus,
                         String messageStatus,
                         Duration reservationAge) {
        Objects.requireNonNull(reservationAge, "reservationAge");
        if (hashTokenPresent != zsetMemberPresent) return Action.MANUAL_REQUIRED;
        if (!hashTokenPresent) {
            return orderStatus != null && (orderStatus == 1 || orderStatus == 2)
                    ? Action.MANUAL_REQUIRED : Action.KEEP;
        }
        if (orderStatus != null) {
            return orderStatus == 3 ? Action.RELEASE : Action.KEEP;
        }
        if (messageStatus == null) {
            return reservationAge.compareTo(orphanGrace) < 0
                    ? Action.WAIT_FOR_DELIVERY : Action.RELEASE;
        }
        if ("BROKER_ACKED".equals(messageStatus) || "SENT".equals(messageStatus)) {
            return reservationAge.compareTo(orphanGrace) < 0
                    ? Action.WAIT_FOR_DELIVERY : Action.RETRY_DELIVERY;
        }
        if ("PREPARED".equals(messageStatus) || "RETRY_PUBLISH_PENDING".equals(messageStatus)
                || "PROCESSING".equals(messageStatus) || "COMPENSATION_PENDING".equals(messageStatus)
                || "COMPENSATED".equals(messageStatus) || "CONSUME_EXHAUSTED".equals(messageStatus)) {
            return Action.WAIT_FOR_DELIVERY;
        }
        return Action.MANUAL_REQUIRED;
    }
}
