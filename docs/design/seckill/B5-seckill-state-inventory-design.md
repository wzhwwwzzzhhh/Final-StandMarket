# B5 秒杀支付、取消与库存闭环 · Design

> Status: 已确认（2026-09-01）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B5 / GitHub Issue #14
> Baseline: `master` @ `bfa4d77dbedfd539bf41076d19e418e92119f9f6`
> Updated: 2026-09-01

## 1. Goal and scope

### In scope

- 将秒杀订单持久状态收敛为 `1=待支付、2=已支付、3=已取消`，支付、用户取消、管理端取消和超时取消全部以 `status=1` 的数据库 CAS 竞争唯一终态。
- `updatePayTime` 只写支付时间；实际确认支付由单条 `1 -> 2` SQL 同时写状态与支付时间，杜绝二次递增。
- 取消的 CAS 与 MySQL 秒杀券库存回补在同一真实 Spring/MySQL 事务内完成，只有 CAS 唯一赢家可以回补一次。
- MySQL 提交后执行 Redis Lua，在一个原子脚本中恢复库存并移除该券的已购用户；失败必须可观察，不伪装成完整成功。
- 调整秒杀订单活动唯一约束，使已取消订单不永久阻止同一用户重新参与，同时继续禁止同一用户拥有重复待支付/已支付活动订单。
- 用户端取消按钮调用真实取消接口；管理端只对待支付订单开放支付确认和取消，不再用前端假成功掩盖后端结果。
- 延迟关闭统一为 30 分钟，并补齐代码、测试和运维兼容说明。
- 使用 TDD、真实 Spring/MyBatis/MySQL 和真实 Redis 验证状态竞态、事务回滚、Lua 原子性与可重新参与。

### Out of scope

- B6 的 publisher confirm/return、投递失败即时回滚、有限消费重试、业务死信、持久补偿记录和定时对账。
- 接入真实第三方秒杀支付网关；B5 收紧现有可信管理确认入口，不新增模拟或随机支付路径。
- B10/B11 的生产迁移执行、RabbitMQ 队列切换、生产 Redis/MySQL 对账和部署。
- 秒杀券 `limit_per_user > 1` 的计数型限购重构；当前产品继续执行每用户每券最多一个活动订单。
- 普通订单、退款、评价及 B7-B9 的功能。

## 2. Current behavior and constraints

- `SeckillOrderMapper#updatePayTime` 当前执行 `pay_time = ?, status = status + 1`，`confirmPayment` 又先做 `1 -> 2`，随后把订单推进为 `3`；“支付成功”会实际变成“已取消”。
- 通用 `updateStatus` 虽带 `status=1` 条件，但调用方使用先读后写且忽略影响行数；确认支付和取消无法可靠报告竞态输赢。
- B4 的用户取消已经把 `user_id + status=1` 放进同一条 SQL，但只改订单状态，没有回补 MySQL/Redis 库存。
- 超时监听器在无事务方法中先 CAS、再分别回补 MySQL 与 Redis；任一步失败都会形成半完成状态，而且 Redis 只加库存、不移除 ZSET 已购用户。
- `SeckillCouponMapper#addStock` 返回 `void`，无法验证券不存在或写入异常；MySQL 回补与订单 CAS没有共同受检的事务契约。
- `seckill.lua` 以 ZSET 成员阻止重复参与；取消后若不原子执行 `ZREM + INCRBY`，用户仍不能重新抢购或库存可能重复增加。
- `seckill_order` 的 `UNIQUE(user_id, coupon_id)` 对取消记录也生效。即使 Redis 已正确回滚，后续异步插单仍会因历史取消记录违反唯一约束。
- 延迟队列 TTL 是 `900000ms`（15 分钟）；直接修改已存在 RabbitMQ 队列参数会触发 `PRECONDITION_FAILED`，生产切换必须由 B11 处理旧队列。
- 用户端 `SeckillOrder.vue` 当前只弹出“取消成功”，没有调用后端取消 API；管理端同时展示可信人工确认与取消操作。
- 当前生产路径中未发现随机秒杀支付代码；需要通过源码契约测试防止重新引入，并移除任何前端假成功行为。

