# 第一阶段：Redis 深入实战（7步）

---

## Step 1.1 Redis 连接池问题

### 考点覆盖

**八股文知识点：**

- 连接池核心参数：`maxActive`、`maxIdle`、`minIdle`、`maxWait`、`timeBetweenEvictionRunsMillis`、`testOnBorrow`、`testOnReturn`
- Spring Boot 2.x 默认使用 **Lettuce**（基于 Netty，共享连接），非 Jedis
- Jedis 是直连模式，每个实例一个 Socket；Lettuce 是 Netty 多路复用，单连接即可处理多请求
- 连接池耗尽现象：`RedisCommandTimeoutException`、`Cannot get connection from pool`
- 连接池满了是阻塞等待（取决于 maxWait）还是快速失败
- 连接泄漏场景：获取连接后未归还（异常未释放）

### 项目现状

```
application.yml:33-37

spring:
  redis:
    host: ${fashion.redis.host}
    port: ${fashion.redis.port}
    password: ${fashion.redis.password}
    database: ${fashion.redis.database}
```

- **没有任何连接池参数**，Spring Boot 2.x Lettuce 默认 maxActive=8
- `RedissonConfig.java:28-32` — Redisson 无超时/重试配置，使用默认值（timeout=10s, retryAttempts=3）
- 高并发下：Lettuce 8 连接瞬间打满，Redisson 阻塞 30s 才放弃

### 模拟问题

**准备：**

1. 查看当前 Redis 最大连接数（Redis CLI）：`CONFIG GET maxclients`
2. 查看默认 Lettuce 连接池大小：启动项目后加 `--debug` 观察

**复现步骤：**

1. 用 JMeter 启动 1000 线程（或更多）并发访问秒杀/商品接口
2. 观察控制台日志：
   - `RedisCommandTimeoutException`
   - `Cannot get connection from pool`
   - `io.lettuce.core.RedisCommandTimeoutException: Command timed out`
3. 观察接口响应时间从正常 5ms 飙升到数秒甚至超时

**预期现象：**

- 大量请求返回 500
- Redis 操作全部超时
- 应用 CPU 可能不高，但 RT 极高

### 解决方案

**1. application.yml 补充 Lettuce 连接池配置：**

```yaml
spring:
  redis:
    host: ${fashion.redis.host}
    port: ${fashion.redis.port}
    password: ${fashion.redis.password}
    database: ${fashion.redis.database}
    lettuce:
      pool:
        max-active: 50          # 最大连接数（默认8）
        max-idle: 20            # 最大空闲连接
        min-idle: 10            # 最小空闲连接
        max-wait: 3000ms        # 获取连接最大等待时间
        time-between-eviction-runs: 30000ms  # 空闲连接检测间隔
```

**2. RedissonConfig.java 补全超时配置：**

```java
config.useSingleServer()
  .setAddress(address)
  .setDatabase(database)
  .setPassword(password.isEmpty() ? null : password)
  .setTimeout(5000)                // 命令等待超时（默认10s）
  .setRetryAttempts(2)             // 重试次数（默认3）
  .setRetryInterval(1500)          // 重试间隔（默认1500ms）
  .setConnectionPoolSize(50)       // 连接池大小
  .setConnectionMinimumIdleSize(10) // 最小空闲连接
  .setIdleConnectionTimeout(10000)  // 空闲连接超时
  .setConnectTimeout(5000);         // 连接超时
```

**3. 加连接池监控（Actuator）：**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

访问 `/actuator/health` 看 Redis 健康状态，`/actuator/metrics/redis.*` 看连接池指标。

**4. 连接池满时做降级（可选）：**

使用 `@CircuitBreaker`（Resilience4j）或手动 try-catch 在 Redis 连接失败时返回降级数据（如本地缓存或默认值）。

### 面试话术

