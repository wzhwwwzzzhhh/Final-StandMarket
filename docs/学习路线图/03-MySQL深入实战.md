# 第三阶段：MySQL 深入实战（7步）

---

## Step 3.1 索引原理与失效场景

### 考点覆盖

**八股文知识点：**
- **B+Tree 数据结构**：
  - 非叶子节点只存索引（不存数据），扇出高，树矮（3-4 层可存千万数据）
  - 叶子节点双向链表连接，适合范围查询（`>`、`<`、`BETWEEN`、`ORDER BY`）
  - B+Tree vs B-Tree vs Red-Black Tree vs Hash
- **聚簇索引**：InnoDB 的主键索引，叶子节点存储完整行数据
- **非聚簇索引（二级索引）**：叶子节点存储主键值，查询需要**回表**
- **覆盖索引**：查询的列都在索引中，无需回表（Extra = Using index）
- **联合索引的 B+Tree**：按定义顺序逐层排序，最左前缀原则
- **索引选择性**：`Cardinality / 总行数`，越接近 1 越好

**8 种索引失效场景：**

| # | 场景 | 示例 | 解决 |
|---|------|------|------|
| 1 | 索引列使用函数 | `WHERE SUBSTR(name,1,3) = 'abc'` | 函数索引 / LIKE |
| 2 | 隐式类型转换 | `WHERE phone = 123`（phone是varchar） | 加引号 |
| 3 | 隐式字符编码转换 | `utf8` JOIN `utf8mb4` | 统一字符集 |
| 4 | 不满足最左前缀 | 联合索引(a,b,c)，只查c | 调整索引顺序 |
| 5 | LIKE 前导 % | `WHERE name LIKE '%abc'` | 搜索引擎 / ICP |
| 6 | OR 含非索引列 | `WHERE a=1 OR b=2`（b无索引） | UNION 替代 |
| 7 | NOT IN / NOT EXISTS | `WHERE id NOT IN (1,2,3)` | 数据分布决定 |
| 8 | 优化器选择全表扫描 | 数据量小 / 分布不均 | `FORCE INDEX` |

### 项目现状

- `OrderMapper.xml:12` — `like concat('%', #{number}, '%')` **前导 %，索引失效！**
- `OrderMapper.xml:29` — `listOrders` 中同样问题
- `ProductMapper.xml:11,28,39` — 商品名称搜索也是前导 %
- 所有查询使用 `select *`，没有覆盖索引优化

### 模拟问题

**复现步骤：**
1. `INSERT INTO orders SELECT ...` 插入 50 万条数据
2. 执行：`EXPLAIN SELECT * FROM orders WHERE number LIKE '%ORD20240624%'`
3. 观察：`type: ALL`、`rows: 500000`、`Extra: Using where`
4. 修改为精确匹配后对比：`type: ref`、`rows: 1`

### 解决方案

**1. 为 orders 表加索引：**

```sql
-- 常用查询组合
ALTER TABLE orders ADD INDEX idx_user_status (user_id, status, order_time);
ALTER TABLE orders ADD INDEX idx_order_time (order_time);
ALTER TABLE orders ADD INDEX idx_status_time (status, order_time);

-- 订单号改为精确匹配（不走 LIKE）
ALTER TABLE orders ADD UNIQUE INDEX uk_number (number);
```

**2. 模糊搜索改精确匹配 + 前端搜索优化：**

```xml
<!-- OrderMapper.xml 优化后 -->
<select id="selectByCondition">
    select id, number, status, amount, order_time, user_id
    from orders
    <where>
        <if test="number != null and number != ''">
            and number = #{number}  <!-- 改为 = 精确匹配 -->
        </if>
        <if test="status != null">
            and status = #{status}
        </if>
    </where>
    order by order_time desc
</select>
```

**3. `select *` 改为只查需要的列，配合覆盖索引：**

```xml
<!-- 改为只查需要的字段，order_time 排序可由 idx_status_time 覆盖 -->
select id, number, status, amount, order_time from orders
```

### 面试话术