## 3. Design decisions

### 3.1 唯一状态迁移

| 动作 | MySQL 前置条件 | 成功后 | MySQL 库存 | Redis 动作 |
|---|---|---|---|---|
| 异步落单 | 无相同活动订单 | `status=1` | 条件扣减 1 | Lua 已在入口预扣 |
| 确认支付 | `status=1` | `status=2, pay_time=now` | 不变 | 不变 |
| 用户取消 | 当前用户所有且 `status=1` | `status=3` | 同事务回补 1 | 提交后原子回补 |
| 管理端取消 | `status=1` | `status=3` | 同事务回补 1 | 提交后原子回补 |
| 超时取消 | 指定订单且 `status=1` | `status=3` | 同事务回补 1 | 提交后原子回补 |

- 支付使用专用 `markPaid(orderNumber, payTime) -> int`，单条 SQL 同时更新状态和时间，`WHERE status = 1`。
- 取消使用用户归属版与可信内部版专用 CAS，均固定写 `3` 且固定 `WHERE status = 1`，不接受调用方传任意目标状态。
- 删除未被合法入口使用的通用状态写能力，或至少从公开 Service 契约移除，避免绕过固定迁移。
- `updatePayTime` 若因兼容需要保留，只允许单独写 `pay_time`，绝不修改状态；支付流程不得再组合“状态更新 + 更新时间”两条 SQL。
- 所有写方法返回影响行数并由调用方检查；`0` 是状态冲突/不存在，不得继续库存补偿。

### 3.2 MySQL 取消事务边界

新增独立的秒杀取消事务 Bean，所有入口都经真实 Spring 代理调用，禁止同类 self-invocation。事务传播固定为以下唯一方案：

- 用户/管理/超时的外层取消编排方法全部**不声明事务**，不得由带事务的同类方法包裹。
- 内层 MySQL 取消 Bean 使用 `@Transactional(propagation = REQUIRES_NEW)`；即使未来调用方意外已有事务，也必须挂起外层事务并独立提交。
- 只有内层代理方法正常返回后，外层才执行 Redis Lua；此时内层事务已经提交。外层后续异常不能回滚已经对外声明提交的 MySQL 取消事实。

内层事务步骤：

1. 按订单号（用户入口还必须带当前 `user_id`）读取取消所需的 `order_id/user_id/coupon_id/status`。
2. 执行固定 `status=1 -> 3` CAS；影响行数不是 `1` 时返回冲突，且不做任何库存写入。
3. 调用 `restoreStock(couponId) -> int` 恢复 MySQL 库存；影响行数必须恰好为 `1`，否则抛出未检查异常。
4. 异常使订单 CAS 与库存回补整体回滚；成功则返回只含 `orderNumber/userId/couponId` 的 Redis 回补命令。

用户、管理端和超时入口复用同一事务核心。用户入口的查询和 CAS 都保留 `user_id` 条件，不能因 B5 回归 B4 的资源归属边界。支付和取消竞争同一订单行的 `status=1`，数据库保证只有一个迁移成功。

### 3.3 Redis 原子回补

新增 `lua/seckill_rollback.lua`：