> **"在压测秒杀接口时发现，1000 并发下有大量请求返回 500，排查日志是 Redis 连接池耗尽。Spring Boot 2.x 默认用 Lettuce，连接池 maxActive 只有 8 个，瞬时并发打满了。解决：①扩充连接池到 50；②Redisson 加了超时和重试配置；③加了连接池监控。通过这次问题，我深入理解了连接池的核心参数和 Lettuce 的 Netty 多路复用模型..."**

### 扩展思考

- 连接池大小到底设多大？`(core_count * 2) + effective_spindle_count` 适用于数据库，Redis 纯内存操作公式不同
- IO密集型 vs CPU密集型任务的连接池策略差异（秒杀是 IO 密集型）
- Jedis 连接池 vs Lettuce 连接池的实现差异（Jedis 是 commons-pool2，Lettuce 是 Netty + commons-pool2）
- Redis 连接池和 Druid 连接池的异同（连接复用方式、检测机制）
- 连接池满了阻塞 vs 快速失败，业务上怎么选？
- 连接泄漏如何排查？（`netstat` 看 TIME_WAIT、`redis-cli CLIENT LIST` 看连接数）

---

## Step 1.2 缓存穿透

### 考点覆盖

**八股文知识点：**

- 缓存穿透定义：查询不存在的数据，缓存无意义，每次请求穿过缓存直击 DB
- **布隆过滤器（Bloom Filter）**：
  - 原理：初始化全 0 的 Bitmap + k 个独立 Hash 函数
  - 添加元素：k 个 Hash 映射到 bitmap 位置设为 1
  - 判断存在：k 个 Hash 位置全部为 1 → 可能存在（有误判率）
  - 判断不存在：任一位置为 0 → 肯定不存在
  - 误判率公式：`p = (1 - e^(-kn/m))^k`（m=bitmap大小, n=元素数, k=hash函数数）
  - 不支持删除（Counting Bloom Filter 可以）
- **空值缓存**：查不到的数据在 Redis 中缓存空值（key = "null"，TTL 30-60s）
- 布隆过滤器 vs 空值缓存：布隆过滤器省内存但不支持删除，空值缓存实现简单但占用空间

### 项目现状

- `SeckillCouponServiceImpl.seckillCoupon()` (line 154) — 没有判断 couponId 是否存在，直接查 Redis/Lua
- 商品查询接口同样没有防穿透措施
- 秒杀预热只预热了秒杀券的库存，其他数据没有布隆过滤器

### 模拟问题

**复现步骤：**

1. 写脚本（Python/JMeter）循环请求不存在的 couponId：负数、UUID、超大数字、SQL 注入字符串
2. 观察 Druid 监控或 MySQL 慢日志，看 DB QPS 飙升
3. 观察接口响应时间：穿透时 DB 查询慢，RT 升高

### 解决方案

**方案 A：布隆过滤器（推荐）**

```java
// 引入 Guava BloomFilter
// pom.xml: com.google.guava:guava:31.1-jre

@Component
public class BloomFilterService {
    private BloomFilter<Long> bloomFilter;

    @PostConstruct
    public void init() {
        // 参数：预计插入10000个元素，误判率1%
        bloomFilter = BloomFilter.create(Funnels.longFunnel(), 10000, 0.01);
        // 预热：从数据库加载所有有效的 couponId
        List<Long> allIds = seckillCouponMapper.selectAllIds();
        allIds.forEach(bloomFilter::put);
    }

    public boolean mightContain(Long id) {
        return bloomFilter.mightContain(id);
    }

    // 新增时调用
    public void add(Long id) {
        bloomFilter.put(id);
    }
}
```

使用 Redis Bitmap 实现布隆过滤器（适合分布式场景）：

```java
// 用 Lua 脚本在 Redis 中实现 Bloom Filter
// 或者使用 Redisson 的 RBloomFilter
RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("couponBloomFilter");
bloomFilter.tryInit(10000L, 0.01);
bloomFilter.add(couponId);
bloomFilter.contains(couponId);
```

**方案 B：接口层参数校验**

```java
if (couponId == null || couponId <= 0) {
    return Result.error("无效的请求参数");
}
```

**方案 C：空值缓存**

