# 第二阶段：RabbitMQ 深入实战（5步）

---

## Step 2.1 消息可靠性

### 考点覆盖

**八股文知识点：**

- 消息丢失三阶段 + 对应解决：
  - **生产者 → Broker**：publisher-confirm + publisher-returns
  - **Broker 自身**：Exchange/Queue durable、消息持久化（`deliveryMode=2`）
  - **Broker → 消费者**：manual ack + 消费端重试
- **Confirm 机制**：
  - `publisher-confirm-type=correlated` — 异步回调，不影响发送性能
  - `publisher-confirm-type=simple` — 同步等待确认，性能差（不推荐）
- **Return 机制**：消息路由不到队列时回调（Exchange → Queue 失败）
- **消息落库**：发送前存 DB（状态=0待发送），收到 confirm 后更新状态=1已发送，定时任务补偿

### 项目现状

```
application.yml:38-48

spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 10
        concurrency: 5
        max-concurrency: 20
```

- **没有配置 publisher-confirm**
- `SeckillCouponServiceImpl:205` — `rabbitTemplate.convertAndSend()` 无确认回调
- Broker 宕机或 route 失败 → 消息丢失

### 模拟问题

**复现步骤：**

1. 在 `rabbitTemplate.convertAndSend()` 之后立即停掉 RabbitMQ

2. 重启 RabbitMQ 后检查队列：
   
   ```bash
   rabbitmqctl list_queues name messages
   ```

3. 发现消息丢失！

**高级模拟：**

- 配置一个不存在的 Exchange，观察消息是否丢失
- 配置一个错误的 RoutingKey 路由不到队列，观察消息是否丢失

### 解决方案

**1. application.yml 补全配置：**

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # 开启生产者确认
    publisher-returns: true              # 开启消息回退
    template:
      mandatory: true                    # 路由失败时触发 ReturnsCallback
    listener:
      simple:
        acknowledge-mode: manual         # 手动 ack
        prefetch: 10
        concurrency: 5
        max-concurrency: 20
```

**2. 实现 Callback：**

```java
@Component
@Slf4j
public class RabbitMQCallbackConfig implements ApplicationContextAware {

    @PostConstruct
    public void init() {
        RabbitTemplate rabbitTemplate = applicationContext.getBean(RabbitTemplate.class);

        // confirm 回调：消息到达 Exchange 后触发
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("消息确认成功: {}", correlationData.getId());
            } else {
                log.error("消息确认失败: {}, 原因: {}", correlationData.getId(), cause);
                // 重试逻辑：重新发送或落库
            }
        });

        // returns 回调：消息路由不到队列时触发
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息路由失败: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
            // 处理路由失败的消息
        });
    }
}
```

**3. 消息落库重试（高可靠方案）：**

```sql
CREATE TABLE message_retry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    exchange VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0待发送 1已发送 2失败',
    retry_count INT DEFAULT 0,
    max_retry_count INT DEFAULT 3,
    create_time DATETIME,
    update_time DATETIME
);
```

### 面试话术

> **"排查项目发现 RabbitMQ 没有配置生产者确认，如果 Broker 宕机或消息路由失败，消息就丢了。补上了 correlated 确认模式。为什么选 correlated？correlated 是异步回调，不影响发送性能。确认失败和路由失败的策略不同：confirm 失败可能是 Broker 故障，做重试；return 失败是配置错误，需要告警人工处理。关键消息还做了落库，收到 confirm 后更新状态，定时补偿未确认的消息。"**

### 扩展思考

- confirm 和 return 的区别（confirm = Exchange 收到；return = 路由不到 Queue）
- 消息落库重试可能造成重复消息，怎么保证幂等？（结合 Step 2.2）
- 事务消息 vs 确认机制（事务性能差 250 倍，不要用 RabbitMQ 事务）
- 消息可靠性理论上能到 100% 吗？（不能，存在极端情况如磁盘坏道）

---

## Step 2.2 幂等性与重复消费

### 考点覆盖

**八股文知识点：**

- **幂等性**：同一条消息消费多次，业务结果相同
- **重复消息来源**：
  - 生产者重发（confirm 超时重试）
  - 消费者 ack 超时（默认 30 分钟）导致 MQ 重新投递
  - RabbitMQ 集群主从切换导致未同步的消息被重新投递
- **实现方案**：唯一键去重、乐观锁（版本号）、状态机判重
- **`@Transactional` + `@RabbitListener` 的坑**：事务超回滚但 MQ 已 ack → 消息丢失

### 项目现状

- `SeckillCouponServiceImpl:222-252` — `handleSeckillOrder()` 有判重（line 231-233）
- 但 `@Transactional` + `@RabbitListener` 组合下，事务超时但提前 ack → 丢失
- 异常处理（line 249-251）：`e.printStackTrace()`，**消息被自动 ack 确认，丢失！**

### 模拟问题

**复现步骤：**

1. 在 `handleSeckillOrder` 中加 `Thread.sleep(50000)` 模拟超时
2. 观察消费者 ack 超时（默认 30min），MQ 重投消息
3. 同一订单被插入两次（或事务回滚但 MQ 已认为消费成功 → 丢失）

### 解决方案

**1. 数据库唯一索引（最强防线）：**

```sql
-- seckill_order 表的 order_number 字段
ALTER TABLE seckill_order ADD UNIQUE INDEX uk_order_number (order_number);
```

**2. 改为 manual ack + 重试策略：**

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual          # 手动 ack
        retry:
          enabled: true                    # 开启重试
          max-attempts: 3                  # 最大重试次数
          initial-interval: 1000ms         # 重试间隔
```