> **"分析订单查询发现 `LIKE '%orderNo%'` 导致全表扫描，50 万数据查询 2s+。原因是前导 % 让 B+Tree 无法按前缀匹配，走不了索引。改了两个方案：订单号搜索改精确匹配（order_number 在业务中是唯一标识，没必要模糊搜）；必须模糊搜的场景交给 Elasticsearch。所有查询从 `select *` 改为只查需要的字段，配合覆盖索引，回表次数大大减少。"**

---

## Step 3.2 EXPLAIN 分析与 SQL 优化

### 考点覆盖

**EXPLAIN 各列详解：**

| 列 | 说明 | 关注点 |
|----|------|--------|
| `type` | 访问类型 | const/eq_ref/ref > range > index > ALL |
| `possible_keys` | 可能使用的索引 | 是否命中预期索引 |
| `key` | 实际使用的索引 | 是否最优 |
| `key_len` | 索引使用的字节数 | 联合索引用了多少列 |
| `rows` | 扫描行数估计 | 越少越好 |
| `Extra` | 附加信息 | **Using index**（好）**Using filesort**（差） |

**Extra 重点关注：**
- `Using index` — 覆盖索引（好）
- `Using index condition` — 索引下推 ICP（还行）
- `Using where` — 回表后过滤（需优化）
- `Using filesort` — 文件排序（需优化）
- `Using temporary` — 临时表（差）

### 项目现状中的 N+1 问题

**N+1 问题**：一次查询获取 N 条主数据，然后循环 N 次查关联数据，共执行 N+1 条 SQL。

项目中的 N+1：
- `OrderServiceImpl:57-62` — `getById()`：查 orders → 循环查 order_detail
- `OrderServiceImpl:143-151` — `listUserOrders()`：查 orders → 循环查 order_detail
- `SeckillOrderServiceImpl:291-339` — `getSeckillOrderStatistics()`：6 条独立 count 查询
- `SeckillOrderServiceImpl:373-391` — `listAll()`：查列表 → 循环查 coupon

```java
// OrderServiceImpl:143-151 — N+1 示例
public List<Orders> listUserOrders(Integer status) {
    Long userId = BaseContext.getUserId();
    List<Orders> orders = orderMapper.listUserOrders(userId, status); // 1 条查询
    for (Orders order : orders) {                                      // N 条查询！
        List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
        order.setItems(orderDetails);
    }
    return orders;
}
```

### 解决方案

**1. N+1 改为 JOIN 一次性查询：**

```xml
<!-- OrderMapper.xml 新增带详情查询 -->
<select id="listUserOrdersWithDetails" resultMap="OrderWithDetailsMap">
    SELECT o.*, od.id as detail_id, od.product_id, od.name, od.number as sku_num,
           od.amount as detail_amount, od.image, od.sku_info
    FROM orders o
    LEFT JOIN order_detail od ON o.id = od.order_id
    WHERE o.user_id = #{userId}
    <if test="status != null">
        AND o.status = #{status}
    </if>
    ORDER BY o.order_time DESC
</select>

<resultMap id="OrderWithDetailsMap" type="com.fashion.entity.Orders">
    <id property="id" column="id"/>
    <result property="number" column="number"/>
    <!-- ... 其他字段 -->
    <collection property="items" ofType="com.fashion.entity.OrderDetail">
        <id property="id" column="detail_id"/>
        <result property="productId" column="product_id"/>
        <result property="name" column="name"/>
        <!-- ... 其他字段 -->
    </collection>
</resultMap>
```

**2. 统计接口优化（6 条 SQL → 1 条）：**

```java
// 原来：6 次独立 count 查询
// 改为一次查询
@Select("SELECT " +
        "COUNT(*) as total, " +
        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as pending, " +
        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as paid, " +
        "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) as canceled, " +
        "SUM(CASE WHEN status = 2 THEN amount ELSE 0 END) as total_amount " +
        "FROM seckill_order")
Map<String, Object> getOrderStatistics();
```

### 面试话术

> **"用 Arthas trace 命令发现获取订单列表时，每个订单都单独查一次 order_detail——N+1 问题。10 条订单查 11 次 SQL。改 LEFT JOIN 一次性查询 + MyBatis `<collection>` 映射。统计接口更夸张，6 个独立 count 查了 6 次，改成一个 SQL 用 `SUM(CASE WHEN)` 一次性统计。优化前后：10 条订单从 300ms 降到 20ms。"**

---

## Step 3.3 深分页优化