```java
// 查不到数据时存空值，TTL 30s
stringRedisTemplate.opsForValue().set(cacheKey, "", 30, TimeUnit.SECONDS);
```

### 面试话术

> **"安全测试中发现有人用脚本遍历不存在的 couponId，请求全部穿透到 DB。用了布隆过滤器解决：预热阶段将所有有效 ID 加载到 Bloom Filter。原理是 bitmap + k 个 hash 函数，判断不存在则 100% 不存在。我设置了 1% 误判率，n=10000，算出最优 m=95850 bit（约12KB），k=7 个 hash 函数。"**

### 扩展思考

- 误判率公式推导？`m = -n*ln(p) / (ln2)^2`，`k = (m/n) * ln2`
- 新增商品时怎么更新布隆过滤器？（启动时重新加载或增量加）
- 删除商品时布隆过滤器无法删除，怎么办？（Counting Bloom Filter、定期重建）
- Redis Bitmap 实现 vs Guava BloomFilter 的优劣对比
- 缓存穿透导致 DB 打挂，恢复流程怎么做？（限流 + 降级 + 布隆过滤器重建）
- 恶意攻击者用随机 ID 穿透，怎么在网关层识别和拦截？（限流 + 黑名单 + WAF）

---

## Step 1.3 缓存击穿

### 考点覆盖

**八股文知识点：**

- 缓存击穿定义：**单个热点 key** 在过期瞬间被大量并发请求同时打到 DB
- **互斥锁方案**：SETNX / Redisson 只允许一个线程重建缓存
  - 第一个请求获取锁，查 DB 重建缓存
  - 其他请求自旋等待或快速失败
  - 缺点：可能阻塞、需处理死锁
- **逻辑过期方案**：缓存永不过期，内部加逻辑过期字段
  - 发现逻辑过期 → 加锁异步更新缓存
  - 直接返回旧数据（性能好，短暂不一致）
  - 缺点：数据不实时
- 互斥锁 vs 逻辑过期对比：互斥锁一致性高但吞吐量低，逻辑过期性能好但有一致性窗口

### 项目现状

- `preheat()` (line 286-299) 预热秒杀券数据到 Redis，但**没有设置 TTL**，key 永不过期
- 这样避免了击穿，但数据更新后无法自动刷新（一致性问题）
- 商品详情缓存同样没有 TTL 和防击穿措施

### 模拟问题

**复现步骤：**

1. 给热点商品 key 设置 TTL（如 10 秒）
2. JMeter 500 并发请求该热点 key
3. 在 TTL 刚过期的瞬间触发并发请求
4. 观察 DB QPS 飙升（所有请求都穿透到 DB）

### 解决方案

**互斥锁实现（推荐）：**

```java
public String getWithMutex(String key) {
    String value = redisTemplate.opsForValue().get(key);
    if (value != null) {
        return value;
    }

    // 缓存未命中，尝试获取互斥锁
    String lockKey = "lock:" + key;
    Boolean lock = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(lock)) {
        try {
            // 双重检查，防止第一个线程已经重建了缓存
            value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return value;
            }
            // 查 DB
            value = queryFromDB(key);
            redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);
            return value;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 其他线程等待后重试
        Thread.sleep(100);
        return getWithMutex(key); // 自旋重试
    }
}
```

**逻辑过期实现（高性能方案）：**

```java
@Data
public class RedisData {
    private Object data;
    private LocalDateTime expireTime;
}

public Object getWithLogicalExpire(String key) {
    RedisData redisData = (RedisData) redisTemplate.opsForValue().get(key);
    if (redisData == null) {
        // 缓存不存在，直接查 DB
        return queryFromDB(key);
    }

    // 未过期
    if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
        return redisData.getData();
    }

    // 已逻辑过期，加锁异步重建
    String lockKey = "lock:" + key;
    if (Boolean.TRUE.equals(redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS))) {
        try {
            // 异步重建缓存
            CompletableFuture.runAsync(() -> {
                Object newData = queryFromDB(key);
                RedisData newRedisData = new RedisData();
                newRedisData.setData(newData);
                newRedisData.setExpireTime(LocalDateTime.now().plusHours(1));
                redisTemplate.opsForValue().set(key, newRedisData);
            });
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // 先返回旧数据
    return redisData.getData();
}
```

