# B6 RabbitMQ 可靠投递与消费失败治理 · Design

> Status: 已确认（2026-09-01）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B6 / GitHub Issue #16（Stage B 总跟踪 #3）
> Baseline: `master` @ `b07f9ed1a21d09c0c8eefae93a84ae10407d00d0`（PR #15 / B5 已合并）
> Updated: 2026-09-01

## 1. Goal and scope

### In scope

- 在 Redis Lua 预扣、RabbitMQ 初始投递、MySQL 落单、延迟关闭、取消补偿之间建立可持久追踪、可重放、可对账的最终一致性闭环；不宣称 Redis、RabbitMQ 与 MySQL 跨存储原子事务。
- 同步发送异常立即使用订单号令牌原子回滚 Redis 库存与用户占用；异步 `ack/nack/return/confirm timeout` 通过消息日志、补偿记录和定时恢复收敛。
- 启用 correlated publisher confirm、publisher return 和 `mandatory`；所有投递使用稳定 `messageId`、消息类型、业务键和 schema 版本。
- 秒杀落单与超时取消消费者使用显式手动确认；业务失败最多处理 3 次，超过阈值转入独立业务失败 DLQ 并产生稳定告警信号，默认路径禁止无限 `requeue`。
- 订单写入、MySQL 秒杀券库存条件扣减、初始消息消费完成标记和延迟消息待发送记录位于同一 MySQL 事务；事务提交后才允许发送延迟消息。
- 使用订单号、数据库唯一约束和消息日志 CAS 处理重复投递；重复消息最多创建一个秒杀订单。
- 为 Redis 预扣增加订单号级 reservation token 和活跃券 registry，使回滚、重复补偿、服务重启、券删除残留和用户取消后重新参与均可被安全识别，不会误回滚新的预扣。
- 增加消息日志、补偿记录、恢复/对账任务和幂等 MySQL 迁移；覆盖首次执行、合法重跑、错误/部分定义及脏数据门禁。
- 定义真实 MySQL 8、Redis 7、RabbitMQ 3.12 的隔离测试条件和故障注入门禁。

### Out of scope

- 完整通用 outbox 平台、跨业务通用消息中台或引入新的外部告警产品；B6 只实现秒杀链路所需的定向持久日志与恢复任务。
- B7-B10 的优惠、评价、缓存、AI 或 Flyway 总体发布基线；B6 的增量 SQL 仍须在 B10 纳入正式版本化迁移。
- 生产数据库迁移、生产 Redis 修复、生产 RabbitMQ 队列声明/删除/切换、部署和流量开放；这些操作继续受 B10/B11 和本设计的切换门禁约束。
- 改变秒杀业务状态 `1=待支付、2=已支付、3=已取消`、30 分钟超时、每用户每券一个活动订单等 B5 已确认规则。
- 将本地验证或未来 PR 合并解释为可生产发布；B0-AC6、B10、B11 和 B6 本身仍是生产发布门禁。

## 2. Current behavior and constraints

### 2.1 Confirmed repository facts

- `SeckillCouponServiceImpl#seckillCoupon` 在 `seckill.lua` 成功后调用无 `CorrelationData` 的 `RabbitTemplate#convertAndSend`。它只捕获 `AmqpException` 并重新抛出，不调用 B5 回滚 Lua，因此同步 MQ 失败会留下 Redis 库存减少和 ZSET 用户占用。
- `application.yml` 未启用 `spring.rabbitmq.publisher-confirm-type=correlated`、`publisher-returns=true` 或 `template.mandatory=true`。
- `DirectExchangeConfig` 的初始订单拓扑是 `market.direct --seckillOrder--> market.mq`。`market.mq` 没有失败 DLX；当前 `dead.queue` 是 `delay.queue` 的 30 分钟 TTL 到期后用于触发超时取消的队列，并不是消费失败业务 DLQ。
- `handleSeckillOrder` 使用默认 `AUTO` ack，在同一个 `@Transactional` 方法中先插订单、再发送延迟消息、最后扣 MySQL 库存。延迟消息不属于数据库事务，库存扣减失败回滚订单时，已经发送的延迟消息不会回滚。
- 两个 `@RabbitListener` 都没有有限重试、手动 ack/nack/reject 策略或毒消息隔离；异常行为依赖容器默认值，无法证明不会无限重投或丢失。
- 消费端先查订单再插入，数据库已有 `UNIQUE(order_number)` 和 B5 活动订单唯一约束，但 `order_number` 当前仍可 NULL，且代码没有将唯一冲突分类为“等价重复”或“冲突毒消息”。
- B5 取消在 MySQL 提交后调用 `seckill_rollback.lua`；Redis 失败仅记录 ERROR 并返回 `REDIS_RECONCILIATION_PENDING`，没有持久补偿事实，也没有重启后恢复能力。
- B5 回滚 Lua 只以 `(couponId,userId)` 的 ZSET 成员作为令牌。若 Redis 回滚已成功而 MySQL 的“补偿成功”落库失败，用户重新参与后旧补偿再次执行，可能误移除新预扣，因此 B6 必须使用订单号级 token。
- 当前测试基础已有 JUnit 5/Mockito、真实 Spring/MyBatis/MySQL 条件集成测试、真实 Redis Lua 条件集成测试和严格幂等迁移测试；没有 RabbitMQ 集成测试或 Testcontainers 依赖。
- 本次设计调查时 `127.0.0.1:3306` 可连接，`6379/5672/15672` 不可连接，系统无 `docker` 命令；这只是环境快照，不构成 B6 集成门禁通过证据。

### 2.2 Reliability model

RabbitMQ publisher confirm 只证明 broker 接管发布，consumer ack 只证明消费者完成处理，两者互相独立。B6 的目标是 **at-least-once + 业务幂等 + 持久补偿与对账收敛**，不是 exactly-once：

```text
Redis 原子预扣
  → MySQL 写 PREPARED 消息日志
  → RabbitMQ mandatory publish
  → broker confirm / return
  → consumer 手动确认
  → MySQL 原子落单与库存扣减
  → 提交后发送 30 分钟延迟消息
  → 失败由消息日志/补偿记录/对账重放或回滚
```

官方语义依据：

- Spring AMQP correlated confirms/returns：<https://docs.spring.io/spring-amqp/reference/amqp/template.html>
- RabbitMQ publisher confirm 与 consumer ack 相互独立：<https://www.rabbitmq.com/docs/confirms>
- RabbitMQ DLX 安全边界：<https://www.rabbitmq.com/docs/dlx>

## 3. Design decisions

### 3.1 Redis reservation token and immediate rollback

订单号在执行预扣 Lua **之前**生成。B6 将预扣脚本扩展为六个 key：

| 参数 | 含义 |
|---|---|
| `KEYS[1]` | `seckill:coupon:stock:{couponId}` |
| `KEYS[2]` | 开始时间 key |
| `KEYS[3]` | 结束时间 key |
| `KEYS[4]` | `seckill:coupon:users:{couponId}` ZSET |
| `KEYS[5]` | `seckill:coupon:reservations:{couponId}` HASH，field=`userId`，value=`orderNumber` |
| `KEYS[6]` | `seckill:coupon:reservation:index` SET，member=`couponId`，作为对账扫描全集 |
| `ARGV[1..3]` | 数量、当前时间、用户 ID（沿用 B5） |
| `ARGV[4]` | 本次预扣订单号 |

Lua 在任何写入前验证 stock/users/reservations/registry 的 key 类型、库存整数、用户 ID 和订单号。成功时原子执行 `DECRBY + ZADD + HSET + SADD`；重复用户、重复/非法 token 或任何错误均不留下部分写入。