### 考点覆盖

**八股文知识点：**
- 深分页本质：`LIMIT 100000, 10` 需要扫描 100010 行，丢弃前 100000 行
- **延迟关联**：先在覆盖索引上快速定位主键，再 JOIN 回原表取完整行
- **游标分页**：记住上一页最后一条的 ID，`WHERE id > lastId LIMIT 10`
- **子查询优化**：`SELECT * FROM t WHERE id >= (SELECT id FROM t ORDER BY id LIMIT 100000,1) LIMIT 10`

### 项目现状

- `SeckillCouponServiceImpl:130`：`int offset = (page - 1) * pageSize`
- orders 和 products 表数据量增大后翻后面几页会越来越慢

### 模拟问题

**复现步骤：**
1. 向 orders 表插入 50 万数据
2. 请求 `LIMIT 199990, 10`
3. 观察执行时间（可能 > 1s）
4. 对比延迟关联后的执行时间

### 解决方案

**1. 延迟关联：**

```sql
-- ❌ 深分页：扫描 200000 行
SELECT * FROM orders ORDER BY id LIMIT 199990, 10;

-- ✅ 延迟关联：先在 idx_id 索引扫描 200000 行（快），再回表取 10 行
SELECT o.* FROM orders o
INNER JOIN (
    SELECT id FROM orders ORDER BY id LIMIT 199990, 10
) AS tmp ON o.id = tmp.id;
```

**2. 游标分页（适合前端不断加载场景）：**

```sql
-- 第一页
SELECT * FROM orders ORDER BY id DESC LIMIT 10;

-- 第二页（传入上一页最后一条的 id）
SELECT * FROM orders WHERE id < #{lastId} ORDER BY id DESC LIMIT 10;
```

### 面试话术

> **"订单分页翻到后面几页越来越慢，`LIMIT 199990,10` 扫描 20 万行。原因是 MySQL 的 LIMIT 实现是先查 20 万行再丢弃前 199990 行。用了延迟关联：先在 id 索引上快速定位 id，再 JOIN 回表取完整行，从 2s 降到 50ms。考虑到管理端的翻页需求，加了最大翻页限制，超过 10000 页强制用户加过滤条件。用户端则改成了游标分页，每次基于上一页最后一条 id 查询，性能恒定。"**

---

## Step 3.4 事务隔离级别与 MVCC

### 考点覆盖

**八股文知识点：**
- **四种隔离级别**：RU（读未提交）→ RC（读已提交）→ RR（可重复读）→ Serializable（串行化）
- **MVCC 原理**：
  - 每一行数据有 `DB_TRX_ID`（创建事务ID）和 `DB_ROLL_PTR`（回滚指针）
  - Undo Log 形成版本链
  - Read View 包含：`creator_trx_id`、`low_limit_id`、`up_limit_id`、`trx_ids`
  - 可见性规则根据事务 ID 比较判断
- **当前读**（`SELECT ... FOR UPDATE` / `UPDATE` / `DELETE`）→ 读最新版本 + 加锁
- **快照读**（普通 `SELECT`）→ 读 Read View 可见的版本，不加锁
- **RC vs RR 核心区别**：RR 有间隙锁（Gap Lock），RC 没有
- **幻读解决**：InnoDB RR 级别使用 Next-Key Lock 解决幻读

### 项目现状

- `SeckillCouponServiceImpl.handleSeckillOrder()` (line 222)：
  ```java
  @Transactional
  public void handleSeckillOrder(SeckillMessage message) {
      // 1. 查判重            → SELECT
      // 2. insert 秒杀订单    → INSERT
      // 3. 发延迟消息         → MQ（网络操作！）
      // 4. 扣 DB 库存         → UPDATE
  }
  ```
- **同一个事务里包含 MQ 网络操作**，这是长事务，风险极大
- 事务期间持有数据库连接和锁，影响并发

### 模拟问题

**复现步骤：**
1. 确认当前隔离级别：`SELECT @@tx_isolation;`
2. 并发 500 请求抢购同一秒杀券
3. RR 级别下间隙锁可能导致死锁概率上升
4. 分析：`SHOW ENGINE INNODB STATUS\G`

### 解决方案

**1. 拆分长事务：**