### 面试话术

> **"秒杀活动开始瞬间，热点商品 key 刚好过期，大量请求同时打到 DB。用了互斥锁方案：第一个请求获取分布式锁后查 DB 重建缓存，其他请求自旋等待。用了 Redisson 的锁实现，它的 WatchDog 机制会每隔 10s 自动续期，防止重建缓存耗时较长导致锁过期。自旋等待设了 3 次上限，超时后快速失败，避免请求堆积。"**

### 扩展思考

- 自旋等待 vs 快速失败 + 客户端重试，怎么选？（根据业务容忍度）
- 逻辑过期方案里，异步更新线程池怎么管理？（用线程池，不能用 new Thread）
- 分布式锁如果 Redis 挂了怎么办？（本地锁降级 + 遗留问题有限容忍）
- 互斥锁和逻辑过期可以同时用吗？（两级防护）

---

## Step 1.4 缓存雪崩

### 考点覆盖

**八股文知识点：**

- 雪崩原因：**大量 key 同时过期** / **Redis 宕机**
- 过期时间加随机值：`baseTTL + random(1min-5min)`
- **多级缓存**：本地缓存（Caffeine） → Redis 缓存 → DB
- **Redis 高可用**：哨兵模式（自动故障转移）、集群模式（数据分片）
- 本地缓存选型对比：Caffeine（Window-TinyLFU）vs Guava Cache（LRU）

### 项目现状

- `preheat()` 和 `preheatBatch()` 预热时没有给 key 设过期时间
- 如果后期需要设 TTL，多个秒杀券可能设了相同的过期时间
- 目前没有使用本地缓存（如 Caffeine）

### 模拟问题

**复现步骤：**

1. 给一批 key 设相同的过期时间（如 30 分钟）
2. JMeter 持续请求这些 key
3. 30 分钟后所有 key 同时过期，观察 DB 负载瞬间飙升

### 解决方案

**1. 过期时间加随机值：**

```java
// 基础过期时间 1 小时 + 随机 0-5 分钟
long baseTTL = 3600;
long randomOffset = ThreadLocalRandom.current().nextLong(300);
redisTemplate.opsForValue().set(key, value, baseTTL + randomOffset, TimeUnit.SECONDS);
```

