package com.fashion.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fashion.properties.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private static final DefaultRedisScript<Long> COMPARE_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> MAX_PUBLISH_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('get', KEYS[1]); " +
                    "local incoming = tonumber(ARGV[1]); " +
                    "if not incoming then return -2 end; " +
                    "if not current then redis.call('set', KEYS[1], ARGV[1]); return 1 end; " +
                    "local currentNumber = tonumber(current); " +
                    "if not currentNumber then return -1 end; " +
                    "if currentNumber <= incoming then " +
                    "if currentNumber < incoming then redis.call('set', KEYS[1], ARGV[1]) end; " +
                    "return 1 end; return 0", Long.class);
    private static final DefaultRedisScript<Long> FENCED_SET_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end; " +
                    "local published = redis.call('get', KEYS[2]); " +
                    "if not published then return -1 end; " +
                    "local current = tonumber(published); local candidate = tonumber(ARGV[2]); " +
                    "if not current or not candidate or current > candidate then return -2 end; " +
                    "redis.call('psetex', KEYS[3], ARGV[4], ARGV[3]); return 1", Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    //初始化缓存客户端
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //设置缓存
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time,unit);
    }
    //获取缓存
    public <T> T get(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            return null;
        }
        try {
            // 特殊处理String类型，直接返回原始字符串
            if(type == String.class){
                return (T) json;
            }
            return JSONUtil.toBean(json, type);
        } catch (Exception e) {
            log.warn("Redis数据解析失败，key: {}, type: {}", key, e.getClass().getSimpleName());
            return null;
        }
    }
    //清除缓存
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public String getRaw(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Long publishMaxVersion(String key, long version) {
        Long result = stringRedisTemplate.execute(
                MAX_PUBLISH_SCRIPT, Collections.singletonList(key), Long.toString(version));
        return result == null ? 0L : result;
    }

    public void setRaw(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cache TTL must be positive");
        }
        stringRedisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Long fencedSet(String lockKey, String publishedVersionKey, String valueKey,
                          String token, long version, String value, Duration ttl) {
        if (token == null || token.isEmpty() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0L;
        }
        Long result = stringRedisTemplate.execute(FENCED_SET_SCRIPT,
                java.util.Arrays.asList(lockKey, publishedVersionKey, valueKey),
                token, Long.toString(version), value, Long.toString(ttl.toMillis()));
        return result == null ? 0L : result;
    }

    /**
     * Acquires a lease and returns its unforgeable ownership token. A null token
     * carries no release capability.
     */
    public String tryLockToken(String key, Duration ttl) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("lock key is required");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lock TTL must be positive");
        }
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                key, token, ttl.toMillis(), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * Atomically releases only the lease still owned by {@code token}.
     */
    public Long releaseLock(String key, String token) {
        if (key == null || key.isEmpty() || token == null || token.isEmpty()) {
            return 0L;
        }
        Long result = stringRedisTemplate.execute(
                COMPARE_DELETE_SCRIPT, Collections.singletonList(key), token);
        return result == null ? 0L : result;
    }

    public void setWithLogicalExpire(String key, Object value, Long time) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    //设置空值
    public <T,ID> T queryWithPassThrough(String keyPrefix, ID id , Class< T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从Redis中获取数据
        String json = stringRedisTemplate.opsForValue().get(key);
        if(json != null){
            if(StrUtil.isNotBlank( json)){
                return JSONUtil.toBean(json, type);
            }
            return null;
        }
        T t = dbFallback.apply(id);
        if(t == null){
            stringRedisTemplate.opsForValue().set(key, "", time,unit);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t), time, TimeUnit.SECONDS);
        return t;
    }

}