```java
@RabbitListener(queues = DirectExchangeConfig.SeckillQueue)
@Transactional
public void handleSeckillOrder(SeckillMessage message) {
    // 事务内只做：查重 + insert + 扣库存
    SeckillOrder existOrder = seckillOrderMapper.selectByOrderNumber(message.getOrderNumber());
    if (existOrder != null) return;

    SeckillOrder seckillOrder = new SeckillOrder();
    // ... 设置字段
    seckillOrderMapper.insert(seckillOrder);
    seckillCouponMapper.reduceStock(message.getCouponId());

    // 事务提交后再发 MQ
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                    DirectExchangeConfig.delayExchange,
                    DirectExchangeConfig.delayRoutingKey,
                    seckillOrder.getId()
                );
            }
        });
}
```

**2. 秒杀场景推荐 RC 隔离级别：**

```sql
-- 全局设置
SET GLOBAL transaction_isolation = 'READ-COMMITTED';

-- 或只对该事务设置
SET session transaction isolation level read committed;
```

### 面试话术

> **"handleSeckillOrder 的事务里既有 DB 操作又有 MQ 发送，是典型的长事务。MQ 网络延迟会一直占用数据库连接，增加锁持有时间。拆分了：事务内只做 insert + 扣库存，通过 `TransactionSynchronizationManager.afterCommit()` 在事务提交后再发送 MQ。同时评估了 RR 和 RC：秒杀场景 RR 的间隙锁可能导致死锁概率升高，RC 更适合。RC 的不可重复读问题在秒杀场景下可以接受。"**

### 扩展思考

- Read View 在 RC 和 RR 下的生成时机（RC 每次查询都生成，RR 只生成一次）
- MVCC + 间隙锁如何解决幻读
- 长事务导致 undo log 膨胀问题
- 秒杀场景为什么适合 RC？（间隙锁不重要，更关注并发性能）

---

## Step 3.5 锁机制与死锁分析

### 考点覆盖

**八股文知识点：**
- **三种锁**：Record Lock（行锁）、Gap Lock（间隙锁）、Next-Key Lock（临键锁 = 行锁 + 间隙锁）
- **意向锁**：Intention Shared Lock / Intention Exclusive Lock，表级别标记，快速判断是否有锁
- **插入意向锁**（Insert Intention Lock）：在间隙中插入时获取，不阻塞其他插入，只阻塞 gap lock
- **锁升级**：WHERE 条件无索引 → InnoDB 从行锁升级为表锁（索引失效走全表扫描，每条记录都加锁）
- **死锁的必要条件**：互斥 + 不可剥夺 + 请求保持 + 循环等待

### 项目现状

- `SeckillCouponMapper.reduceStock()`：`UPDATE seckill_coupon SET stock = stock - 1 WHERE id = ? AND stock > 0`
- 热点行更新：同一秒杀券的库存行被大量并发 UPDATE
- **必须确认 id 是主键或有索引，否则行锁变表锁**

### 模拟问题

**复现步骤：**
1. 500 并发抢购同一秒杀券
2. 并发执行库存扣减 SQL
3. MySQL 可能产生死锁（取决于事务内其他 SQL 的执行顺序）
4. `SHOW ENGINE INNODB STATUS\G` 查看死锁日志

**死锁日志解读：**

```
------------------------
LATEST DETECTED DEADLOCK
------------------------
*** (1) TRANSACTION:
UPDATE seckill_coupon SET stock = stock - 1 WHERE id = 1 AND stock > 0

*** (2) TRANSACTION:
UPDATE seckill_coupon SET stock = stock - 1 WHERE id = 1 AND stock > 0

*** WE ROLL BACK TRANSACTION (2)
```
- 分析：两个事务都在等对方的锁释放
- 解决：缩短事务、固定加锁顺序

### 解决方案

**1. 确认索引使用：**

```sql
-- 查询表索引
SHOW INDEX FROM seckill_coupon;

-- EXPLAIN 确认 type 为 const（主键查询不会锁升级）
EXPLAIN UPDATE seckill_coupon SET stock = stock - 1 WHERE id = 1 AND stock > 0;
```

**2. 死锁处理：**

```java
// 捕获死锁异常后重试
@Retryable(value = DeadlockLoserDataAccessException.class, maxAttempts = 3)
public void reduceStock(Long couponId) {
    seckillCouponMapper.reduceStock(couponId);
}
```