回滚 Lua 固定接收 stock/users/reservations/registry 四个 key以及 `couponId,quantity=1,userId,expectedOrderNumber`。只有 HASH 中的 token 精确等于期望订单号且 ZSET 成员存在时，才原子执行 `INCRBY + ZREM + HDEL`。写后同时读取 `HLEN reservations` 和 `ZCARD users`：只有两者都为 0 才 `SREM` registry；任一非零都保留 registry，数量不一致返回 `APPLIED_LEDGER_INCONSISTENT`，表示目标 token 已安全释放但同券还有账本异常需要对账/告警。删除券后 registry 仍能把 HASH-only/ZSET-only 残留纳入扫描，不能因只看 HLEN 隐藏损坏。因而：

- 同一订单回滚重放最多增加一次库存；
- Redis 成功、MySQL 成功标记失败后再次执行不会改变库存；
- 用户取消后重新参与形成新订单号时，旧回调/旧补偿不能删除新 reservation；
- token 缺失、token 不匹配、ZSET/HASH 部分不一致均不猜测修复，转 `MANUAL_REQUIRED` 并告警。

同步发送路径固定时序：

1. 生成 `orderNumber`，执行带 token 的预扣 Lua。
2. 通过独立事务 Bean（`REQUIRES_NEW`）插入 `ORDER_CREATE` / `PREPARED` 消息日志；入口本身禁止处于包裹发布的外层事务。只有事务代理已返回、且独立连接能读到该行后才允许调用 RabbitTemplate。日志插入失败视为同步投递前失败，立即执行订单号级 Redis 回滚并向客户端返回失败。
3. 发布前短事务生成并持久化 INITIAL `publishAttempt=1/currentCorrelationId={messageId}:P1`，随后使用 `mandatory` 和 `CorrelationData(currentCorrelationId)` 发送；INITIAL 不得复用逻辑 messageId 作为 correlation ID。
4. `convertAndSend` 同步抛出时，先幂等登记 `DELIVERY_FAILED + PENDING` 补偿，再立即执行 Redis 回滚；即使补偿记录落库失败也必须尝试 Lua，并输出稳定 ERROR 事件供对账发现。
5. 只有发送调用正常返回才向客户端返回“处理中”；这不等价于 broker ack 或订单已创建。

### 3.2 Publisher contract

配置固定为：

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

每条 B6 消息必须同时设置：

- `messageId`：稳定确定值，初始落单为 `SECKILL_ORDER_CREATE:{orderNumber}`，延迟关闭为 `SECKILL_ORDER_TIMEOUT:{orderNumber}`，死信 envelope 为 `SECKILL_DEAD:{sourceMessageId}`；
- `CorrelationData.id`：每次网络发布唯一，固定为 `{messageId}:P{publishAttempt}`；逻辑 `messageId` 稳定，网络 attempt correlation 不复用；
- headers：`fsm-message-type`、`fsm-schema-version=1`、`fsm-business-key=orderNumber`、`fsm-publish-attempt`、`fsm-consume-attempt`；
- `contentType=application/json`，继续使用 Jackson converter，不接受 Java 原生序列化 payload。
- `deliveryMode=PERSISTENT`；durable exchange/queue 与持久消息同时成立才构成 broker 重启恢复条件，禁止依赖 converter 的隐含默认值。

全应用只能注册一个 `RabbitTemplate.ConfirmCallback` 和一个 `ReturnsCallback`；每次发布前在短事务中原子递增 `publish_attempt`、写入 `current_correlation_id/publish_purpose`，再构造消息。Confirm 以 `CorrelationData.id` 关联，Return 以稳定 messageId + header `fsm-publish-attempt` 关联；callback 只有在 attempt 精确等于当前行 `publish_attempt` 时才能推进业务状态，旧 attempt 的迟到 ack/nack/return 只追加受限审计，不改变当前状态。禁止依赖进程内 Map，保证重启可恢复。

回调先按持久行的 `message_type + publish_purpose` 分派，不使用一套泛化失败动作：

| 消息/发布目的 | ack | nack / return / confirm timeout |
|---|---|---|
| `ORDER_CREATE / INITIAL` | `PREPARED/SENT → BROKER_ACKED` | 未消费时 `DELIVERY_FAILED → COMPENSATION_PENDING`；已 `CONSUMED` 只记异常回调，不释放 reservation |
| `ORDER_CREATE / CONSUME_RETRY` | 回到可消费的 `BROKER_ACKED` | 保持/回到 `RETRY_PUBLISH_PENDING`，由恢复任务继续有限发布；发布耗尽后持久化 `CONSUME_EXHAUSTED + dead_letter_status=PENDING`，且无订单时立即独立创建 reservation 释放补偿 |
| `ORDER_TIMEOUT / INITIAL_OR_RECOVERY` | `SENT → BROKER_ACKED` | `TIMEOUT_PUBLISH_PENDING`，按原 `due_at` 补发或直接执行到期兜底；绝不释放 reservation |
| `BUSINESS_DEAD_LETTER / DEAD_LETTER` | 当前 attempt 且无 return 时，dead-letter 行 `BROKER_ACKED`、源行 `dead_letter_status=ACKED` | dead-letter 行 `DEAD_LETTER_PUBLISH_PENDING`、源行 `dead_letter_status=PENDING`；只重发 DLQ envelope，绝不控制库存补偿 |

通用规则：ack 不把任何失败/消费/补偿终态改回成功；return 即使 ack 已到也保存 reply code/text/exchange/routing key并应用上表；后到 ack 不覆盖 return；重复 callback 只更新审计字段。每个 callback 先校验 publish-attempt token，所以旧 attempt return/nack 晚于新 attempt ack 时不能污染新事实。`BUSINESS_DEAD_LETTER` 的 `business_key` 固定为源 `messageId`，因此同一订单的 ORDER_CREATE 与 ORDER_TIMEOUT 可以各自有一个失败 envelope。

`ORDER_CREATE / INITIAL` 的 Consumer 与失败回调都在 MySQL 事务中 `SELECT ... FOR UPDATE` 同一日志：consumer 先提交 `CONSUMED` 时，后到 nack/return 只记录异常回调而不补偿；失败回调先提交 `COMPENSATION_PENDING` 时，后到消息不得创建订单，直接作为已撤销的迟到投递确认。这个串行化点消除“消息已落单却被异步回调回滚 Redis”的竞态。publish 前独立提交测试必须用 latch 阻塞发送，同时从另一连接读日志，再释放发送并让 confirm/return/consumer 执行；查不到日志是测试失败而不是可忽略 callback。

### 3.3 Message log state machine

新增 `seckill_message_log`，一行代表一个逻辑消息而不是一次网络尝试。

核心字段：

- `message_id VARCHAR(128) NOT NULL`；`current_correlation_id VARCHAR(160)` 保存当前 attempt token；
- `message_type VARCHAR(32) NOT NULL`：`ORDER_CREATE` / `ORDER_TIMEOUT` / `INVALID_MESSAGE` / `BUSINESS_DEAD_LETTER`；`publish_purpose` 区分 `INITIAL/CONSUME_RETRY/TIMEOUT_RECOVERY/DEAD_LETTER`；
- `business_key VARCHAR(128) NOT NULL`（订单号、quarantine key 或源 messageId）；`source_message_id VARCHAR(128)`（仅 DLQ）、`source_message_id_hash CHAR(64)`、受限清洗后的 ID 前缀、`body_sha256 CHAR(64)`、`body_size`、`user_id`、`coupon_id`；
- `payload TEXT NOT NULL`、`payload_schema_version INT NOT NULL`、exchange/routing key；
- `status VARCHAR(32) NOT NULL`；源消息另有 `dead_letter_status VARCHAR(16) NOT NULL DEFAULT 'NONE'`，把业务处理终态与 DLQ 发布轨道解耦；
- `confirm_status VARCHAR(16) NOT NULL`：`PENDING/ACK/NACK/TIMEOUT`，以及 `returned`、return/confirm 原因字段；
- `publish_attempt`、`consume_attempt`、`processing_attempt`、`due_at`（仅 timeout，创建后不可变）、`next_retry_at`、`locked_by`、`locked_until`、`version`、源 messageId、创建/更新时间/确认/消费时间。

约束与索引：