**2. 引入 Caffeine 本地缓存：**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)           // 最大条目数
            .expireAfterWrite(10, TimeUnit.MINUTES)  // 写入后过期
            .recordStats()                // 开启统计
            .build();
    }
}
```

**3. 多级缓存查询：**

```java
public Object getProduct(Long id) {
    // 1. 查 Caffeine（毫秒级，不受 Redis 影响）
    Object localCache = caffeineCache.getIfPresent(id);
    if (localCache != null) return localCache;

    // 2. 查 Redis
    String redisKey = "product:" + id;
    Object redisCache = redisTemplate.opsForValue().get(redisKey);
    if (redisCache != null) {
        caffeineCache.put(id, redisCache); // 回填本地缓存
        return redisCache;
    }

    // 3. 查 DB
    Object dbData = productMapper.getById(id);
    if (dbData != null) {
        redisTemplate.opsForValue().set(redisKey, dbData, 1, TimeUnit.HOURS);
        caffeineCache.put(id, dbData);
    }
    return dbData;
}
```

### 面试话术

> **"分析现有缓存配置时发现所有 key 的过期策略相同，存在雪崩风险。做了两件事：一是过期时间加了随机偏移量（1h ± 0-5min）；二是引入了 Caffeine 本地缓存作为一级缓存。这样即使 Redis 挂了或 key 同时过期，本地缓存还能扛住一部分流量。Caffeine 使用 Window-TinyLFU 算法，相比 Guava Cache 的 LRU，TinyLFU 能更好地识别高频热点数据，命中率更高。"**

### 扩展思考

- Caffeine 的 Window-TinyLFU 原理（Window 窗口 + 主空间，维护频率草图）
- 本地缓存怎么解决多节点一致性问题？（MQ 广播失效通知、版本号）
- Redis 宕机时的降级方案设计（本地缓存 + 限流 + DB 防护）
- 雪崩后的恢复流程（流量控制、缓存逐步重建、DB 保护）

---

## Step 1.5 缓存与数据库一致性

### 考点覆盖

**八股文知识点：**

- **Cache-Aside 模式**（旁路缓存）：读 → 查缓存(未命中) → 查 DB → 写缓存
- **更新策略对比**：
  - 先删缓存再更新 DB → 后续请求可能读到旧数据并回填旧缓存
  - 先更新 DB 再删缓存 → 更新瞬间可能有并发读读到旧数据（概率较小）
  - 延时双删：删缓存 → 更新 DB → **延迟二次删缓存**
- **Canal 方案**：伪装 MySQL slave，解析 binlog，异步删除缓存
- **最终一致性 vs 强一致性**：缓存注定是最终一致性，业务容忍度决定方案选择

### 项目现状

- `SeckillCouponServiceImpl.update()` (line 104-107) — 只更新 DB，**没有处理缓存**
- 管理员修改秒杀券信息后，Redis 中缓存的是旧数据
- 用户看到的还是旧价格/旧名称

### 模拟问题

**复现步骤：**

1. 管理员通过 admin 接口修改秒杀券价格（如 100 元 → 50 元）
2. 用户同时请求该秒杀券详情
3. 用户看到的价格还是 100 元（缓存中为旧数据）

### 解决方案

**延时双删策略（推荐）：**

```java
@Override
public void update(SeckillCoupon seckillCoupon) {
    Long couponId = seckillCoupon.getId();
    // 1. 第一次删除缓存
    stringRedisTemplate.delete("seckill:coupon:" + couponId);

    // 2. 更新 DB
    seckillCoupon.setUpdateTime(LocalDateTime.now());
    seckillCouponMapper.update(seckillCoupon);

    // 3. 延迟第二次删除缓存（500ms 后）
    Executors.newSingleThreadScheduledExecutor()
        .schedule(() -> {
            stringRedisTemplate.delete("seckill:coupon:" + couponId);
        }, 500, TimeUnit.MILLISECONDS);
}
```

**使用 MQ 延迟队列实现二次删除（更可靠）：**

```java
// 发送延迟删除消息
rabbitTemplate.convertAndSend("delay.delete.exchange", "delay.delete.key", couponId);