**3. 库存扣减 SQL 优化：**

```sql
-- 确保用主键索引 + 乐观锁条件
UPDATE seckill_coupon
SET stock = stock - 1
WHERE id = #{id} AND stock > 0;
-- 返回受影响行数，=0 表示库存不足
```

### 面试话术

> **"秒杀券库存扣减是热点行更新，500 并发 UPDATE 同一行。三步排查：①EXPLAIN 确认用了主键索引（type=const），不会锁升级；②确认事务内 SQL 顺序固定，减少死锁概率；③加了 `@Retryable` 捕获死锁异常自动重试。最终方案是 Redis Lua 脚本做主力库存扣减，DB 只做最终一致性兜底，DB 层的并发压力大幅减小。"**

---

## Step 3.6 热点行更新与库存一致性

### 考点覆盖

- **热点行优化方案树**：
  - MQ 串行化（最常用，项目已用）
  - 库存分段（拆成多个库存行，减轻单行压力）
  - 乐观锁（CAS 重试）
  - Redis + Lua 预扣减（项目已用）
- **Redis 库存 vs DB 库存不一致问题**：
  - Redis 扣减成功 → DB 扣减失败 → Redis 库存虚低
  - DB 扣减成功 → Redis 回补失败 → Redis 库存虚高

### 项目现状

- Redis Lua 预扣减 + MQ 异步落库
- Redis 扣减成功 → 发 MQ → 消费者 INSERT + DB 扣库存
- DB 扣库存成功但 Redis 未回补（死信处理异常时）：`handleDeadQueue` line 273

### 解决方案

**对账机制：**

```java
@Component
@Slf4j
public class StockReconcileTask {
    @Autowired
    private SeckillCouponMapper seckillCouponMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 60000) // 每分钟执行
    public void reconcile() {
        List<SeckillCoupon> coupons = seckillCouponMapper.listAll();
        for (SeckillCoupon coupon : coupons) {
            String stockKey = "seckill:coupon:stock:" + coupon.getId();
            String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);
            if (redisStockStr == null) continue;

            long redisStock = Long.parseLong(redisStockStr);
            long dbStock = coupon.getStock();

            if (redisStock != dbStock) {
                log.warn("库存不一致: couponId={}, Redis={}, DB={}, 以DB为准修正",
                        coupon.getId(), redisStock, dbStock);
                stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));
            }
        }
    }
}
```

### 面试话术

> **"Redis 预扣减 + DB 最终落库的模式可能不一致：DB 扣成功但 Redis 更新失败。加了定时对账任务每分钟检查 Redis 和 DB 库存的差异，以 DB 为准修正 Redis。极端情况还能用 MQ 保证 Redis 更新不丢失：在 handleDeadQueue 中 Redis 回补操作失败了怎么办？记录到重试表，等下次对账自动修复。最终一致性方案的关键是要能发现差异并自动修复。"**

---

## Step 3.7 MySQL 生产问题汇总

### 场景演练

**场景 1：死锁**
- 现象：`Deadlock found when trying to get lock; try restarting transaction`
- 排查：`SHOW ENGINE INNODB STATUS\G` 看 `LATEST DETECTED DEADLOCK`
- 解决：缩短事务时间、固定 SQL 顺序、RC 隔离级别

**场景 2：长事务**
- 现象：undo log 膨胀、锁持有时间长
- 排查：`SELECT * FROM information_schema.innodb_trx\G`
- 解决：事务中不要有 MQ/HTTP 调用、分拆事务

**场景 3：慢 SQL**
- 现象：接口响应慢、CPU 高
- 排查：`slow_query_log` 开启 + `EXPLAIN` 分析
- 解决：加索引、改 SQL、覆盖索引

**场景 4：锁等待超时**
- 现象：`Lock wait timeout exceeded; try restarting transaction`
- 排查：`innodb_lock_wait_timeout` 默认 50s
- 解决：缩短事务执行时间、索引优化

**场景 5：连接池打满**
- 现象：`Cannot get connection from pool`、接口 500
- 排查：Druid 监控看活跃连接数
- 解决：慢 SQL 优化释放连接、调整连接池大小