- `UNIQUE(message_id)`；
- `UNIQUE(message_type,business_key)`：ORDER_CREATE/ORDER_TIMEOUT 的 business key 为最长 50 字符订单号，BUSINESS_DEAD_LETTER 的 business key/source_message_id 为最长 128 字符源 messageId；保证同一逻辑消息和它的 DLQ envelope 各只有一行；
- 恢复扫描索引 `(status,next_retry_at,id)`；
- 对账索引 `(coupon_id,user_id,status,id)`；
- CHECK 限制类型、状态、非负 attempt 和 schema version；未知同名表/列/索引或非预期定义由迁移显式失败。

按类型分别定义状态机，不能跨类型套用补偿边：

```text
ORDER_CREATE / INITIAL:
  PREPARED → SENT → BROKER_ACKED → PROCESSING → CONSUMED
      └────────────── delivery failure ─────→ COMPENSATION_PENDING → COMPENSATED
  PROCESSING failure → RETRY_PENDING → RETRY_PUBLISHING → BROKER_ACKED → PROCESSING
  第 3 次/永久错误 → CONSUME_EXHAUSTED
    并行轨道 A：dead_letter_status NONE → PENDING → ACKED 或 MANUAL_REQUIRED
    并行轨道 B：无订单时 upsert RELEASE_RESERVATION；其补偿状态独立推进

ORDER_TIMEOUT:
  PREPARED → SENT → BROKER_ACKED → PROCESSING → CONSUMED
      └ publish failure → TIMEOUT_PUBLISH_PENDING ─┘
  PROCESSING 临时失败 → RETRY_PENDING → RETRY_PUBLISHING → BROKER_ACKED → PROCESSING
  第 3 次/永久错误 → CONSUME_EXHAUSTED
    并行轨道：dead_letter_status NONE → PENDING → ACKED 或 MANUAL_REQUIRED
  PREPARED/TIMEOUT_PUBLISH_PENDING/RETRY_PENDING/CONSUME_EXHAUSTED 且 due_at 已到
    → TIMEOUT_FALLBACK_PENDING → CONSUMED 或 MANUAL_REQUIRED

BUSINESS_DEAD_LETTER:
  PREPARED/DEAD_LETTER_PUBLISH_PENDING → SENT → BROKER_ACKED
  失败只回到 DEAD_LETTER_PUBLISH_PENDING；达到发布阈值为 MANUAL_REQUIRED，不触碰库存

INVALID_MESSAGE:
  直接持久化为 CONSUME_EXHAUSTED，dead_letter_status NONE → PENDING → ACKED/MANUAL_REQUIRED
  不进入订单事务，不创建 reservation 补偿
```

`SENT` 更新只允许来自对应 pending 状态且 `publish_attempt/current_correlation_id` 匹配，因此同步发送尚未返回时先到的 ack/return 不会被后写覆盖。所有 claim 使用 `version` 或 `status + locked_until` CAS；服务崩溃后超时 lease 可被另一实例回收。每个消息类型的 CHECK/Service transition 表只允许上图边，未知组合进入人工门禁。

### 3.4 Compensation record state machine

新增 `seckill_compensation_record`：

- 业务身份：`order_number,user_id,coupon_id`；
- `compensation_action`：B6 当前只有 `RELEASE_RESERVATION`；
- `first_reason/last_reason`：同步异常、nack、return、confirm timeout、消费死信、B5 取消待对账、悬空预扣等稳定枚举；来源只是审计，不拆分幂等动作 identity；
- `evidence_mask`：按位保留 `INITIAL_DELIVERY_FAILED`、`CONSUME_EXHAUSTED`、`CANCEL_COMMITTED`、`ORPHAN_RECONCILED` 等可授权释放的持久证据；upsert 只增不减，避免 first/last reason 覆盖掉关键事实；
- `status`：`PENDING → RUNNING → SUCCEEDED`，可由失败进入 `RETRY_PENDING`，超过配置次数或遇到 token/账本矛盾进入 `MANUAL_REQUIRED`；
- `attempt_count,next_retry_at,locked_by,locked_until,last_result,last_error,version,created_at,updated_at,completed_at`。

唯一键 `UNIQUE(compensation_action,order_number)`；恢复扫描索引 `(status,next_retry_at,id)`。publisher callback、取消路径和对账三方只能 upsert/claim 同一条 `RELEASE_RESERVATION`；另设追加式/受限长度的 reason 审计字段或事件记录来源，不创建第二个可执行动作。Redis token 是跨存储最终一次性执行令牌。

另新增 `seckill_reconciliation_anomaly`，专门承载“目标 token 已安全释放、但同券其他成员或 registry 仍不一致”的独立事实，不能复用补偿失败状态。字段至少包含 `anomaly_type,coupon_id,status(OPEN/RESOLVED),first_seen_at,last_seen_at,occurrence_count,sample_user_id,sample_order_number,details_hash,version`；唯一键 `UNIQUE(anomaly_type,coupon_id)`，并用版本 CAS 合并并发发现。该表没有库存操作入口；只有后续至少两轮完整扫描均证明 HASH/ZSET/registry 一致，或经过人工核验，才可转 `RESOLVED`。

补偿领取顺序：

1. MySQL CAS 将 due 的 `PENDING/RETRY_PENDING` 或过期 `RUNNING` claim 为 `RUNNING`；同 orderNumber 的其他来源只更新 reason 审计，不能形成第二个 claim。
2. 先按订单事实和 evidence 明确分支，历史 `ORDER_CREATE=CONSUMED` 不能覆盖后来取消事实：
   - 等价订单 `status=1/2`：禁止释放，并把错误投递事实标为已被有效订单吸收；
   - 等价订单 `status=3`：必须释放，`ORDER_CREATE` 是否 CONSUMED 不影响；
   - 无订单但 evidence_mask 含初始投递失败、消费耗尽、已提交取消或已证明悬空预扣：允许释放；已取消订单后来被现有删除能力删除时，补偿行内 `CANCEL_COMMITTED` 仍是授权事实；
   - 无订单且只有 `ORDER_CREATE=CONSUMED`、没有任何可授权 evidence：进入 `MANUAL_REQUIRED`，不得推断已取消或直接吸收。
3. 执行带 expected orderNumber 的 Redis Lua。
4. `APPLIED` 标记目标补偿 `SUCCEEDED` 和适用的消息补偿状态；`APPLIED_LEDGER_INCONSISTENT` 同样把**目标补偿**标为 `SUCCEEDED`，同时幂等 upsert 独立 reconciliation anomaly 并告警、保留 registry；不得把“目标已经应用”和“其他成员损坏”压成同一个失败。明确“此前已由同一 token 完成”的幂等结果也可成功。token 不匹配、目标 HASH/ZSET 不一致、key 类型/库存非法进入 `MANUAL_REQUIRED`；临时连接失败进入有限退避重试。
5. Redis 已成功但最后一次 MySQL 更新失败时，lease 到期会重跑；旧 token 已被删除，且新 token 不匹配，因此不会误回滚新预扣。实现必须把“同一唯一补偿行此前已 SUCCEEDED/已记录 redis_applied_at”与“首次发现账本缺损”区分：前者等价完成，后者进入人工门禁。由于 Redis 与 MySQL 之间不能原子写 `redis_applied_at`，在 Redis 成功后、MySQL 标记前崩溃的极小窗口允许安全停在 `MANUAL_REQUIRED`，不能冒充自动成功，但不会重复加库存或误伤新 token。

### 3.5 Consumer transaction, finite retry and acknowledgements

订单消费者和超时消费者不再直接在监听方法上承担数据库事务。监听层接收原始 AMQP `Message + Channel`，显式校验 messageId、schema version、header、payload 和字段一致性，再调用独立 Spring 事务 Bean。