**3. 消费端正确处理异常：**

```java
@RabbitListener(queues = DirectExchangeConfig.SeckillQueue)
public void handleSeckillOrder(SeckillMessage message, Channel channel, 
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        // 唯一键判重
        SeckillOrder existOrder = seckillOrderMapper.selectByOrderNumber(
            message.getOrderNumber());
        if (existOrder != null) {
            channel.basicAck(deliveryTag, false); // 已处理，直接确认
            return;
        }

        // 业务处理...
        // 手动确认（处理成功）
        channel.basicAck(deliveryTag, false);

    } catch (Exception e) {
        log.error("处理秒杀消息失败", e);
        // 重试 3 次后进入死信队列（requeue=false）
        channel.basicNack(deliveryTag, false, false);
    }
}
```

### 面试话术

> **"消息消费端有两个隐患：一是 `@Transactional` 超时导致消息丢失，二是异常只打印不处理同样丢失。做了三重保障：①order_number 加唯一索引（最终防线，DDL 保障幂等）；②消费者改 manual ack，成功才 ack，失败 nack 不重回队列进入死信；③配置了重试策略，3 次失败进死信人工处理。"**

### 扩展思考

- 唯一索引和业务判重哪个优先？（唯一索引是最终保障，业务判重减少无用操作）
- nack requeue=true 和 requeue=false 分别适用什么场景？（重试 vs 死信）
- `@Retryable` 注解 vs listener.retry 配置怎么选？
- 幂等还会有性能问题吗？（唯一索引插入性能在大并发时可能成为瓶颈）

---

## Step 2.3 死信队列与延迟队列

### 考点覆盖

**八股文知识点：**

- **死信来源**：TTL 过期 / 队列满 / 消费者 nack(requeue=false)
- **死信架构**：DLX（Dead Letter Exchange）+ DLK（Dead Letter Routing Key）
- **延迟队列实现对比**：
  - TTL + DLX：固定延迟，简单可靠，消息在队列头部过期才被检查（有阻塞问题）
  - 延迟插件（`rabbitmq_delayed_message_exchange`）：精准延迟，支持动态 TTL
  - 内存时间轮：纯内存，高性能，但重启丢失
- **TTL 的坑**：消息只在**队列头部**检查是否过期，短 TTL 消息在长 TTL 消息后面会被阻塞

### 项目现状

- `DirectExchangeConfig` — 延迟队列 `delay.queue` TTL=1800s（30min），绑定死信交换机；旧队列参数需在 B11 停写、排空并重建后才能上线
- `handleDeadQueue()` (line 254-274) — 消费死信，取消未支付订单
- **死信消费者异常时，消息丢失**：catch 只 `e.printStackTrace()`，自动 ack 确认

### 模拟问题

**复现步骤：**

1. 创建秒杀订单，不支付
2. 等待 30 分钟
3. 在 RabbitMQ 管理界面观察消息流转：`market.mq` → `delay.queue` → `dead.queue`
4. 订单被自动取消，库存回补

### 解决方案

**1. 修复 handleDeadQueue 的异常处理：**

```java
@RabbitListener(queues = DirectExchangeConfig.deadQueue)
public void handleDeadQueue(Long orderId, Channel channel, 
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        SeckillOrder seckillOrder = seckillOrderMapper.selectById(orderId);
        if (seckillOrder == null || seckillOrder.getStatus() != 1) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        // 取消订单
        seckillOrderMapper.updateStatus(seckillOrder.getOrderNumber(), 3);
        // 回补库存
        seckillCouponMapper.addStock(seckillOrder.getCouponId());
        // Redis 库存回补
        stringRedisTemplate.opsForValue()
            .increment("seckill:coupon:stock:" + seckillOrder.getCouponId());

        channel.basicAck(deliveryTag, false);

    } catch (Exception e) {
        log.error("处理死信消息失败: orderId={}", orderId, e);
        try {
            channel.basicNack(deliveryTag, false, true); // 重试
        } catch (IOException ex) {
            log.error("nack 失败", ex);
        }
    }
}
```

**2. 手动 ack + 重试配置（同 Step 2.2）**

### 面试话术