- `KEYS[1]` 为 `seckill:coupon:stock:{couponId}`，`KEYS[2]` 为 `seckill:coupon:users:{couponId}`。
- `ARGV[1]` 为回补数量（B5 固定 `1`），`ARGV[2]` 为用户 ID。
- 在第一次写之前验证：回补数量精确为 B5 支持的正整数 `1`、用户 ID 为非空十进制整数、库存 key 类型为 string、用户 key 类型为 zset 或不存在、库存值为规范非负十进制整数且不超过 `2147483646`。缺 key、wrong-type、非整数、负数或上溢风险均返回不同错误码且两个 key 都不变。
- 再确认用户仍在 ZSET；用户 key 不存在或成员不存在时返回“未执行”，不增加库存，避免脚本重放造成重复回补。
- 完成全部只读验证后先执行已证明不会类型/整数/上溢失败的 `INCRBY`，再执行已证明 key 为 zset 且成员存在的 `ZREM`，返回成功码。Lua 执行期间其他客户端不能改变已验证 key，因此写入阶段不再包含可预见的运行时错误。
- 不能用“Redis Lua 运行时错误会自动回滚”作为原子性依据；Redis 不回滚脚本错误前已经完成的写入。本设计通过“写前穷尽验证 + 安全写入顺序”保证错误发生前无写入。

只有 MySQL 事务 CAS 的赢家才调用 Lua。Lua 在内层 MySQL 事务成功返回、即数据库已经提交之后执行，绝不在 CAS 前回补 Redis。

### 3.4 跨存储失败语义

MySQL 与 Redis 不声明原子提交：

- MySQL 失败或 CAS 输掉：不调用 Lua，接口返回状态冲突/失败。
- MySQL 已提交且 Lua 成功：取消完整成功。
- MySQL 已提交但 Lua 抛错、返回缺库存 key、wrong-type、非法库存或用户不存在：记录 `orderNumber/userId/couponId/result` 的 ERROR 日志；用户/管理端返回业务成功包中的“待对账”结果，页面显示“订单已取消，库存恢复待处理”并重新加载真实订单状态。不得返回普通失败诱导用户反复提交，也不得伪称库存已完整恢复。
- 超时路径遇到上述 Redis 异常同样记录明确 ERROR；B5 不通过再次执行 MySQL CAS来重放 Lua，因为订单已经是 `3`，盲目重放会混淆是否已补库存。
- B6 必须基于补偿记录、有限重试和对账收敛上述窗口；因此 B5 可本地合并验证，但在 B6 完成前不可部署。

### 3.5 允许取消后重新参与的唯一约束

用活动订单唯一标记替换永久 `UNIQUE(user_id, coupon_id)`：

```sql
active_marker TINYINT
  GENERATED ALWAYS AS (CASE WHEN status = 3 THEN NULL ELSE 1 END) STORED,
UNIQUE KEY uk_seckill_order_active_user_coupon (user_id, coupon_id, active_marker)
```

- 只有显式已取消 `status=3` 的 marker 为 `NULL`；MySQL 唯一索引允许多个 `NULL`，所以多次“抢购后取消”不会被历史记录永久阻断。
- 待支付/已支付以及任何迁移过程中意外出现的未知状态 marker 均为 `1`，不会因脏状态错误释放唯一约束。
- 同步把 `status` 收紧为 `NOT NULL DEFAULT 1` 和 `CHECK (status IN (1,2,3))`。升级预检必须使用 `status IS NULL OR status NOT IN (1,2,3)`，发现空值/未知值显式失败，禁止自动猜测业务状态。
- 异步插单仍依赖订单号唯一约束实现消息幂等；活动唯一冲突不可静默吞掉，可靠消费治理属于 B6。
- B5 不扩展 `limit_per_user` 计数语义；若将来允许购买多份，需要单独重做 Redis 计数和数据库约束。

提供 B5 幂等迁移 SQL并同步 `mysql/final07.sql`。迁移必须校验旧索引定义、`status` 列定义/CHECK、生成列表达式和新索引列顺序；发现未知同名对象、`NULL`/未知状态或异常活动重复数据时显式失败，不猜测修复。`final07.sql` 中秒杀订单 dump 改为带显式列名的 INSERT，不向 generated column 写值。生产执行留给 B10/B11。

### 3.6 30 分钟延迟与 RabbitMQ 兼容

