package com.fashion.product;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Process-local counters for operational diagnosis; durable facts remain in MySQL. */
@Component
public class ProductProjectionMetrics {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        if (name == null || !name.matches("[a-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("invalid metric name");
        }
        counters.computeIfAbsent(name, ignored -> new LongAdder()).increment();
    }

    public long count(String name) {
        LongAdder value = counters.get(name);
        return value == null ? 0L : value.sum();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> result = new TreeMap<>();
        counters.forEach((name, value) -> result.put(name, value.sum()));
        return Collections.unmodifiableMap(result);
    }
}