// 消费延迟删除消息
@RabbitListener(queues = "delay.delete.queue")
public void handleDelayDelete(Long couponId) {
    stringRedisTemplate.delete("seckill:coupon:" + couponId);
}
```

### 面试话术

> **"发现秒杀券更新后用户看到的还是旧数据，因为只更新了 DB 没操作缓存。用了延时双删：先删缓存 → 更新 DB → 延迟 500ms 再删一次。第二次删除是为了防止第一次删除后，其他线程并发回填了旧缓存。延迟时间怎么定？基于业务读缓存耗时 + 200ms 冗余，500ms 够覆盖绝大多数场景。"**

### 扩展思考

- 延时双删的"延迟"时间怎么确定？（读缓存平均耗时 + 写 DB 耗时 + 冗余）
- 第二次删除失败了怎么办？（加重试机制 + 监控告警）
- Canal 原理（伪装 MySQL slave，解析 binlog 增量同步）
- 为什么是"删缓存"而不是"更新缓存"？（避免写并发竞争，删比更新更安全）
- 读写分离架构下的缓存一致性（主从延迟可能导致从库读不到最新数据）

---

## Step 1.6 🔴 Redis + Lua 原子性分析（你的独家 bug）

> 🔴 **P0** — 面试必问，1 小时内完成（含动手修复）

---

### ① 考点覆盖

**八股文知识点：**

- Lua 脚本在 Redis 中**原子性执行**：整个脚本作为一个命令执行，不会被其他命令打断
- EVAL 命令流程：上传脚本 → Lua 执行 → 返回结果
- EVALSHA（脚本缓存） vs EVAL（每次都上传脚本）—— 节省带宽
- KEYS vs ARGV：KEYS 告诉 Redis 哪些 key 被操作（集群模式下必须传）
- `redis.call()` 出错抛异常 vs `redis.pcall()` 出错返回错误对象
- Lua 脚本阻塞：`lua-time-limit`（默认 5 秒），超时后先日志警告，再强行中断
- **脚本复制模式**：默认复制写命令到 slave/AOF（涉及随机命令时有问题）

---

### ② 原理深究

**Q：为什么用 Lua 脚本来扣减库存，不用 Java 代码？**

Java 方式：

```java
// 非原子，有并发问题
int stock = Integer.parseInt(redisTemplate.opsForValue().get(stockKey));
if (stock > 0) {
    redisTemplate.opsForValue().decrement(stockKey);
}
```

三步操作（GET → 判断 → DECR）不是原子的，高并发时多个线程同时读到剩余 1 个库存，都认为有货，全部执行 DECR → **超卖**。

Lua 方式：整个脚本在 Redis 单线程中执行，**中间不会有其他命令插入**，天然原子。

**Q：为什么不直接在 MySQL 做库存扣减？**

MySQL 行锁在超高并发下会成为瓶颈，Redis 单机 QPS 可达 10 万+，适合做秒杀的第一道防线。

**Q：为什么 Redis 存时间要用时间戳？**

Redis 中存时间只有两种常见选择——字符串（如 "2024-06-01T10:00:00"）或时间戳（如 1717228800）。

| 方案  | 优点                        | 缺点               |
| --- | ------------------------- | ---------------- |
| 字符串 | 人类可读，Java 直接 `toString()` | Lua 无法直接比较，需额外解析 |
| 时间戳 | Lua 用 `tonumber()` 直接比较   | 人类不可读            |

注意 `tonumber("2024-06-01T10:00:00")` 返回的是 **nil**（因为包含非数字字符），所以用字符串在 Lua 中做时间比较是行不通的。时间戳是唯一可行的方案。

**Q：`tonumber()` 返回 nil 为什么不直接报错？**

这就是 bug 的根源——Lua 中 `tonumber` 遇到非法字符串不会报错，只会返回 nil。而 nil 在条件判断中是 falsy（假值），所以 `if start_time and ...` 直接跳过了时间校验，**静默失败**。

---

### ③ 项目现状（修复前）

**关键问题——两个预热方法时间存储格式不一致：**

```java
// preheat() 单券预热 —— line 296-298（修复前）
stringRedisTemplate.opsForValue().set(startTimeKey, coupon.getStartTime().toString());
// 存的是 "2024-06-01T10:00:00"
```

```java
// preheatBatch() 批量预热 —— line 319-320
long startTime = coupon.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
connection.set(startTimeKeyBytes, String.valueOf(startTime).getBytes());
// 存的是 "1717228800"
```

```lua
-- seckill.lua line 19
local start_time = tonumber(redis.call('GET', start_time_key))
-- tonumber("2024-06-01T10:00:00") → nil
-- nil 是 falsy → if start_time and ... → 条件跳过 → 时间校验失效！
```

**影响：** 调用 `preheat()` 预热的秒杀券，在秒杀开始前也可以正常抢购——时间校验被静默跳过。

---

### ④ 动手清单

#### 修复 1：preheat() 时间存储改为时间戳

打开 `SeckillCouponServiceImpl.java`，找到 `preheat()` 方法中的以下代码（line 296-298）：

```java
// 原来的错误代码
stringRedisTemplate.opsForValue().set(startTimeKey, coupon.getStartTime().toString());
stringRedisTemplate.opsForValue().set(endTimeKey, coupon.getEndTime().toString());
```

改为：

```java
if (coupon.getStartTime() != null) {
    long startTime = coupon.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
    stringRedisTemplate.opsForValue().set(startTimeKey, String.valueOf(startTime));
}
if (coupon.getEndTime() != null) {
    long endTime = coupon.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond();
    stringRedisTemplate.opsForValue().set(endTimeKey, String.valueOf(endTime));
}
```

#### 修复 2：Lua 脚本添加 nil 校验防御

打开 `lua/seckill.lua`，在 `tonumber()` 解析后添加 nil 检查：

```lua
local start_time = tonumber(redis.call('GET', start_time_key))
-- 新增：时间数据不存在或格式错误时直接拒绝
if start_time == nil then
    return -5