订单消费先对消息日志执行持久 attempt claim：`incomingAttempt > consume_attempt` 且当前状态可处理时，CAS 为 `PROCESSING`、写入 `consume_attempt/processing_attempt/locked_by/locked_until`。同一 attempt 的并发/重投看到相同或更大已持久 attempt 时，只能续接该 attempt 的既有 `PROCESSING/RETRY_PENDING/DEAD` 事实，不能再次计数或开启第二个业务事务；lease 过期后同一 attempt 可由恢复者重领，但计数不增加。随后订单事务固定步骤：

1. `FOR UPDATE` 锁定 `ORDER_CREATE` 消息日志并验证未进入补偿/死信终态。
2. 按订单号查询：不存在则插入订单；存在且 `userId/couponId/orderNumber` 完全相同则分类为等价重复；存在但身份不一致则永久毒消息。
3. 新订单路径执行 `stock > 0` 条件扣减 MySQL 秒杀券库存，影响行数必须为 1。
4. 在同一事务把初始日志标为 `CONSUMED`，并幂等插入 `ORDER_TIMEOUT/PREPARED` 日志（payload 保留当前 `orderId` 兼容 30 分钟消息体）。
5. 提交后返回监听层；只有此时才尝试发送延迟消息。

数据库 `UNIQUE(order_number)` 是并发重复的最终兜底。先查只用于快速分类；并发插入唯一冲突必须在新事务中重新读取，只有字段完全一致才按重复成功 ack，不能吞掉活动唯一约束、其他数据冲突或坏消息。

消费尝试采用持久 attempt，而不是只依赖进程内 Spring Retry：

- `fsm-consume-attempt` 从 1 开始，固定最大 3 次；单行 CAS 条件固定为 `incomingAttempt > consume_attempt`，claim 成功才是新一次。claim 失败后重读：同 attempt 且状态为 PROCESSING/RETRY_PENDING/DEAD/CONSUMED 时分别续接或 ack；更小 attempt 一律视为迟到重复并 ack；更大但不连续进入 MANUAL_REQUIRED。测试必须覆盖失败事实已持久、broker ack 前崩溃及并发 redelivery，证明同 attempt 不会提前耗尽次数。
- 临时业务/数据库异常回滚业务事务后，在独立短事务把完整 payload、下一次时间和 `RETRY_PENDING` 持久化。持久化成功即 `basicAck` 当前原消息；恢复任务按退避到期重新发布，因此服务重启不会重置次数。
- 第 3 次失败或确定性非法消息在同一短事务把源行置为 `CONSUME_EXHAUSTED`、`dead_letter_status=PENDING` 并幂等插入 BUSINESS_DEAD_LETTER 行。当前原消息在 MySQL 已接管失败事实后 ack，不再 requeue。若源类型是 ORDER_CREATE 且数据库仍无等价订单，同一事务或紧随其后的独立幂等调用立即 upsert 唯一 `RELEASE_RESERVATION`；库存释放与 DLQ exchange 是否可达互不阻塞。若源类型是 ORDER_TIMEOUT，则由 due_at 到期兜底取消，不直接释放 reservation。
- 只有在 MySQL 完全不可用、无法把原消息所有权转交给日志时才允许 `basicNack(requeue=true)`；同时暂停对应 listener container 并发出基础设施告警，恢复探针成功后再启动。`default-requeue-rejected=false`，任何未分类异常不得依赖默认无限 requeue。
- 在严格校验前先建立稳定 quarantine identity。源 messageId 缺失、超过 128、含控制字符/不符合 B6 格式或 payload 超限时，对 `receivedExchange + receivedRoutingKey + contentType + body原始字节` 做 SHA-256（排除 deliveryTag、redelivered、x-death 等会随重投改变的属性），内部 key 固定为 `INVALID:{64位hex}`。原 messageId 只保存清洗截断前缀与完整值 hash，不作为唯一键；同一 redelivery 只能命中一条 `INVALID_MESSAGE` 和一条 DLQ 行。
- 永久反序列化/契约错误不进入业务处理事务，持久化 body hash、长度和错误摘要后直接走 `seckill.invalid.failed` 业务 DLQ。body 小于配置化取证上限时 envelope 可携带原字节，大于上限只携带 hash/长度/受限前缀，避免再次发布超限毒消息；日志不输出完整 body。持久 quarantine 事实成功后 ack 主队列，禁止因非法 identity 热 requeue。

ack/reject 表：

| 结果 | Broker 动作 |
|---|---|
| 新建订单提交成功、等价重复、已补偿后的迟到消息 | `basicAck(tag,false)` |
| 失败已持久化为 `RETRY_PENDING` 或 `CONSUME_EXHAUSTED + dead_letter_status=PENDING` | `basicAck(tag,false)`，由 MySQL 恢复/DLQ/补偿轨道接管 |
| 无法持久化接管（MySQL 基础设施故障） | 暂停容器后 `basicNack(tag,false,true)`，不得热循环 |
| 未知异常 | 先按失败接管；禁止裸 `reject/requeue` 默认策略 |

主动取消和超时取消必须复用同一个取消事务 Bean，事务边界固定为：锁定订单并执行 `status=1 → 3` CAS、恢复 MySQL 秒杀券库存、upsert 唯一 `RELEASE_RESERVATION` 补偿行并原子执行 `evidence_mask |= CANCEL_COMMITTED`。四项要么一起提交，要么一起回滚；不得在事务提交后才补写授权证据。事务代理返回后才执行 token-aware Redis Lua：成功把预建补偿行标为 `SUCCEEDED`，临时失败保持 `PENDING/RETRY_PENDING` 由恢复任务接管。已取消订单的删除路径必须先校验对应补偿行存在且含 `CANCEL_COMMITTED`；缺失时拒绝删除并告警，因此订单即使在 Redis 调用前后被删除，持久证据仍足以授权同一 token 的一次性释放。

业务 DLQ 不复用 `dead.queue`，且默认不配置消费者，确保失败消息保留供查询/人工处置。DLQ callback 用当前 publish-attempt token 在同一 MySQL 事务更新两行：ack 且无 return 时 DLQ 行 `BROKER_ACKED`、源行 `dead_letter_status=ACKED`；return/nack/timeout 时 DLQ 行 `DEAD_LETTER_PUBLISH_PENDING`、源行保持/回到 `dead_letter_status=PENDING`。旧 attempt callback 只审计。进入消费耗尽状态即输出稳定结构化 ERROR 事件 `SECKILL_MQ_DEAD_LETTER`（messageId/orderNumber/type/attempt，不含 payload）；B11 必须把该事件和 DLQ depth 接入真实告警规则。在没有外部告警平台证据前只声明“本地告警信号可验证”，不声明生产告警完成。

### 3.6 Delay message after-commit contract

延迟消息仍使用 B5 的 `delay.exchange/delay.routingKey/delay.queue`，消息体继续是订单 ID，保持 wire shape；B5 在途消息必须按 3.7 zero-inflight 门禁处置完毕，不能因 body 相同而绕过 B6 header/schema 契约。明确时序：

```text
订单事务：insert order → reduce MySQL stock → mark ORDER_CREATE CONSUMED
          → insert ORDER_TIMEOUT PREPARED(due_at=createTime+30m) → COMMIT
监听层：事务代理返回 → 按 due_at 计算剩余 TTL → publish delay message
          → confirm/return 更新 ORDER_TIMEOUT 日志
```

- 事务回滚时订单和 timeout 日志都不存在，发送函数没有调用点，因此不会产生孤立延迟消息。
- `due_at` 在订单事务中按订单 `create_time + 30 分钟` 写入且不可修改；`next_retry_at` 只是技术重试时刻，不能改变业务到期点。
- 提交后进程崩溃或发送失败时，`ORDER_TIMEOUT/PREPARED` 留在 MySQL；恢复任务按 `max(1,due_at-now)` 设置消息级 expiration 补发，不能重新获得完整 30 分钟。队列 30 分钟 TTL 只作为上限。
- 恢复时 `due_at <= now` 则不再发送新的 30 分钟消息，直接走同一可信超时取消编排并记录 `TIMEOUT_FALLBACK`；消息因 classic queue 队头行为晚于 due_at 到达时同样安全幂等。
- 重复订单消息只会命中 `UNIQUE(ORDER_TIMEOUT,orderNumber)`，不会无界创建延迟消息。
- 延迟消息可能因 confirm 不确定而重复，超时消费者继续依靠 B5 `status=1 → 3` CAS；只有 CAS 胜者回补 MySQL/Redis。
- 30 分钟后仍为待支付但 timeout 消息长期未确认时，对账任务直接调用同一可信超时取消编排作为最终兜底，并留下 `RECONCILIATION_TIMEOUT` 记录。