- 代码中用命名常量表达 `30 * 60 * 1000` 毫秒，并以配置/合约测试断言为 `1800000`，禁止散落魔法数字。
- 文档和用户/管理端提示统一为 30 分钟；不声称 15 分钟兼容。
- RabbitMQ 不允许原地修改已声明队列的 TTL。B11 部署时必须先停秒杀入口和生产者、排空或处置旧延迟队列、删除旧队列后以 30 分钟参数重建并核验；在此之前 B5 不部署。
- B5 不提前实现 B6 的版本化队列、confirm/return 或消费重试策略。

### 3.7 Controller 与前端契约

用户与管理取消接口沿用项目的 HTTP 200 + `Result<T>` 包装，并固定 `data` 为 `SeckillCancelResponse`：

```text
SeckillCancelResponse {
  orderNumber: string,
  orderStatus: 3,
  outcome: "CANCELLED" | "REDIS_RECONCILIATION_PENDING",
  message: string
}
```

- 完整成功：`Result.code=1`，`outcome=CANCELLED`，精确文案“取消订单成功”。
- MySQL 已提交但 Redis 未完成：仍为 `Result.code=1`，`outcome=REDIS_RECONCILIATION_PENDING`，精确文案“订单已取消，库存恢复待处理”。这是“订单状态迁移成功但存在基础设施待对账”，不能使用 `Result.error`。
- 不存在、越权或 CAS冲突：`Result.code=0`、无成功 DTO，精确文案“订单不存在或状态已变化，无法取消”；不泄露其他用户订单事实，不调用 Redis。
- 未预期的 MySQL 事务失败：`Result.code=0`、文案“取消订单失败”，数据库状态和库存已回滚。

- 用户端取消只对 `status=1` 展示，并调用 `/user/seckill/order/cancel/{orderNumber}`；`code=1/outcome=CANCELLED` 显示 success，`code=1/outcome=REDIS_RECONCILIATION_PENDING` 显示 warning，`code=0` 显示 error，三种结果都刷新列表以读取真实状态。
- 管理端确认支付和取消都只对 `status=1` 展示；后端仍以 CAS 为最终边界，不能信任按钮可见性。
- 管理确认支付是现有受管理员鉴权保护的可信人工入口，不增加随机结果；生产代码和目标 Vue 文件不得包含基于 `Random/Math.random` 的支付状态决定。
- 用户取消、管理取消和超时取消均调用同一编排能力；Controller 不直接写状态或库存。

## 4. Contracts

### Mapper contracts

- `markPaid(orderNumber, payTime) -> int`：固定 `SET status=2, pay_time=? WHERE order_number=? AND status=1`。
- `cancelPending(orderNumber) -> int`：可信内部/管理/超时固定 `1 -> 3`。
- `cancelPendingByOrderNumberAndUserId(orderNumber, userId) -> int`：用户归属版固定 `1 -> 3`。
- `updatePayTime(orderNumber, payTime) -> int`：若保留，仅写时间，不写状态。
- `restoreStock(couponId) -> int`：目标券存在时加 `1` 并返回 `1`；否则为 `0`，调用方必须使事务失败。

### Service contracts

- 当前用户 ID 只从 `BaseContext` 获取；未登录、越权与不存在不触发库存回补。
- 内层取消事务返回 `CANCELLED` 或 `CONFLICT`，只有 `CANCELLED` 携带 Redis 命令；内层使用 `REQUIRES_NEW`，外层取消编排无事务。
- Redis 结果区分 `ROLLED_BACK`、`NOT_APPLIED` 和 `INFRASTRUCTURE_ERROR`；后两者均按跨存储不一致记录，不冒充完整成功。
- 用户/管理端将 `ROLLED_BACK` 映射为 `Result.code=1/CANCELLED`，将后两者映射为 `Result.code=1/REDIS_RECONCILIATION_PENDING`；CAS冲突映射为 `Result.code=0`。
- 重复支付、重复取消、主动/超时并发以及支付/取消并发都最多有一个 MySQL CAS 赢家。