end
if current_time < start_time then
    return -3
end
```

同样在 `end_time` 的 `tonumber()` 后也加 nil 检查。

#### 验证方法

**方法一：直接看 Redis 存了什么**

```bash
# 修复前
redis-cli GET "seckill:coupon:startTime:1"
# 返回: "2024-06-01T10:00:00"  ← 字符串，Lua 解析为 nil

# 修复后
redis-cli GET "seckill:coupon:startTime:1"
# 返回: "1717228800"            ← 时间戳，Lua 正确解析
```

**方法二：写测试用例验证**

```java
// 修复前：秒杀未开始但可以正常进入
// 修复后：秒杀未开始 → 返回 -3 / -5，接口返回 "秒杀未开始"
```

**方法三：检查代码中是否还有其他 `toString()` 到 Redis 的时间字段**

```bash
git grep "\.toString()" -- "*.java" | grep -i time
```

---

### ⑤ 面试话术

> **"我在审查秒杀 Lua 脚本时发现了一个隐藏 bug。项目有两个预热方法——`preheat()` 单券预热和 `preheatBatch()` 批量预热。批量预热存的是时间戳，但单券预热存的是 `LocalDateTime.toString()` 的字符串（比如 '2024-06-01T10:00:00'）。Lua 脚本用 `tonumber()` 去解析这个字符串，但因为字符串里包含字母和冒号，`tonumber()` 返回了 nil。关键问题是——nil 在 Lua 条件判断中是 falsy，所以 `if start_time and current_time < start_time` 这个时间校验就被**静默跳过**了。结果是管理员通过管理界面手动预热的秒杀券，时间校验完全失效，用户可以在秒杀开始前随意抢购。修复很简单：统一用时间戳。但我更在意的是从 bug 中学习到的三个教训：第一，Redis 中存时间一律用时间戳；第二，Lua 脚本中关键数据必须做 nil 校验防御；第三，代码审查时要注意方法之间的实现一致性。"**

---

### ⑥ 面试官追问树

**追问 1："你怎么知道 `tonumber` 会返回 nil？"**

- 答：我直接在 Redis CLI 里用 `EVAL "return tonumber('2024-06-01T10:00:00')" 0` 测试的，返回 nil。这是 Lua 的基本行为——只要字符串包含非数字字符（小数点、负号除外），tonumber 就返回 nil。

**追问 2：“如果没发现这个 bug，生产上会有什么后果？”**

- 答：如果运营人员手工调用了单券预热接口（管理界面用的是 `preheat()`），那这个秒杀券的开始时间校验就失效了。用户可以在活动开始前就抢购。后果是：①活动规则被破坏，提前卖完；②可能造成资损。尤其在大促场景下，这种 bug 可能导致严重的运营事故。

**追问 3：“为什么 `preheatBatch()` 没有这个 bug？”**

- 答：因为是两个人写的或者不同时间写的。`preheatBatch()` 用的 `RedisCallback` 底层 API，传的是 byte[]，开发人员自然做了 `String.valueOf(startTime)` 转换。而 `preheat()` 用 `StringRedisTemplate` 的高级 API，直接 `toString()` 更自然。这就是代码一致性问题——两个方法做同一件事但没有用同一个工具函数。

**追问 4：“除了你发现的这个 bug，还有没有其他地方可能也有时间存储问题？”**

- 答：我通过 `git grep "\.toString()" | grep -i time` 检查了全项目，发现还有两个地方需要注意，已统一修复。同时加了一个规范：所有写入 Redis 的时间字段必须用工具类统一处理，后面会抽象一个 `RedisTimeUtils` 方法。

**追问 5：“如果 Redis 的时间数据被误删了，`GET` 返回 nil，你的 nil 检查会怎么处理？”**

- 答：Lua 脚本中 `GET` 不存在的 key 返回 nil，`tonumber(nil)` 也返回 nil，所以 nil 检查同样能兜住。返回 -5 错误码后，Java 端会返回"秒杀未开始"的提示。业务上这是合理的——数据不完整时拒绝服务比出错更安全。

---

### ⑦ 自测

1. **Lua 的 `tonumber()` 对以下输入分别返回什么？**
   
   - `tonumber("1717228800")`
   - `tonumber("2024-06-01T10:00:00")`
   - `tonumber("3.14")`
   - `tonumber("abc")`
   - `tonumber(nil)`

2. **Redis 是单线程的，那为什么多个客户端同时执行同一个 Lua 脚本不会有并发问题？如果 Lua 脚本里操作了多个 key 呢？**

3. **如果一个 key 在 Lua 脚本执行到一半时过期了（TTL 到了），会发生什么？**

---

## Step 1.7 Big Key 与 Hot Key

### 考点覆盖

**八股文知识点：**

- **Big Key**：单个 key 的 value 很大（大 String > 10MB、大 ZSET/List > 万级别元素）
  - 危害：Redis 阻塞（读/写大 key 是 O(N)）、网络带宽、集群数据倾斜、慢查询
  - 排查：`redis-cli --big-keys`、`MEMORY USAGE key`、`DEBUG OBJECT key`
  - 解决：拆分（Hash 分片）、大集合转小集合、压缩 value
- **Hot Key**：单个 key 被极高频率访问（如秒杀热点商品）
  - 危害：单节点 CPU 打满、请求延迟
  - 解决：本地缓存兜底、读写分离（多 slave）、key 分拆（加后缀分散到多节点）

### 项目现状

- 秒杀用户集合 `seckill:coupon:users:{couponId}` 使用 ZSET，大促可能有数万元素
- 没有 Big Key / Hot Key 的监控和报警

### 解决方案

**1. 监控 Big Key：**

```bash
# Redis CLI 扫描大 key
redis-cli --big-keys