### 3.7 RabbitMQ topology and compatibility

| Purpose | Exchange | Routing key | Queue | Compatibility |
|---|---|---|---|---|
| 初始落单 | `market.direct` | `seckillOrder` | `market.mq` | 保留 B5 名称与现有 binding |
| 30 分钟延迟 | `delay.exchange` | `delay.routingKey` | `delay.queue` | 保留名称和消息体；TTL 固定 1800000ms |
| 到期执行 | `dead.exchange` | `dead.routingKey` | `dead.queue` | 保留；明确命名虽旧但语义是 timeout-ready，不是业务 DLQ |
| 消费失败业务死信 | `seckill.failure.exchange` | `seckill.order.failed` / `seckill.timeout.failed` / `seckill.invalid.failed` | `seckill.failure.queue`（三个 binding） | B6 新增，durable、非独占、非自动删除；默认不消费；非法 envelope 只携带稳定 quarantine key、hash、size 与清洗摘要，不复制超限原文 |

B6 不修改已存在 `market.mq` 的 arguments，有限重试由 MySQL 日志恢复发布实现，避免给旧队列原地加 DLX 导致 `PRECONDITION_FAILED`。新业务 DLQ 可在隔离 vhost 和未来生产切换窗口声明。三类消息显式使用 persistent delivery mode。

`delay.queue` 的 15→30 分钟参数冲突是 B5 已知门禁，应用不能对已有 15 分钟队列原地覆盖。虽然 timeout body 继续是 Long orderId，B5 在途消息没有 B6 messageId/schema headers，不能由严格 B6 listener 冒充兼容处理。B11 采用明确的 **zero-inflight 门禁**：关闭秒杀入口和生产者，但保持旧 B5 消费者运行；先等待 `market.mq` 消费完，再等待最长一个既有超时周期，让 `delay.queue → dead.queue` 全部完成；连续两个观测窗口核验三队列 `ready=0/unacked=0` 且无活动秒杀写事务后，才停止旧消费者。若不能等待，必须另立经审查的“消息转存为 MySQL timeout log 且持久成功后才 ack”工具，禁止 basic.get 后删除或直接丢弃。

zero-inflight 成立后再执行：MySQL 迁移和 reservation/registry backfill → 删除并以 30 分钟参数重建旧 `delay.queue` → 声明并校验 B6 failure topology → 启动 B6 consumer/recovery/reconciliation → 最后开放生产者。任何 legacy 消息在切换后出现都按发布门禁破坏处理并告警，不能静默送普通业务 DLQ。这个顺序是 B11 硬门禁，不在 B6 本地执行。

### 3.8 Reconciliation source of truth and repair rules

事实优先级：

1. MySQL 已提交 `seckill_order` 是订单及其 `status` 的事实来源。
2. Redis ZSET + reservation HASH 的完整二元组是“预扣当前仍占用”的事实来源；reservation registry 是扫描全集，不是库存事实；只看库存数字或单个 key 不足以自动修复。
3. `seckill_message_log` 是投递/消费/延迟消息所有权事实；`seckill_compensation_record` 是补偿意图、执行和人工阻塞事实。

不能直接比较 Redis stock 与 MySQL stock 是否相等：合法在途预扣尚未写入 MySQL时两者本就不同。对账按 reservation 逐条比对，不执行全局“库存对齐”。

`SeckillReconciliationTask` 使用配置化 fixed delay、批大小和安全窗口；以 Redis reservation registry 的 SSCAN 为扫描全集，并把 MySQL 有效券/消息日志中出现的 couponId 合并去重，再对每个 reservation HASH/ZSET 使用 HSCAN/ZSCAN，禁止 Redis `KEYS`。因此 MySQL 已删除券留下的 Redis reservation 仍可发现。registry/HASH 不一致进入人工门禁；B11 cutover 必须从现有 ZSET/MySQL 活动订单回填并核验 registry。每轮记录 `runCutoff`，只处理早于 cutoff/安全窗口的 reservation，避免与刚发生的预扣竞争。

| Redis reservation | MySQL / message fact | Action |
|---|---|---|
| token 与 ZSET 都存在 | 等价订单 `status=1/2` | 有效占用，不修改 |
| token 与 ZSET 都存在 | 订单 `status=3` | 唯一创建/领取取消或对账补偿，token-aware Lua 回滚 |
| token 与 ZSET 都存在 | 无订单，消息 PREPARED/SENT 且未超安全窗口 | 等待下轮；超窗后按未发送/明确失败规则补偿 |
| token 与 ZSET 都存在 | 无订单，消息 BROKER_ACKED 且消费窗口已超 | 有限重发相同 messageId/orderNumber；因原消息可能仍在 broker，不直接回滚；重发耗尽后 MANUAL_REQUIRED + 告警 |
| token 与 ZSET 都存在 | 无订单且消息明确失败/死信，或根本无日志且已过安全窗口 | 创建同一个 RELEASE_RESERVATION 补偿并自动回滚，留下记录 |
| token 对应订单号但订单身份字段不一致 | 任意 | `MANUAL_REQUIRED` + 告警，不自动写 Redis |
| HASH/ZSET 只有一边、key 类型/库存非法 | 任意 | `MANUAL_REQUIRED` + 告警，不猜测加减库存 |
| 有效订单存在但 reservation 缺失 | 任意 | 记录高优先级人工异常；无可靠库存基线时禁止盲目 DECR |

并发和重启：

- 每券使用 Redisson 有租期分布式锁减少重复扫描，但正确性不依赖锁；`UNIQUE(RELEASE_RESERVATION,orderNumber)`、MySQL claim CAS 和 Redis order token 才是最终幂等边界。
- 多实例、重复调度以及 publisher/取消/人工重复触发可同时发现同一项，但只会有一个全局 reservation-release claim；Lua 只允许一个相同 token 成功。
- `RUNNING` 记录带 `locked_until`，服务重启后可回收；扫描和恢复任务均限制批量、耗时与重试退避，避免全表/全 Redis 阻塞。
- 进程在 Redis 成功与 MySQL 完成标记之间崩溃时，重跑不会回滚新 token；无法从持久字段证明旧执行已成功时进入人工门禁，不用猜测库存。

## 4. Contracts and state transitions

### 4.1 Internal service contracts

