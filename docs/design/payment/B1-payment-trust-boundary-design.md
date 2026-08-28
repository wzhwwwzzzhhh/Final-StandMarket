# B1 支付可信边界与并发幂等 · Design

> Status: 已确认
> Requirement source: [阶段 B：B1 支付入口、通知校验与幂等](../../plans/阶段B-P0P1交易链路修复.md#b1支付入口通知校验与幂等p0)
> Tracking: [GitHub Issue #6](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/6)
> Updated: 2026-08-28
> Confirmed: 2026-08-28（用户确认）

## 1. Goal and scope

建立普通订单支付宝支付的可信状态迁移边界：客户端只能发起支付和查询本人订单，只有经过完整校验的支付宝异步通知可以把普通订单从待支付迁移为已支付；并发发起支付和重复通知不得产生多个活动支付流水或重复迁移。

### In scope

- 普通订单支付宝发起、本人状态查询和只读同步回跳。
- 支付宝异步通知的签名、`app_id`、金额、交易状态、支付记录类型和订单前置状态校验。
- 支付记录创建的数据库级活动流水唯一约束与并发冲突收敛。
- 普通订单支付记录与订单状态在同一数据库事务内一次性迁移。
- 移除普通订单、用户端秒杀订单的随机模拟支付入口，以及管理端普通订单人工确认支付入口。
- 删除对应前端调用或按钮；未接入真实网关的支付方式不得伪装成功。
- 为共享 HTTP 客户端配置连接与读取超时。

### Out of scope

- 新增微信支付或秒杀订单真实支付网关。
- 管理端秒杀订单确认支付、秒杀支付/取消状态机和库存补偿；这些由 B5 收口。
- 普通订单库存回补、完整取消/确认收货/发货状态机；这些由 B2 收口。B1 只为避免覆盖支付结果，把当前用户取消和超时取消改为待支付 CAS，不在此补库存闭环。
- 人工补单能力、双人复核和审计流程。
- 支付宝退款、主动查询补单或对账能力。
- Flyway 基线和生产迁移执行；Flyway 统一接入属于 B10。
- B2-B11 的其他交易、库存和发布工作。

## 2. Current behavior and constraints

- 当前本地 `master` 位于 `d0dda71`，远端 `master` 已包含 B0 合并提交 `94627c5`；本地存在 10 个 B1 产品文件的未提交修改（103 增、59 删），不得 reset、丢弃、擅自 stash 或混入范围外修改。
- 已有存量修改实现了本人普通订单状态查询、`order_type=0` 查询、同步回跳只读以及支付/订单部分 CAS，但尚无自动化测试。
- `PUT /user/order/pay/{id}` 和 `POST /user/seckill/order/pay/{orderNumber}` 仍调用随机成功的 `processPayment`；管理端普通订单仍可直接确认收款。
- 管理端通用 `PUT /admin/order/{id}/status` 接收完整 `Orders`，可写 `status/pay_status/checkout_time`；`OrderService.updatePaySuccess` 也是未审计的普通订单直写入口。二者会绕过“只有异步通知可置已支付”的边界。
- 存量 B1 diff 已新增 `cancelPending` SQL，但用户取消和超时取消仍调用无条件动态 `update`，可能用旧快照覆盖已支付状态。
- 异步通知仍在验签失败时记录完整参数，且订单前置状态只在后续 CAS 间接判断；B0 已在远端修复完整参数日志，B1 集成时必须保留该修复。
- `payment` 表仅对 `pay_no` 唯一；“先查再插”不能阻止并发创建多个活动支付流水。
- 项目尚未接入 Flyway。`mysql/payment_table.sql` 是历史建表脚本，不得作为已在生产执行的证据。
- Issue #6 明确依赖 Issue #4；在 #4 关闭前，只能准备 Design/workpack 和盘点，不得继续修改产品代码。

## 3. Design decisions

### D1. 可信入口与公开契约

- 普通订单唯一真实发起入口保留为 `POST /user/pay/alipay/{orderId}`。
- 删除普通订单随机模拟入口 `PUT /user/order/pay/{id}`、用户端秒杀随机模拟入口 `POST /user/seckill/order/pay/{orderNumber}` 及其不可再使用的 service 方法。
- 删除管理端普通订单 `PUT /admin/order/{id}/confirm-payment`。未来人工补单必须单独立项。
- 删除未使用的 `OrderService.updatePaySuccess` 直写入口。管理端通用状态接口改用只含 `status` 的专用 DTO/Service，并明确拒绝 `status=2`；它不得接收或写入 `pay_status/checkout_time`，也不得把 `pay_status!=1` 的订单推进到 `3/4/5`。B2 再统一已支付订单的其余合法迁移。
- 管理端秒杀订单确认支付由 B5 处理，不在 B1 偷改秒杀状态机；B1 只移除用户端随机模拟路径。
- 用户端仅允许支付宝进入真实支付流程。微信等未接入网关的方式在创建/订单界面禁用或明确显示不可用，不再调用 mock 接口。

### D2. 支付创建由服务端在订单行锁内完成

- Controller 不再先读取订单再把客户端可影响的金额传给通用创建方法。
- 支付 Service 在一个事务内按 `orderId` 对普通订单执行 `SELECT ... FOR UPDATE`，再校验当前用户、`status=1`、`pay_status=0`，并只使用订单持久化金额创建或复用支付宝支付记录。
- 订单行锁串行化同一订单的支付创建与异步回调；数据库唯一约束作为绕过正常 service 路径时的最终兜底。
- 支付表单只能使用该事务返回的 `pay_no` 和服务端订单金额。

### D3. 一个订单类型最多一个活动支付流水

- 活动支付记录定义为 `status IN (0, 1)`；`2` 成功、`3` 失败为终态。
- MySQL 8 为 `payment` 增加两个持久化生成列：活动时分别映射 `order_id`、`order_type`，终态时为 `NULL`；在两列上建立唯一索引。MySQL 唯一索引允许多组 `NULL`，因此保留历史终态流水，同时拒绝同一 `(order_id, order_type)` 的第二条活动流水。
- 创建流程可先查后插以复用现有活动流水。插入遇到 `DuplicateKeyException` 后只做一次 `SELECT ... FOR UPDATE` 当前读：若同订单类型的活动赢家存在，则校验并返回；若不存在则原样抛出，以免把 `pay_no` 或其他唯一键冲突误判为活动流水竞争。不得通过捕获所有数据库异常掩盖其他错误。
- 复用活动流水时核对金额与支付方式；不一致即失败，不静默覆盖。
- 失败流水进入 `status=3` 后可创建新尝试；已支付订单即使活动索引释放，也会被订单行锁内的 `pay_status/status` 校验拒绝。

### D4. 只有异步通知可以迁移普通订单为已支付

- 同步回跳接口可以验签并返回当前支付状态，但不得调用任何更新支付或订单的方法。
- 异步通知先完成签名、必填字段、`app_id`、金额、`order_type=0` 校验；支付记录和订单必须存在且关联一致。`TRADE_SUCCESS` 与 `TRADE_FINISHED` 均作为支付宝成功终态处理，其他已验签状态只确认收到、不迁移。
- 事务处理统一按“锁定订单 → 复核订单前置状态 → 支付记录 CAS → 订单 CAS → 核销优惠券”的顺序执行，避免创建与回调锁顺序相反。
- 支付记录只允许 `0/1 → 2`；普通订单只允许 `status=1 AND pay_status=0 → status=2 AND pay_status=1`。
- 支付记录、订单和优惠券核销处于同一 Spring 事务。订单 CAS 或后续动作失败时，支付记录更新一并回滚。
- 精确重复以原支付事实为准：事务内确认 payment/order/type/amount 关联一致、`payment.status=2`、已记录 `trade_no` 与通知一致，且订单仍承认支付事实。订单可已推进到 `status=2/3/4` 且 `pay_status=1`；退款处理中可为 `status=6/pay_status=1`，真实退款完成后可为 `status=6/pay_status=2`。这些组合返回成功且零写入；待支付、取消、支付字段矛盾或交易号不一致则返回失败并告警。
- 用户取消和超时取消改用 `status=1 AND pay_status=0` 的 `cancelPending` CAS，只有 CAS 成功才释放优惠券；与回调竞争时最多一个迁移成功。B1 不据此宣称 B2 的库存回补和全订单状态机已经完成。

### D5. HTTP 超时是显式配置

- `RestConfig` 构建的共享 `RestTemplate` 必须设置有限的连接和读取超时：`fashion.http.client.connect-timeout-ms` 默认 3000 ms，`fashion.http.client.read-timeout-ms` 默认 30000 ms，并允许通过同名外部配置覆盖。
- B1 只保证不会无限等待；Agent 服务地址和更细粒度的专用客户端配置仍由 B9 处理。

## 4. Contracts and state transitions

### 普通订单支付接口

| Contract | Authentication | Behavior |
|---|---|---|
| `POST /user/pay/alipay/{orderId}` | 用户登录 | 锁定并校验本人待支付普通订单，创建/复用唯一活动流水，返回支付宝表单 |
| `GET /user/pay/status/{orderId}` | 用户登录 | 只查询本人普通订单及 `order_type=0` 的支付记录，不更新状态 |
| `POST /user/pay/alipay/verify` | 用户登录 | 验证同步回跳并只读返回本人普通订单状态，不更新状态 |
| `POST /notify/paySuccess` | 支付宝签名 | 完整校验后执行普通订单事务性 CAS；仅一致的精确重复幂等返回 |
| `PUT /admin/order/{id}/status` | 管理员登录 | 专用 DTO 仅允许非支付状态字段；拒绝 `status=2`，不得写 `pay_status/checkout_time`，未支付订单不得推进到 `3/4/5` |

被移除的契约返回 404/405（不再注册映射），不得保留“返回失败但仍可调用”的模拟实现。管理端通用状态接口不能作为替代支付入口。

### 状态迁移

```text
payment: 0 待支付 ─┐
                   ├─[有效异步通知 + CAS]─→ 2 成功
payment: 1 支付中 ─┘
payment: 0/1 ──[可信失败路径；B1 不新增网关失败回调]─→ 3 失败

orders: status=1,pay_status=0
  ──[同一事务内 payment CAS 成功]─→ status=2,pay_status=1
```

同步回跳、状态查询、客户端按钮、管理端普通订单接口均没有支付状态迁移权。

### 支付宝异步通知响应矩阵

| Case | Database effect | Literal response |
|---|---|---|
| 验签失败、缺少必填字段、`app_id`/金额/类型/关联不一致 | 零写入 | `failure` |
| `TRADE_SUCCESS` 或 `TRADE_FINISHED`，订单与支付均处于合法前置状态 | 同一事务迁移一次 | 事务提交后 `success` |
| 同一 `trade_no` 且 payment 成功，订单已支付后处于待发货/已发货/已完成/退款中，或真实退款已完成 | 零写入 | `success` |
| 支付或订单仅一方完成、已记录 `trade_no` 不同、订单仍待支付/已取消或支付字段矛盾 | 零写入并告警 | `failure` |
| 其他已验签交易状态 | 零写入 | `success`（确认收到但不代表支付成功） |
| 数据库异常、CAS 后续失败或事务回滚 | 整体回滚 | `failure` |

Stage B 未把 `seller_id` 列为 B1 验收项，本设计不把它新增为完成门禁；如后续加入商户身份纵深校验，必须先补充可配置的可信 seller 标识并更新 Design。

## 5. File-level change surface

### 后端

- `controller/user/PaymentController.java`
- `controller/notify/PayNotifyController.java`
- `controller/user/UserOrderController.java`
- `controller/user/UserSeckillOrderController.java`
- `controller/admin/OrderController.java`
- `service/PaymentService.java`、`service/impl/PaymentServiceImpl.java`
- `service/OrderService.java`、`service/impl/OrderServiceImpl.java`
- 新增管理端订单状态专用 DTO，并移除普通订单支付字段直写 Service；用户/超时取消复用待支付 CAS
- `service/SeckillOrderService.java`、`service/impl/SeckillOrderServiceImpl.java`
- `mapper/PaymentMapper.java`、`resources/mapper/PaymentMapper.xml`
- `mapper/OrderMapper.java`、`resources/mapper/OrderMapper.xml`
- `config/RestConfig.java` 及必要的非敏感默认配置
- 新增支付 Controller/Service/Mapper/配置测试

### 数据库

- 更新 `mysql/payment_table.sql` 的新建库结构。
- 新增 B1 独立、可审查的已有库增量 SQL，包含重复活动流水预检、生成列和唯一索引；不得自动删除或改写冲突支付数据。

### 前端

- `frontend/fashion-client/src/views/Order.vue`
- `frontend/fashion-client/src/views/CreateOrder.vue`
- `frontend/fashion-client/src/views/PayResult.vue`
- `frontend/fashion-client/src/api/payment.js`
- `frontend/fashion-client/src/api/product.js`
- `frontend/fashion-client/src/api/seckill.js`
- `frontend/fashion-client/src/views/SeckillOrder.vue`
- `frontend/fashion-admin/src/api/order.js`
- `frontend/fashion-admin/src/views/OrderList.vue`

## 6. Failure handling, idempotency, and compensation

- 唯一索引冲突后通过一次锁定当前读寻找同订单类型赢家；重新读取不到赢家或字段不一致时返回明确失败，不循环无限重试。
- 异步通知缺字段、验签失败、`app_id`/金额/类型不一致或订单关联错误时返回 `failure`，且不得写库。
- 非成功交易状态不迁移；对无需处理且签名有效的通知返回稳定响应，避免无意义重试。
- 重复成功通知由支付 CAS 和订单 CAS 双层限制；只有首个事务执行优惠券核销。相同 `trade_no` 且订单仍承认原支付事实才视为可安全确认的重复；订单后续发货、完成或退款不会否定原支付通知。
- 支付回调与用户/超时取消竞争时，订单前置状态 CAS 决定唯一赢家；取消 CAS 失败不得释放优惠券。其他普通订单状态机缺陷明确留给 B2，因此 B1 只声明支付边界与关键取消竞争闭合，不声明全局订单状态机闭合。
- 回调事务异常整体回滚并返回 `failure`，让支付宝重试；日志只记录必要标识和失败类别，不记录完整参数或密钥。
- B1 不引入跨存储操作，因此不声称 Redis/MQ 补偿或跨存储原子性。

## 7. Migration, compatibility, and rollback

1. 增量脚本先检查元数据：两列和目标唯一索引全部存在时明确报告“已应用”并 no-op；只存在部分对象时 `SIGNAL` 失败，禁止猜测修复。
2. 在测试 MySQL 8 上预检 `(order_id, order_type)` 的多条 `status IN (0,1)`。若有冲突则 `SIGNAL` 停止并人工核对；脚本不得猜测赢家或删除支付记录。
3. 无冲突时用一条 `ALTER TABLE` 同时增加两列和唯一索引，依赖 MySQL 8 原子 DDL 避免部分对象。上线执行需停掉应用支付写入；B1 不执行生产迁移。即使预检后出现竞争写，唯一索引构建也必须以失败而不是重复数据结束。
4. 冲突清理后可以重跑；成功后重跑按第 1 步 no-op。任何部分对象状态都要求人工审查，不自动 DROP/重建。
5. B10 接入 Flyway 时将该结构纳入版本化历史，不能把“存在脚本”当作“已部署”。
6. 结构是加法变更，旧代码显式列出 INSERT 字段，不依赖生成列。应用回滚时优先保留唯一索引；若必须回退结构，先停止写入、确认无并发活动支付，再单独删除索引和生成列。

## 8. Verification gates

- Controller 测试：本人/他人订单、正确 `order_type`、同步回跳零写入、模拟 URL 无映射、管理端普通确认 URL 无映射、管理端通用状态接口不能写支付字段/设置 `status=2`，也不能推进未支付订单。
- 通知测试：签名、必填字段、`app_id`、金额、记录类型、关联、`TRADE_SUCCESS`、`TRADE_FINISHED`、其他已签名状态；一致重复覆盖待发货/已发货/已完成/退款中/退款完成，不一致终态覆盖待支付/取消/交易号冲突，并断言精确 `success/failure` 响应。
- Service 单元测试：订单行锁内校验、活动流水复用、唯一冲突一次 current-read 收敛、金额不一致拒绝和异常传播。
- MySQL 8/Testcontainers 事务集成验证：并发创建复用同一活动流水；目标唯一冲突后可见赢家；双回调只有一次 payment/order/coupon；券或订单后续失败时 payment 回滚；回调与取消竞争只有一个最终状态且无死锁；DDL 历史冲突失败、清理后重试、成功后重跑 no-op，并拒绝“只有一个生成列”或“有列无索引”等部分 schema。环境不可用时 AC2/AC3/AC7 保持阻塞，不能用 mock 测试冒充通过。
- 配置测试：连接/读取超时均为有限正值。
- 全量后端 `mvn test`，用户端/管理端 `npm run build`，以及 `git diff --check`、范围和敏感信息复核。

## 9. Decisions requiring user confirmation

1. 采用“订单行锁 + MySQL 8 生成列唯一索引”保证同一订单类型最多一个活动支付流水，并允许保留多个终态历史尝试。
2. B1 交付独立增量 SQL 和新建库 DDL，但不执行生产迁移；Flyway 登记延后到 B10。
3. B1 删除用户端普通/秒杀随机模拟入口和管理端普通订单确认入口；同时封死管理端通用状态接口的支付字段/`status=2` 旁路。管理端秒杀确认入口留给 B5。
4. 在真实微信网关立项前，用户端只允许支付宝支付，微信入口禁用或标记不可用。
5. 为避免支付回调被旧快照取消覆盖，B1 将用户取消和超时取消改为待支付 CAS；库存回补和完整订单状态机仍留给 B2。
6. 共享 HTTP 客户端默认连接超时 3 秒、读取超时 30 秒，并可由外部配置覆盖；B9 再拆分 Agent 专用配置。

## 10. Independent review

- Round 1 verdict: FAIL（2026-08-28）
- Round 1 findings: 5 个 P1（管理端支付旁路、取消覆盖竞态、通知响应矩阵、唯一冲突/DDL 重试、真实数据库并发证据）和 1 个需修订的 P2（精确重复一致性）已在本稿修订；`seller_id` 不作为 Stage B B1 必须项。
- Round 2 verdict: FAIL（2026-08-28）
- Round 2 findings: 1 个 P1（已发货/已完成后的相同支付通知应视为一致重复）和 2 个 P2（部分 schema 迁移测试、未支付订单不能由管理通用状态接口推进）已修订。
- Round 3 verdict: PASS（2026-08-28）
- Round 3 findings: P0-P3 均无；前两轮发现已关闭。
- User confirmation: 2026-08-28，Design 已确认。项目维护者随后明确解除 B1 本地开发阻塞，进入实现。
