package com.fashion.seckill;

import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import org.springframework.data.redis.connection.DecoratedRedisConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.DataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class SeckillRedisScanPageReader {
    private static final String REGISTRY_KEY = "seckill:coupon:reservation:index";
    private static final int MAX_LOGICAL_ENTRIES_PER_PAGE = 5000;
    private final StringRedisTemplate redis;

    public SeckillRedisScanPageReader(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    public ScanPage<String> scanRegistry(String cursor, int count) {
        RawPage raw = scan("SSCAN", REGISTRY_KEY, cursor, count);
        List<String> values = new ArrayList<>();
        for (Object entry : raw.entries) values.add(decode(entry));
        return new ScanPage<>(raw.nextCursor, values);
    }

    public ScanPage<HashEntry> scanReservations(Long couponId, String cursor, int count) {
        RawPage raw = scan("HSCAN", reservationsKey(couponId), cursor, count);
        List<HashEntry> values = new ArrayList<>();
        for (int index = 0; index + 1 < raw.entries.size(); index += 2) {
            values.add(new HashEntry(decode(raw.entries.get(index)), decode(raw.entries.get(index + 1))));
        }
        return new ScanPage<>(raw.nextCursor, values);
    }

    public ScanPage<ZEntry> scanUsers(Long couponId, String cursor, int count) {
        RawPage raw = scan("ZSCAN", usersKey(couponId), cursor, count);
        List<ZEntry> values = new ArrayList<>();
        for (int index = 0; index + 1 < raw.entries.size(); index += 2) {
            String score = decode(raw.entries.get(index + 1));
            values.add(new ZEntry(decode(raw.entries.get(index)), Double.valueOf(score)));
        }
        return new ScanPage<>(raw.nextCursor, values);
    }

    public String reservationToken(Long couponId, String userId) {
        Object value = redis.opsForHash().get(reservationsKey(couponId), userId);
        return value == null ? null : String.valueOf(value);
    }

    public Double userScore(Long couponId, String userId) {
        return redis.opsForZSet().score(usersKey(couponId), userId);
    }

    public boolean registryContains(Long couponId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(REGISTRY_KEY, String.valueOf(couponId)));
    }

    public long reservationCount(Long couponId) {
        Long size = redis.opsForHash().size(reservationsKey(couponId));
        return size == null ? 0L : size;
    }

    public long userCount(Long couponId) {
        Long size = redis.opsForZSet().size(usersKey(couponId));
        return size == null ? 0L : size;
    }

    public boolean stockIsValid(Long couponId) {
        String key = "seckill:coupon:stock:" + couponId;
        if (redis.type(key) != DataType.STRING) return false;
        String value = redis.opsForValue().get(key);
        if (value == null || !value.matches("[0-9]+")) return false;
        try {
            return Long.parseLong(value) >= 0;
        } catch (NumberFormatException overflow) {
            return false;
        }
    }

    private RawPage scan(String command, String key, String cursor, int count) {
        if (cursor == null || !cursor.matches("[0-9]+") || count < 1 || count > 5000) {
            throw new IllegalArgumentException("invalid redis scan request");
        }
        Object response = redis.execute((RedisCallback<Object>) connection -> {
            RedisConnection target = connection;
            while (target instanceof DecoratedRedisConnection) {
                RedisConnection delegate = ((DecoratedRedisConnection) target).getDelegate();
                if (delegate == null || delegate == target) break;
                target = delegate;
            }
            if (!(target instanceof LettuceConnection)) {
                throw new IllegalStateException("unsupported redis connection for cursor scan: "
                        + target.getClass().getName());
            }
            return ((LettuceConnection) target).execute(command,
                    new NestedMultiOutput<byte[], byte[]>(ByteArrayCodec.INSTANCE),
                    bytes(key), bytes(cursor), bytes("COUNT"), bytes(String.valueOf(count)));
        }, true);
        if (!(response instanceof List) || ((List<?>) response).size() != 2) {
            throw new IllegalStateException("invalid redis scan response: "
                    + (response == null ? "null" : response.getClass().getName()));
        }
        List<?> outer = (List<?>) response;
        String nextCursor = decode(outer.get(0));
        if (!nextCursor.matches("[0-9]+")) {
            throw new IllegalStateException("invalid redis scan cursor");
        }
        if (!(outer.get(1) instanceof List)) {
            throw new IllegalStateException("invalid redis scan entries");
        }
        List<?> entries = (List<?>) outer.get(1);
        int rawLimit = "SSCAN".equals(command)
                ? MAX_LOGICAL_ENTRIES_PER_PAGE : MAX_LOGICAL_ENTRIES_PER_PAGE * 2;
        if (entries.size() > rawLimit) {
            throw new IllegalStateException("redis scan page exceeds hard limit");
        }
        if (!"SSCAN".equals(command) && entries.size() % 2 != 0) {
            throw new IllegalStateException("invalid redis scan pair response");
        }
        return new RawPage(nextCursor, entries);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String decode(Object value) {
        if (value instanceof byte[]) return new String((byte[]) value, StandardCharsets.UTF_8);
        return String.valueOf(value);
    }

    private String reservationsKey(Long couponId) {
        return "seckill:coupon:reservations:" + couponId;
    }

    private String usersKey(Long couponId) {
        return "seckill:coupon:users:" + couponId;
    }

    private static final class RawPage {
        private final String nextCursor;
        private final List<?> entries;

        private RawPage(String nextCursor, List<?> entries) {
            this.nextCursor = nextCursor;
            this.entries = entries;
        }
    }

    public static final class ScanPage<T> {
        private final String nextCursor;
        private final List<T> entries;

        public ScanPage(String nextCursor, List<T> entries) {
            this.nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public static <T> ScanPage<T> empty(String nextCursor) {
            return new ScanPage<>(nextCursor, Collections.emptyList());
        }

        public String getNextCursor() { return nextCursor; }
        public List<T> getEntries() { return entries; }
    }

    public static final class HashEntry {
        private final String userId;
        private final String orderNumber;

        public HashEntry(String userId, String orderNumber) {
            this.userId = userId;
            this.orderNumber = orderNumber;
        }

        public String getUserId() { return userId; }
        public String getOrderNumber() { return orderNumber; }
    }

    public static final class ZEntry {
        private final String userId;
        private final Double score;

        public ZEntry(String userId, Double score) {
            this.userId = userId;
            this.score = score;
        }

        public String getUserId() { return userId; }
        public Double getScore() { return score; }
    }
}