## 5. File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/service/SeckillOrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java`
- 新增独立秒杀取消事务 Bean 与必要的内部结果类型。
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/SeckillOrderMapper.java`
- `backend/fashion-server/src/main/resources/mapper/SeckillOrderMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/mapper/SeckillCouponMapper.java`
- `backend/fashion-server/src/main/resources/mapper/SeckillCouponMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserSeckillOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/admin/SeckillOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java`
- 新增 `backend/fashion-server/src/main/resources/lua/seckill_rollback.lua`
- `frontend/fashion-client/src/api/seckill.js`
- `frontend/fashion-client/src/views/SeckillOrder.vue`
- `frontend/fashion-admin/src/views/SeckillOrderList.vue`
- `mysql/final07.sql` 与新增 B5 幂等迁移 SQL。

### Expected tests

- Mapper/Service 状态机与取消事务单元测试。
- 支付、取消、延迟 TTL、前端真实 API 和“无随机支付”的静态契约测试。
- Lua 脚本真实 Redis 集成测试。
- 真实 Spring/MyBatis/MySQL 并发与回滚测试。
- B5 迁移 SQL 静态及真实 MySQL 执行测试。
- 用户端与管理端生产构建。

## 6. Failure handling and idempotency

- MySQL 库存回补失败必须抛出异常，订单取消 CAS一并回滚；禁止 catch 后返回 `false` 导致事务提交。
- Redis Lua 以“ZSET 成员仍存在”为唯一执行令牌；同一脚本重放不会重复加库存。脚本在任何写入前验证 key 类型、库存整数范围和参数，真实运行时错误测试必须证明两个 key 都不变。
- 数据库 CAS 以 `status=1` 为一次性令牌；只有赢家产生 Redis 回补命令。
- 支付与取消的 SQL 都不采用“先读状态正确就视为成功”；最终以影响行数为准。
- 延迟消息重复到达时，已支付/已取消订单的 CAS 返回 `0`，不再回补任一库存。
- Redis 回补失败后的可靠重试没有足够持久事实，明确留给 B6；B5 只保证错误可观察和不扩大错误。

## 7. Migration, compatibility, and rollback

- 迁移前暂停秒杀入口、支付确认、主动取消、超时消费者和异步落单消费者，排空在途数据库事务并盘点 `status IS NULL OR status NOT IN (1,2,3)`、重复活动订单和 Redis/MySQL 库存差异。
- 迁移脚本先验证旧 `idx_user_coupon(user_id,coupon_id)` 精确定义，再增加生成列/新唯一索引并移除旧索引；或在安全顺序中保证任何时刻不开放写流量。部分执行、同名错误对象或异常历史数据必须阻断。
- 干净基线与升级后 schema 的 `status NOT NULL/CHECK`、生成列表达式、列类型和索引列顺序必须等价；脚本需验证首次执行和重复执行。
- 测试必须实际执行更新后的 `final07.sql` 中 `seckill_order` DDL 与带列名 dump INSERT，证明 generated column 不造成列数不匹配或显式写生成列错误；不得只检查 DDL 文本。
- RabbitMQ 旧 15 分钟队列不能由应用启动时自动改参。B11 负责停写、排空/处置、删除、30 分钟重建及参数核验；B5/B6 未完成前禁止部署。
- 代码回滚不得恢复会把 `pay_time` 与状态递增绑定的旧 Mapper。若已应用活动唯一迁移，生成列和新索引保留；回滚制品必须兼容该 schema 并保留 B5 状态机安全边界。
- 生产迁移、Redis 修复和队列操作不在 B5 本地交付授权内。

## 8. Verification gates

- TDD RED：证明旧支付变成取消、主动/超时竞态可能补两次、用户取消不回补、Lua 缺失、取消后数据库唯一约束阻止重新参与、TTL 仍为 15 分钟、用户端假成功。
- 聚焦单元/合约测试：固定 CAS SQL、影响行数检查、真实事务 Bean 边界、Controller 结果、Lua 脚本结构和两端 UI。
- 真实 Spring/MyBatis/MySQL：支付/取消竞态只有一个终态；主动/超时双取消只回补一次；库存回补注入失败使订单 CAS回滚；用户越权不写；取消后可再次插入活动订单而已支付订单仍阻止重复。另用独立连接/事务探针证明 Lua 调用点数据库已经提交、Spring 当前无活动外层事务，且 Lua 后抛出的外层异常不能回滚 MySQL 取消事实。
- 真实 Redis：成功脚本同时加库存和移除用户；重放不重复加；缺库存 key、wrong-type、非整数、负值和上溢场景两个 key 都不变；并发脚本只有一个执行者。
- 真实 MySQL 迁移：完整更新基线 DDL+dump 从零导入、首次升级、合法重跑、错误旧索引、错误 status 列/CHECK、错误生成列/新索引、NULL/未知状态及异常数据阻断，并比较 clean/upgrade 元数据等价。
- 后端聚焦测试和完整 `mvn test`；用户端、管理端分别 `npm run build`。
- `git diff --check`、限定范围 diff、敏感信息扫描；只记录实际运行结果。
- 实现完成后必须独立 Review PASS；B6、B0-AC6、B10/B11 仍是生产发布门禁。

## 9. Decisions requiring user confirmation

1. 支付使用单条 `status=1 -> 2 + pay_time` CAS；兼容 `updatePayTime` 仅写时间，不能再承担状态迁移。
2. 用户、管理端和超时取消共用独立 Spring 事务 Bean；外层编排无事务、内层固定 `REQUIRES_NEW`，订单 `1 -> 3` CAS 与 MySQL 库存回补同事务且在 Lua 前已经提交。
3. Redis Lua 在任何写前验证参数、key 类型和库存整数范围，之后以 ZSET 成员为一次性令牌，按安全顺序原子完成“库存加一 + 移除用户”；不依赖脚本错误回滚。
4. MySQL 已取消而 Redis 回补失败时，接口固定返回 `code=1/outcome=REDIS_RECONCILIATION_PENDING` 并记录“库存恢复待处理”；可靠重试和对账由 B6 实现，因此 B6 前不可部署。
5. 用“只有 `status=3` 才为 NULL”的生成活动标记唯一索引替换永久 `(user_id,coupon_id)` 唯一约束，并把状态收紧为 `NOT NULL + CHECK(1,2,3)`；取消后可重新参与，其他状态不释放唯一约束。
6. 管理确认支付继续作为受鉴权的可信人工入口，但不允许任何随机/前端模拟支付；用户端取消必须调用真实 API。
7. 延迟 TTL 改为 30 分钟；现有 RabbitMQ 15 分钟队列的停写、排空、删除与重建留给 B11，不能直接带旧队列部署。

## 10. Independent review

- Verdict: PASS（第二轮：P0/P1/P2/P3 均为 0；用户已确认）
- Round 1 findings addressed:
  - Redis 脚本错误不回滚：改为写前验证全部类型/数值/溢出条件，再按不会报错的顺序写，并增加真实错误场景不变性测试。
  - 事务提交时点不确定：固定外层无事务、内层 `REQUIRES_NEW`，增加提交可见性与外层异常测试。
  - NULL/未知状态释放唯一性：只有显式 `3` 生成 NULL，并增加 `NOT NULL/CHECK` 与脏状态阻断。
  - “缓存待恢复”响应不确定：固定 `SeckillCancelResponse`、Result code/outcome、精确文案与两端映射。
  - clean baseline dump 风险：改用带列名 INSERT，并实际执行 DDL+dump、比较 clean/upgrade 元数据。
- Round 2: 首轮五项均已关闭，未发现新增 P0-P3 或 B5/B6 范围漂移。