- `SeckillReservationService#reserve(couponId,userId,orderNumber)`：只返回稳定结果码；成功代表 stock/ZSET/HASH/registry 四者同一 Lua 原子写入。
- `SeckillReservationService#rollback(couponId,userId,orderNumber)`：只有 token 精确匹配才回滚；返回 `APPLIED/APPLIED_LEDGER_INCONSISTENT/ALREADY_APPLIED/TOKEN_MISMATCH/LEDGER_CORRUPT/INFRA_FAILURE`。`APPLIED_LEDGER_INCONSISTENT` 表示目标 stock/ZSET/HASH 已原子完成释放，但同券其他成员计数或 registry 仍异常；调用方必须完成目标补偿并独立 upsert anomaly，不能再次给目标加库存。
- `SeckillMessagePrepareTransaction#prepareOrderCreate(...)`：`REQUIRES_NEW` 独立提交 PREPARED 行；返回前事务必须完成，入口不得在其外包裹 publish 事务。
- `SeckillReliablePublisher#publish(messageLog)`：从已提交持久日志构造 persistent 消息和 `CorrelationData`，按 `message_type/publish_purpose` 应用不同失败状态；不允许调用方绕过日志直接发送 B6 消息。
- `SeckillOrderConsumeTransaction#consume(command,messageId,attempt)`：真实 Spring 代理事务，返回 `CREATED/DUPLICATE/STALE_COMPENSATED`；临时错误抛出，永久契约错误在进入事务前分类。
- `SeckillTimeoutMessageService#publishAfterCommit(orderNumber)`：只能读取已提交 `ORDER_TIMEOUT/PREPARED` 日志；按不可变 due_at 计算剩余 TTL，已到期则直接走可信取消；方法内部不得创建订单或开启包裹订单事务。
- `SeckillCancellationTransaction#cancel(orderId,reason)`：主动取消与超时取消共用；在同一事务完成 `status=1→3` CAS、MySQL 库存恢复、`RELEASE_RESERVATION` upsert 和 `CANCEL_COMMITTED` evidence，提交后返回补偿 ID。已支付/已取消或 CAS 输掉按明确结果分类，不能绕过证据门禁删除已取消订单。
- `SeckillTimeoutConsumeService#consume(orderId,messageId,attempt)`：调用统一取消事务；事务提交后才按补偿 ID 执行 Redis，成功完成补偿，失败保持待重试后 ack；临时 MySQL 错误才进入有限消费重试。
- `SeckillCompensationService#claimAndRun(id)` 和 `SeckillReconciliationService#runBatch(cutoff)`：按唯一键/CAS/lease 幂等执行。

### 4.2 Failure convergence summary

| Failure window | Durable fact | Convergence |
|---|---|---|
| Lua 后、日志前进程崩溃 | Redis reservation 带 orderNumber，无 MySQL 日志/订单 | 对账过安全窗口自动创建补偿并回滚 |
| 日志后同步发送异常 | PREPARED/DELIVERY_FAILED + compensation | 当前线程立即回滚；失败由补偿任务重试 |
| 初始 publish 成功但 confirm 丢失 | ORDER_CREATE confirm timeout；broker 可能有消息 | consumer 与补偿锁同一日志行；最多一方取得事实，允许重复投递但不重复订单 |
| return 与 ack 乱序 | returned 审计 + failure-dominant CAS | 未消费则补偿；已消费则订单事实优先 |
| 消费事务中途失败 | 订单/库存/CONSUMED/timeout log 全回滚 | 持久 attempt 后延迟重发，最多 3 次 |
| 订单提交后 delay publish 失败/崩溃 | ORDER_TIMEOUT/PREPARED + immutable due_at 已提交 | 按剩余 TTL 补发；due_at 已到直接由同一取消编排兜底，不延后 30 分钟 |
| 主动/超时取消事务已提交、Redis 尚未执行或失败 | 与 status=3/MySQL 库存恢复同事务提交的 RELEASE_RESERVATION + CANCEL_COMMITTED | 提交后 token-aware Lua；进程崩溃/临时失败由同一补偿行重试，即使订单随后合法删除仍可恢复 |
| ORDER_CREATE 消费耗尽 | 源日志 CONSUME_EXHAUSTED + DLQ 行 +（无订单时）RELEASE_RESERVATION | DLQ 发布和 reservation 释放并行恢复；任一失败不阻塞另一条轨道 |
| 业务 DLQ publish 失败 | 独立 BUSINESS_DEAD_LETTER/DEAD_LETTER_PUBLISH_PENDING | 当前原消息已由 MySQL 接管，publisher 恢复任务只补发 DLQ，不触碰库存 |

## 5. File-level change surface

### Expected production files

- `backend/fashion-server/src/main/resources/application.yml`
- `backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java`
- 新增 `SeckillMqConfirmConfig`、listener container/可靠发布配置及 B6 内部常量。
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java`（入口编排拆分，不再承载事务消费者细节）
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java`（主动/超时取消统一事务编排和删除证据门禁）
- 新增 reservation、可靠发布、消费事务、消息恢复、补偿与对账服务/任务。
- `backend/fashion-server/src/main/resources/lua/seckill.lua`
- `backend/fashion-server/src/main/resources/lua/seckill_rollback.lua`
- 新增消息日志、补偿、reconciliation anomaly 实体、Mapper 与 XML；必要时为订单/券 Mapper 增加严格查询和批处理接口。
- `backend/fashion-server/src/main/java/com/fashion/FashionApplication.java`（仅当 message converter/header 契约需收口；不改变业务范围）
- `mysql/add_seckill_mq_reliability.sql` 与 `mysql/final07.sql`；`mysql/README.md` 登记边界。

### Expected tests

- publisher 回调、乱序/重复、同步异常回滚、消费者 attempt/ack 决策和 after-commit 单元/事务测试。
- 真实 Redis reservation/rollback/reconciliation Lua 测试。
- 真实 MySQL 消息/补偿 claim、anomaly 并发 upsert/收敛、重复订单、并发对账和迁移测试。
- 真实 RabbitMQ mandatory return、confirm ack/nack/timeout、有限重试、业务 DLQ、重复投递和延迟消息测试。
- 三依赖联合故障注入测试；源码/配置合约测试只能补充，不能替代上述行为证据。

## 6. Failure handling, idempotency, and compensation

- 所有跨存储操作明确拆成“本存储原子提交 + 另一存储幂等补偿”。不得使用 `@Transactional`、Lua 或 Rabbit channel transaction 宣称三者原子。
- Redis rollback 的一次性令牌是 `(couponId,userId,orderNumber)`；MySQL 落单的一次性令牌是 `order_number NOT NULL + UNIQUE(order_number)`；消息与补偿的一次性令牌分别是 `UNIQUE(message_id)`、`UNIQUE(type,business_key)` 和 `UNIQUE(RELEASE_RESERVATION,order_number)`。
- 任何 callback、scheduler、consumer 或人工触发都只能调用相同 CAS/claim 服务，不得存在第二套“直接改 status/直接加库存”的旁路。
- 业务异常摘要限制长度并去除连接串、凭据和完整 payload；错误日志只含 messageId/orderNumber/type/attempt/result。
- 恢复任务具有最大 publish/compensation attempt 和退避；达到阈值转 `MANUAL_REQUIRED` 并告警，不无限高频循环。对账发现的新悬空项仍可创建新的唯一补偿事实，但不能重置已耗尽记录。

## 7. Migration, compatibility, and rollback

### 7.1 MySQL migration

- B6 使用当前仓库模式提供可审查、可重复执行的 `mysql/add_seckill_mq_reliability.sql`，新增三张表并同步 clean baseline。B10 再将其纳入 Flyway；B6 不提前完成 B10。
- 在创建 B6 表前先预检 `seckill_order.order_number`：拒绝 NULL、空白、非十进制数字或长度超出 1–50 的值和重复；随后收紧为 `VARCHAR(50) NOT NULL`（保持现有字符集/排序规则）并验证 `idx_seckill_order_number` 是精确单列唯一索引。clean baseline 同步 NOT NULL。该 DDL 自动提交，所以预检必须先完成，旧 B5 制品对 NOT NULL schema 仍兼容。
- 三张新表按 `seckill_message_log → seckill_compensation_record → seckill_reconciliation_anomaly` 固定阶段创建。迁移对每张表分别验证精确列/CHECK/索引/默认值，并处理可证明的中断点：三表都不存在则首次创建；已有前 N 张时，只有它们**定义精确、行数均为 0**且不存在逆序对象，才允许前滚创建剩余表；三表均精确则校验数据后合法重跑。出现逆序表、已有前序表非空但后序表缺失、任一错误/部分定义或脏数据都 `SIGNAL SQLSTATE '45000'` 并要求先备份、人工审查后以前滚修复脚本处理，禁止自动 DROP/重命名或 `CREATE TABLE IF NOT EXISTS` 掩盖问题。
- 迁移脚本用阶段变量/过程内明确检查点记录可恢复步骤；真实 MySQL 测试在 `order_number` 收紧后以及每张新表创建后分别模拟中断并验证合法恢复或精确阻断。错误对象先备份 schema/data，修复只允许追加经审查 SQL；生产回滚保留新增表和 NOT NULL 约束，不执行破坏性逆迁移。
- 实际执行测试必须覆盖 clean baseline、首次升级、第二次执行、每个 DDL 中断点、错误索引/约束、NULL/空白/非规范/重复订单号、非法状态/attempt/重复业务键；比较 clean 与 upgrade 元数据等价。
- 生产执行前备份、执行窗口和正式版本号由 B10/B11 决定；本阶段不执行生产 SQL。

