package com.fashion.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fashion.properties.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static cn.hutool.poi.excel.sax.AttributeName.t;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String LOCK_KEY_PREFIX = "lock:";
    //线程池，用于异步执行缓存重建任务
    //用ThreadPoolExecutor实现
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            10, 15, 10L,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(100000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

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
            log.warn("Redis数据解析失败，key: {}, 数据: {}", key, json);
            return null;
        }
    }
    //清除缓存
    public void delete(String key) {
        stringRedisTemplate.delete(key);
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

    //逻辑过期解决缓存击穿
    public <T,ID> T queryWithLogicalExpire(String keyPrefix, ID id , Class< T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //不为空应该返回缓存数据且判断是否未过期
        if(StrUtil.isBlank( json)){
            if(json == null){
                //说明是第一次请求，直接查询数据库
                T t = dbFallback.apply(id);
                if(t == null){
                    stringRedisTemplate.opsForValue().set(key, "", time,unit);
                    return  null;
                }
                setWithLogicalExpire(key, t, time);
                return t;
            }
            //否则为空值
            return null;
        }

        //判断是否过期
        //未过期应该返回缓存数据
        if(redisData.getExpireTime()!= null &&redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return JSONUtil.toBean((JSONObject) redisData.getData(), type);
        }
        //缓存已过期，重建缓存，且返回旧值
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        String lockKey = LOCK_KEY_PREFIX + keyPrefix + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T t2 = dbFallback.apply(id);
                    if(t2 == null){
                        stringRedisTemplate.opsForValue().set(key, "", time,unit);
                        //如果为空说明之前未null，直接返回就行
                        return;
                    }else{
                        setWithLogicalExpire(key, t2, time);
                    }
                    log.info("重建缓存成功，key：{}", key);
                } catch (Exception e) {
                    log.error("重建缓存失败，key：{}", key, e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //返回t（t是之前的值）
        return t;
    }
    /**
     * 缓存击穿 获取互斥锁
     */
    public <T,ID> T queryWithMutex(String keyPrefix, ID id , Class< T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从Redis中获取数据
        String json = stringRedisTemplate.opsForValue().get(key);
        if(json != null){
            if(StrUtil.isNotBlank( json)){
                return JSONUtil.toBean(json, type);
            }
            return null;
        }
        String lockKey = LOCK_KEY_PREFIX + keyPrefix + id;
        boolean isLock = tryLock(lockKey);
        try {
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }else{
                //二重检测
                json = stringRedisTemplate.opsForValue().get(key);
                if(json != null){
                    if(StrUtil.isNotBlank( json)){
                        return JSONUtil.toBean(json, type);
                    }
                    return null;
                }
                T t = dbFallback.apply(id);
                if(t == null){
                    stringRedisTemplate.opsForValue().set(key, "", time,unit);
                }
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t), time, unit);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return null;
    }

    /**
     * 尝试获取锁
     * @param key 锁的key
     * @return 是否成功
     */
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return flag != null && flag ;
    }
    /**
     * 释放锁
     * @param key 锁的key
     */
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