# 或检查特定 key 内存占用
redis-cli MEMORY USAGE seckill:coupon:users:1
```

**2. Big Key 拆分（按用户 ID 分片）：**

```lua
-- 分片路由
local userId = tonumber(ARGV[1])
local shardCount = 10
local shardIndex = userId % shardCount
local usersKey = KEYS[4] .. ":" .. shardIndex

-- 判断用户是否已购买（需要查所有分片）
for i = 0, shardCount - 1 do
    local shardKey = usersKeyBase .. ":" .. i
    if redis.call('ZSCORE', shardKey, user_id) then
        return -4
    end
end
```

**3. Hot Key 本地缓存兜底（复用 Step 1.4 的 Caffeine 方案）**

**4. 配置 Redis 慢查询日志：**

```bash
# redis-cli 设置慢查询阈值（单位微秒）
CONFIG SET slowlog-log-slower-than 100000  # 100ms
CONFIG SET slowlog-max-len 1000
```

### 面试话术

> **"分析秒杀场景发现，抢购热门券的用户 ZSET 可能积累上万元素。ZSCORE 虽然 O(logN)，但并发高时仍需关注。把用户集合按 user_id 哈希到 10 个分片 key 上。同时配置了 Redis 慢查询日志，阈值 100ms，及时发现大 key 问题。"**

### 扩展思考

- ZSET 10 万元素时 ZSCORE 的耗时大概多少？（百万内都是微秒级，但网络传输有影响）
- `redis-cli --big-keys` 扫描原理（循环 scan + TYPE 检查 size）
- Hot Key 怎么自动识别？（Redis 4.0 LFU 策略 + `OBJECT FREQ` 命令）
- 分片策略设计（一致性哈希？取模？）
