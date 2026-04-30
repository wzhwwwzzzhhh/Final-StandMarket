package com.fashion.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class UniqueID {
    /**
     * 开始时间戳
     */
    private static final long START_TIMESTAMP = 1640995200L;

    /**
     * 序列号位数
     */
    private static final long SEQUENCE_BIT = 32;

    /**
     * 注册Redis并初始化
     */
    private  final StringRedisTemplate redisTemplate;
    public UniqueID(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId(String keyPrefix) {
        /**
         * 生成当前时间戳
         */

        /**
         * toEpochSecond 是把时间转换成从1970-01-01 00:00:00 开始到现在的秒数
         * 这个数字叫时间戳，比如 1704067200 表示 2024-01-01 00:00:00
         *
         * ZoneOffset.UTC，用UTC（世界协调时间）可以保证全世界的服务器算出来的时间戳是一样的
         */
        long nowTime = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);

        long timestamp = nowTime - START_TIMESTAMP;
        if (timestamp < 0) {
            throw new RuntimeException("时间不能小于开始时间");
        }

        /**
         * 获取当前日期
         */
        String date = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        /**
         * 获取当前日期的计数
         * 获取当前日期的计数，比如 2024-01-01 00:00:00 的计数为 0
         */
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        /**
         * 拼接并返回
         * 2024:01:01:000000000001
         *
         */
        return timestamp << SEQUENCE_BIT | count;
    }
}