### 7.2 Redis cutover

- B6 新增 reservation HASH 和 registry，不能在有写流量时静默启用。B11 必须按 3.7 的 zero-inflight 顺序停流，按 `(couponId,userId)` 将 ZSET 与 MySQL 活动订单一一核对，并只在唯一匹配时回填 orderNumber token，同时把对应 couponId 加入 registry。
- ZSET 成员无唯一活动订单、活动订单无 ZSET 成员、多个候选订单或 key 类型错误均阻塞上线；不得自动猜测。悬空项先以审查过的补偿步骤处理并留记录。
- B6 写入新 token 后，直接回滚到不识别 token 的 B5 制品不安全。代码回滚边界是：继续停流量，排空/冻结 B6 消息，完成 reservation/订单/补偿对账，再使用兼容脚本重建旧键或前滚修复。不得在活跃 reservation 上直接降级。

### 7.3 RabbitMQ cutover and rollback

- 本地/测试只在独立 vhost 声明。生产的现有 `delay.queue` TTL 冲突必须按 3.7 的 B11 停写、排空、删除、重建流程处理；禁止应用启动时强行覆盖。
- 新 failure exchange/queue 是加法拓扑；回滚时可停止 B6 listener/publisher 并保留 durable queue 和消息，不删除证据。删除队列属于单独生产授权。
- 旧 `dead.queue` 的 timeout 语义和 Long payload 保持兼容；B6 不把它改成业务 DLQ。

## 8. Verification gates

### 8.1 Repeatable dependency conditions

真实集成门禁使用显式 `-Db6.integration=true -Db6.config=<absolute ignored test config>`，默认 `mvn test` 可条件跳过，但 evidence 必须另行运行显式门禁。配置文件不复制、不打印、不提交。

- MySQL：8.0.x；测试账号可 `CREATE/DROP DATABASE`；每类测试创建匹配 `fsm_b6_*_[0-9a-f]{32}` 的隔离 schema，finally 校验名称后删除。
- Redis：7.0.x；使用专用测试 database 或 `fsm:b6:it:{runId}:*` 前缀；测试前 `PING`，测试后只删除精确前缀键，禁止 FLUSHDB/KEYS 清库。
- RabbitMQ：3.12.x；使用专用 `fsm_b6_it_<runId>` vhost 与最小 configure/write/read 应用测试用户。vhost 创建/删除由单独的 test provisioner 管理身份执行，应用测试用户不持有全局管理权限；凭据只来自忽略配置/环境变量，不写命令行证据、不打印。测试只操作该 vhost，清理器先校验 `^fsm_b6_it_[0-9a-f]{32}$` 再删除。必须能核验 exchange/queue/binding/arguments、publish/consume/basic.get 和管理指标；无管理 API 时由预置 vhost 模式提供并在 evidence 如实说明无法自动删除/读取深度。
- 三者版本、时钟和连接健康检查成功后才开始联合测试。当前机器 Redis/RabbitMQ 与 Docker CLI 不可用，进入开发计划时必须先给出可重复启动方式或在 evidence 记录准确阻塞。

### 8.2 Required fault injection

| Scenario | Injection | Required evidence |
|---|---|---|
| MQ 不可达 | 专用连接工厂指向关闭端口/停止测试 broker，不改生产配置 | Lua 预扣成功后同步发送异常；stock、ZSET、HASH 原子恢复；API 失败；日志/补偿可查询 |
| publisher ack | 正常测试 binding | messageId 对应日志单调到 BROKER_ACKED，重复 ack 无副作用 |
| publisher return | mandatory 发布到测试 exchange 的无 binding routing key | return 原因持久化；后到 ack 不覆盖失败；未落单 reservation 被补偿 |
| publisher nack/通道失败 | 在独立 vhost 建 `x-max-length=1,x-overflow=reject-publish` 测试队列并填满后再发布以获取真实 broker nack；另测不存在 exchange/channel close | NACK/失败可关联；重复/乱序 callback 只产生一个全局 reservation-release 补偿 |
| confirm timeout/服务重启 | 注入丢弃 callback 或在 publish 后终止应用，保留三依赖 | 重启后恢复任务 claim；与迟到消费并发时订单或补偿只有一个事实赢家 |
| 消费者失败 | 注入可重复的 MySQL 条件失败/事务异常 | attempt 1–3 持久；当前消息被 ack 后按退避重发；无默认热 requeue |
| 业务死信 | 永久坏 payload 或连续三次失败 | failure queue 可 basic.get 到 envelope；源消息不再占主消费者；DLQ 行 BROKER_ACKED、源 dead_letter_status=ACKED 和告警事件存在 |
| 非法消息稳定隔离 | 分别重投缺失、超长、带控制字符的 messageId 与超限 payload，并改变 deliveryTag/redelivered/x-death | 相同原始内容只形成一个 `INVALID:{SHA256}`、一条 INVALID_MESSAGE 和一条 DLQ 行；有限处理后 ack，不热 requeue；DLQ 不含超限原文 |
| DLQ 不可达但需补偿 | ORDER_CREATE 消费耗尽后让 failure exchange return/nack，保持 Redis/MySQL 可用 | DLQ 行持续待恢复/告警，但同一个 RELEASE_RESERVATION 仍幂等释放 stock/ZSET/HASH，不被 DLQ 阻塞 |
| 重复投递 | 同 messageId/orderNumber 并发发布两次并制造 confirm 不确定重发 | MySQL 恰好一个订单、券库存只减一次、timeout 日志一条 |
| 事务回滚 | 在订单插入后令 MySQL 库存扣减为 0/抛错 | 订单、CONSUMED、timeout log 全回滚，delay queue 无对应消息 |
| PREPARED 可见性 | latch 阻塞 publish，独立 MySQL 连接读取日志，再释放真实 Rabbit callback/consumer | publish、confirm、return、consumer 发生前 PREPARED 已提交可见 |
| 提交后延迟发送 | 正常落单并用独立连接观察；提交返回后注入 delay publish 失败并推进测试时钟 | publish 调用时订单已提交；补发使用剩余 TTL；due_at 到期直接取消，故障不推迟 30 分钟截止点 |
| 悬空预扣 | 真实 Lua 写入带旧时间 token，不创建订单/消息日志 | 对账发现、唯一建补偿、原子修复并留痕 |
| 删除券/ACKED 老化 | registry 中有已删除券 reservation；另造 BROKER_ACKED 无订单超窗 | 删除券残留仍被发现；ACKED 只有限重发后人工告警，不被不安全回滚 |
| 对账幂等/并发 | callback、取消、两线程/两 Spring context 同时发现同一 reservation；在 Redis 成功后模拟进程终止 | 全局一个 RELEASE_RESERVATION claim/一次库存恢复；重启不误删新 token；异常账本进 MANUAL_REQUIRED |
| registry 部分损坏 | 同券保留“目标正常 token + 另一用户 ZSET-only”后回滚目标 | 目标安全恢复；HLEN/ZCARD 不一致时 registry 保留并产生账本异常，残留可被下轮发现 |
| 取消提交崩溃与删除门禁 | 构造 `ORDER_CREATE=CONSUMED`，在统一取消事务提交后、Redis 调用前终止进程；分别验证缺失 evidence 时删除被拒绝，以及保留 `CANCEL_COMMITTED` 后删除订单并重启 | 状态 CAS、MySQL 库存恢复、唯一补偿行和 evidence 同事务；重启后仍只恢复一次 Redis 库存并移除 ZSET/HASH；旧 CONSUMED 不吸收取消补偿，重跑保持 SUCCEEDED |
| publish callback 跨 attempt 乱序 | attempt 1 confirm timeout 后发 attempt 2 并 ack，再注入 attempt 1 return/nack | 逻辑 messageId 不变、CorrelationData.id 不同；旧 callback 只审计，attempt 2 状态不回退 |
| broker 重启持久性 | persistent 消息入 durable 测试队列后重启专用 RabbitMQ 实例/节点 | 消息和日志相关性仍在；若环境不允许重启必须记录 blocker，不能用声明检查冒充 |
| 迁移 | clean、legacy、second run、每个 DDL 中断点、partial/wrong/逆序 definition、order_number/消息脏数据及 50 字符订单号的 create/timeout/各类 DLQ key | 三表首次/重跑或可证明空中断前滚成功；未知/脏状态显式失败；最长合法 key 不截断；clean/upgrade 元数据等价 |

