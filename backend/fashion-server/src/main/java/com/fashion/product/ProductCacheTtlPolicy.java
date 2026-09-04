package com.fashion.product;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

@Component
public class ProductCacheTtlPolicy {

    private final LongSupplier deterministicSource;

    public ProductCacheTtlPolicy() {
        this.deterministicSource = null;
    }

    public ProductCacheTtlPolicy(LongSupplier deterministicSource) {
        if (deterministicSource == null) {
            throw new IllegalArgumentException("jitter source is required");
        }
        this.deterministicSource = deterministicSource;
    }

    public Duration withJitter(Duration base, Duration maximumJitter) {
        if (base == null || base.isZero() || base.isNegative()) {
            throw new IllegalArgumentException("base TTL must be positive");
        }
        if (maximumJitter == null || maximumJitter.isNegative()) {
            throw new IllegalArgumentException("maximum jitter must not be negative");
        }
        long maximumMillis = maximumJitter.toMillis();
        long jitterMillis;
        if (deterministicSource == null) {
            jitterMillis = maximumMillis == 0 ? 0
                    : ThreadLocalRandom.current().nextLong(maximumMillis + 1);
        } else {
            jitterMillis = deterministicSource.getAsLong();
            if (jitterMillis < 0 || jitterMillis > maximumMillis) {
                throw new IllegalArgumentException("jitter source returned a value outside [0,max]");
            }
        }
        return base.plusMillis(jitterMillis);
    }
}
