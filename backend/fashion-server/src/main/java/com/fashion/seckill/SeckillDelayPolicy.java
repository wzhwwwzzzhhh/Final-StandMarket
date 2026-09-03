package com.fashion.seckill;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SeckillDelayPolicy {

    private SeckillDelayPolicy() {
    }

    public static long remainingMillis(Instant dueAt, Instant now) {
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(now, "now");
        if (!dueAt.isAfter(now)) {
            return 0L;
        }
        return Math.max(1L, Duration.between(now, dueAt).toMillis());
    }
}