实现阶段每个行为先运行能够证明缺口的失败测试，再写最小实现。Mock 只用于 callback 顺序和异常分类单元测试；RabbitMQ/MySQL/Redis 的验收必须由真实依赖行为测试证明。最终还需完整 `backend/mvn test`、显式 B6 集成门禁、`git diff --check`、限定范围 diff 和高置信敏感信息扫描。

## 9. Confirmed decisions

> User confirmation: 2026-09-01

1. 在预扣前生成订单号，并给 Redis 增加 `reservations` HASH；预扣与回滚都以 `(couponId,userId,orderNumber)` 为令牌，解决旧补偿误伤新参与和重启窗口。
2. 初始消息在发布前先写 MySQL `PREPARED` 日志；这会让秒杀入口依赖 MySQL 可用，但为 confirm、迟到消费和对账提供持久串行化点，本阶段不建设通用 outbox。
3. PREPARED 必须由 `REQUIRES_NEW` 在 publish 前独立提交；逻辑 messageId 稳定，但每次 publish 使用 attempt 唯一 CorrelationData.id。callback 按 ORDER_CREATE initial/retry、ORDER_TIMEOUT、BUSINESS_DEAD_LETTER 分型并校验 attempt；只有 ORDER_CREATE 被确定为未落单（初始失败或消费耗尽且无订单）才释放 reservation，timeout/DLQ 发布失败只恢复各自轨道。
4. 消费失败固定最多 3 次；失败 payload/attempt 先持久化后 ack 原消息，由恢复任务退避重发。只有 MySQL 无法接管时才暂停容器并 requeue，避免毒消息无限循环。
5. 订单事务内写 `ORDER_TIMEOUT/PREPARED + immutable due_at=createTime+30m`，事务提交后再按剩余 TTL 发送；恢复不能重置 30 分钟，due_at 到期直接走可信取消。
6. 新建 `seckill.failure.exchange/queue` 作为真正业务 DLQ；现有 `dead.queue` 继续只承担 30 分钟到期处理。业务 DLQ 默认不消费，ERROR 稳定事件和队列深度留给 B11 接入生产告警规则。
7. 所有 reservation 释放来源共享 `UNIQUE(RELEASE_RESERVATION,orderNumber)`；对账通过 Redis registry 覆盖已删除券，只自动修复“完整 token+ZSET 且能证明无有效订单”的悬空预扣。BROKER_ACKED 无订单只有限重发/告警，不做不安全回滚。
8. MySQL 收紧 `seckill_order.order_number NOT NULL` 并新增消息日志、补偿、reconciliation anomaly 三表；Redis token/registry 与 30 分钟队列采用 B11 zero-inflight 切换。B6 制品写入新 token 后不能带活跃 reservation 直接降级回 B5。

## 10. Independent review

- Round 1 verdict: FAIL（P0=0，P1=6，P2=4，P3=1；独立只读 reviewer，文件修改 0）
- Round 1 findings addressed in this revision:
  - 将 ORDER_CREATE initial/retry、ORDER_TIMEOUT、BUSINESS_DEAD_LETTER 的 callback、失败状态和补偿动作拆开，消费死信后明确收敛 reservation。
  - 增加不可变 due_at 和剩余 TTL，补发不再重置 30 分钟截止点。
  - 将补偿 identity 收敛为全局 `RELEASE_RESERVATION + orderNumber`，来源只做审计。
  - 把 B5 在途消息切换改为 zero-inflight 硬门禁，禁止停消费者后含糊“排空”。
  - 增加 Redis reservation registry 和 BROKER_ACKED 老化动作，覆盖已删除券与不确定 broker 所有权。
  - 固定 PREPARED `REQUIRES_NEW` 独立提交，并增加独立连接/latch 证据。
  - 明确 persistent delivery mode、单行 attempt claim CAS、DDL 中断前滚/阻断规则和 `order_number NOT NULL` 脏数据门禁。
  - 区分 RabbitMQ provisioner 管理身份与最小权限测试用户。
- Round 2 verdict: FAIL（P0=0，P1=4，P2=2，P3=0；独立只读 reviewer，文件修改 0）
- Round 2 findings addressed in this revision:
  - 为 ORDER_TIMEOUT 补齐消费临时失败、attempt 2/3、消费耗尽、业务 DLQ 与 due_at fallback 的合法状态边。
  - ORDER_CREATE 消费耗尽后，reservation release 与业务 DLQ 发布改为相互独立的恢复轨道；DLQ 不可达不再阻塞库存/用户占用释放。
  - 逻辑 messageId 保持稳定，CorrelationData.id 和 header 改为 publish-attempt 唯一；旧 attempt callback 只审计。
  - messageId/businessKey/sourceMessageId 扩为 128，correlation ID 扩为 160，并增加最长订单号迁移测试。
  - registry 只在 HLEN 与 ZCARD 同时为 0 时移除；数量不一致保留扫描入口并返回告警结果。
  - DLQ callback 用 attempt token 在同一 MySQL 事务更新 DLQ 行和源行 dead-letter 子状态，ack/return 乱序不再留下虚假 DEAD_LETTERED。
- Round 3 verdict: FAIL（P0=0，P1=1，P2=3，P3=0；独立只读 reviewer，文件修改 0）
- Round 3 findings addressed in this revision:
  - 补偿先判订单最终事实和持久 evidence：已取消或已删除但保留 CANCEL_COMMITTED 的订单不能被旧 CONSUMED 吸收；活动/已支付订单禁止释放。
  - INITIAL 发布也统一使用 attempt 唯一 `currentCorrelationId={messageId}:P1`，不再复用逻辑 messageId。
  - `APPLIED_LEDGER_INCONSISTENT` 明确为目标补偿成功，并用独立、幂等、无库存操作入口的 anomaly 表跟踪其他成员损坏。
  - 非法/缺失/超长 messageId 与超限 payload 使用排除易变 broker 属性的 SHA-256 稳定 quarantine identity，有限隔离且不复制超限原文。
- Round 4 verdict: FAIL（P0=0，P1=1，P2=0，P3=0；独立只读 reviewer，文件修改 0）
- Round 4 finding addressed in this revision:
  - 主动取消与超时取消统一为一个事务：`status=1→3` CAS、MySQL 库存恢复、唯一补偿行和 `CANCEL_COMMITTED` evidence 同时提交；提交后才执行 Redis。删除已取消订单受 evidence 门禁保护，并增加提交后/Redis 前进程终止、删除、重启恢复测试。
- Round 5 verdict: PASS（P0=0，P1=0，P2=0，P3=0；独立只读 reviewer，文件修改 0）
- Round 5 conclusion: 第 4 轮唯一 P1 已关闭；全量复核未发现新的状态机矛盾、跨存储竞态、迁移不可恢复点或范围扩张。用户已于 2026-09-01 确认 Design；产品代码仍受 workpack plan 确认门禁约束。