> **"延迟 30 分钟关单通过 TTL + DLX 实现。但死信消费者异常时仍需要 B6 的有限重试、业务死信和补偿记录保证不丢。分析 TTL 实现时还发现一个坑：RabbitMQ 只在队列头部检查 TTL 过期，如果一条短 TTL 消息在长 TTL 消息后面，会被阻塞到长 TTL 消息也过期。所以项目用固定延迟（30min）没问题，如果需要动态延迟就得用延迟插件了。旧 15 分钟队列不能原地改参，需由 B11 停写、排空并重建。"**

### 扩展思考

- TTL 队列头部过期机制的详细原理
- 延迟插件 vs TTL+DLX 的优劣（功能 vs 运维复杂度）
- 订单在延迟期间支付了，怎么取消延迟消息？（RabbitMQ 不支持移除已投递消息，需要业务做状态判断）
- 处理死信的服务也挂了怎么保证最终一致性？

---

## Step 2.4 消息积压处理

### 考点覆盖

**八股文知识点：**

- **积压原因**：生产者速度 > 消费者速度 / 消费者异常 / 消费逻辑慢（DB 写入慢等）
- **排查手段**：RabbitMQ 管理界面看 `Ready` / `Unacked` / `Total`
- **临时扩容**：新建临时队列 + 多消费者分摊
- **长期治理**：调整 prefetch、增加 concurrency、批量写 DB、异步处理

### 项目现状

- `application.yml:46-48` — prefetch=10, concurrency=5, max-concurrency=20
- 秒杀高峰期消息量可能剧增
- 已配置 Lazy Queue（`x-queue-mode=lazy`）—— 消息直接写磁盘，防内存溢出
- `handleSeckillOrder` 中单条 insert 库存扣减，没有批量

### 模拟问题

**复现步骤：**

1. 用秒杀接口高频率发消息
2. 在 RabbitMQ 管理界面观察队列堆积
3. 观察应用 CPU/内存/Druid 连接池使用情况

### 解决方案

**1. 动态监控队列长度：**

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 50           # 提高 prefetch，减少网络往返
        concurrency: 10        # 提高最小消费者
        max-concurrency: 50    # 最大消费者数（积压时自动扩容）
```

**2. 批量消费优化：**

```java
// 使用 Listener 批量模式
@RabbitListener(queues = DirectExchangeConfig.SeckillQueue, 
                containerFactory = "batchFactory")
public void handleBatch(List<SeckillMessage> messages, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) List<Long> deliveryTags) {
    try {
        // 批量插入 DB
        seckillOrderMapper.batchInsert(messages);
        // 批量确认
        channel.basicAck(deliveryTags.get(deliveryTags.size() - 1), true);
    } catch (Exception e) {
        // 逐条 nack
        for (long tag : deliveryTags) {
            channel.basicNack(tag, false, false);
        }
    }
}
```

### 面试话术

> **"秒杀高峰期消息量暴增，已配置 lazy queue 防内存溢出还不够。优化了三点：①提高 prefetch 到 50，减少消费者空等；②单条插入改批量 insert，3000 条消息从 30s 降到 2s；③加了队列积压告警，Ready 消息 > 10000 发通知。设计上还考虑了自动扩容：消费者 concurrency 随积压动态增加，上限 50。"**

### 扩展思考

- Lazy Queue 的读写性能影响（全部走磁盘，吞吐量下降但内存稳定）
- 积压消息的 TTL 怎么设置？（超过一定时间的消息应直接丢弃，而不是积压到 OOM）
- RabbitMQ 内存告警（`vm_memory_high_watermark` 默认 40%）触发后会发生什么？（所有连接被阻塞）
- 消息积压和数据一致性的关系（积压了要不要降级？直接丢弃还是等恢复？）

---

## Step 2.5 MQ 生产问题汇总

### 故障场景演练

**场景 1：RabbitMQ 服务宕机**

- 现象：`rabbitTemplate.convertAndSend()` 抛出 `AmqpException`
- 处理：catch 异常，消息落库 `message_retry` 表，定时任务重发
- 预防：RabbitMQ 集群部署

**场景 2：消费者处理缓慢，消息积压**

- 现象：队列 Ready 消息数持续增长
- 排查：确认消费者是否卡住、DB 是否慢
- 处理：临时扩容消费者、提高 prefetch、批量消费

**场景 3：消息重复消费**

- 现象：同一 orderNumber 出现两条记录
- 排查：检查 DB 的唯一索引是否生效、消费者 ack 超时时间
- 处理：唯一索引兜底、manual ack、幂等表

**场景 4：死信队列消费失败**

- 现象：订单超时未取消、库存未回补
- 排查：检查 dead.queue 是否有堆积、消费者日志
- 处理：manual ack + 重试 + 告警

**场景 5：交换机/队列配置错误**

- 现象：return 回调频繁触发
- 排查：检查 routingKey 拼写、Exchange 是否绑定 Queue
- 处理：配置巡检 + 单元测试 RabbitMQ 拓扑
