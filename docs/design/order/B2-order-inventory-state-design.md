# B2 普通订单库存与状态闭环 · Design

> Status: 已确认（2026-08-30）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B2 / GitHub Issue #8
> Baseline: `master` @ `df5a480d851b8802bdef6e8bf8aaf2d9d09b5736`
> Updated: 2026-08-30

## 1. Goal and scope

### In scope

- 普通订单创建在一个 MySQL 事务内完成服务端计价、通用券锁定、商品条件扣库存、订单及明细落库。
- 使用数据库条件更新和确定性锁顺序防止超卖，并保证任一步失败时整体回滚。
- 为订单增加明确的库存占用标识，取消/超时取消仅由一次成功的待支付 CAS 触发库存回补。
- 支付、发货、确认收货均通过限定合法前置状态的 SQL 更新完成迁移。
- 支付发起/复用与首次异步成功回调使用同一库存前置条件，禁止为历史未扣库存订单创建、返回活动支付流水或首次迁移为已支付；已经成功的精确重复回调按既有支付事实幂等返回。
- 普通订单忽略客户端金额、秒杀活动 ID 和秒杀券 ID，落库时明确写入非秒杀状态。
- 移除订单查询、取消、确认收货中未登录回退到用户 `1` 的逻辑。
- 为超时扫描补齐覆盖普通订单的查询与索引，并以真实 MySQL 8 验证并发、回滚和竞态。

### Out of scope

- B3 的退款外部完成状态与退款库存回补。
- B5 的秒杀 Redis/MQ/MySQL 库存闭环。
- B10 的 Flyway 基线和生产迁移执行。
- B0-AC6 外部凭据处置、生产数据库操作和生产发布。
- B3 退款实现本身；B2 只记录并设置“B3 完成前禁止部署”的硬门禁，不提前改写退款业务。
- 对现有地址归属、SKU 独立库存模型或购物车交互做产品层重构。

## 2. Current behavior and constraints

- `OrderServiceImpl#create` 只先读 `product.stock` 判断库存，未执行扣减；并发请求可同时通过检查并超卖。
- 普通订单当前会读取客户端传入的 `activityId` / `couponId` 并应用秒杀优惠，且将相关字段写入实体；这违反普通订单信任边界。
- `OrderMapper#insert` 没有写入实体已有的 `original_price`、`is_seckill`、`seckill_activity_id`、`seckill_coupon_id` 字段。
- `orders` 没有库存是否已成功扣减/是否已回补的持久标识；当前取消只迁移状态和释放通用券，不回补库存。
- 超时任务只扫描 `user_coupon_id is not null` 的订单，无券普通订单永远不会超时取消。
- 支付已有 `订单行锁 → 支付记录行锁` 的 B1 锁顺序和待支付 CAS，但 `markPaid` 尚未约束库存已经成功扣减。
- `PaymentServiceImpl#createAlipayPayment` 目前只校验订单/支付状态，仍会为历史未扣库存订单创建或复用活动流水；若只收紧回调，会形成网关已扣款但订单无法置为已支付的窗口。
- 发货先读状态再调用通用 `update`；确认收货和管理端状态更新均可能覆盖并发变化，且确认收货没有合法前置状态限制。
- `listUserOrders`、`cancel`、`confirm` 在缺少登录上下文时回退到用户 `1`。
- 当前 `mysql/final07.sql` 有按用户、状态、支付状态的索引，但没有同时覆盖 `status + pay_status + order_time` 的超时扫描索引；基线还缺少 B1 已使用的 `user_coupon_id` 字段。
- `CouponServiceImpl` 的绑定、核销、释放对零影响行数静默返回，不能兑现“券状态与订单/库存一起回滚”的事务承诺。
- 当前 `RefundServiceImpl#approve` 会直接回补库存，却不清理库存标识且审批不是 CAS。B2 与 B3 之间存在已知冲突，因此 B2 可本地合并验证，但在 B3 完成前不可部署到任何可办理退款的环境。

## 3. Design decisions

### 3.1 库存标识

在 `orders` 增加 `stock_deducted TINYINT(1) NOT NULL DEFAULT 0`：

- `0`：该订单当前没有可回补的普通商品库存；包括 B2 上线前的历史订单、创建尚未成功扣减的订单，以及取消/后续退款已经完成回补的订单。
- `1`：普通订单库存已在创建事务中成功扣减且尚未回补。

选择布尔标识而不是从订单状态推断库存，是因为历史订单从未扣减，且后续 B3 退款也需要与“已支付/已完成”状态独立判断是否允许一次回补。该列在 B2 创建/取消链路内是库存事实源；旧退款审批尚未接入该列，所以 B3 完成前设置部署硬门禁，不能把 B2 单独发布为完整交易闭环。

### 3.2 普通订单信任边界

- 保留 `OrderCreateDTO.amount/activityId/couponId` 字段以兼容现有客户端请求结构，但普通订单创建路径不读取它们参与计价或识别订单类型。
- 仅服务端商品现价、购物车数量及服务端校验后的 `userCouponId` 参与普通订单金额计算。
- 普通订单固定写入 `is_seckill = 0`、`seckill_activity_id = NULL`、`seckill_coupon_id = NULL`、`seckill_price = NULL`。
- `original_price` 写服务端重算的优惠前商品总额；`amount` 写扣除合法通用券后的金额。

### 3.3 创建事务与锁顺序

`create` 保持一个 Spring/MySQL 事务，顺序固定为：

1. 校验登录用户、请求及购物车项归属；拒绝重复的购物车项 ID、非正数数量、失效商品。
2. 生成订单明细快照和服务端金额；按 `product_id` 聚合数量。
3. 按 `product_id` 升序执行条件更新：`UPDATE product SET stock = stock - ? WHERE id = ? AND status = 1 AND stock >= ?`。
4. 任一更新影响行数不是 `1` 立即抛出未检查异常；前面已经扣减的库存由事务回滚。
5. 如使用通用券，调用现有乐观锁接口锁券并计算折扣。
6. 插入 `stock_deducted = 1` 的普通订单，绑定通用券订单 ID，批量插入明细。

所有涉及多个商品的扣减和回补都按商品 ID 升序执行，以降低交叉订单的死锁概率；遇到 MySQL 检测到的死锁由当前请求整体回滚，不在业务层局部重试。

订单声明了 `user_coupon_id` 时，锁券、绑定订单、支付核销和取消释放都必须恰好影响 `1` 行；任何零行或多行结果均抛出未检查异常并回滚订单/库存事务。无券订单才允许跳过券写入。

### 3.4 取消与超时处理

- 用户取消和超时取消都先 `SELECT ... FOR UPDATE` 锁定订单行；所有订单竞争路径继续遵守“订单行优先”的 B1 规则。
- 随后执行单条 CAS：仅当 `status = 1 AND pay_status = 0 AND stock_deducted = <锁定行快照值>` 时，更新为已取消并将 `stock_deducted` 置 `0`。
- 只有 CAS 影响行数为 `1` 且锁定快照为 `stock_deducted = 1` 的调用方，才按订单明细聚合并升序回补商品库存。
- 同一事务内释放已锁通用券；任一商品不存在、回补影响行数异常或券操作抛错时，订单 CAS、已回补库存和券释放一起回滚。
- 历史 `stock_deducted = 0` 待支付订单允许取消，但不回补库存；支付路径不允许这类订单转为已支付。
- 超时查询覆盖所有 `status = 1 AND pay_status = 0` 且超过 30 分钟的订单，不再要求绑定通用券。
- 新建独立 Spring Bean `OrderCancellationService`，其用户取消和超时取消入口均为 `@Transactional`。`OrderServiceImpl` 的用户取消委托给该 Bean；`OrderTimeoutTask` 先分页查询候选 ID，再逐个从 Bean 代理调用 `cancelTimeoutOrder(id)`。禁止在 `OrderServiceImpl` 内 self-invocation 事务方法。
- 超时取消单笔失败记录订单 ID 并留待下轮重试，不回滚同批其他订单；候选查询按 `id` 升序使用固定批量上限，每轮从当前最小待处理候选重新 keyset 取数，不使用会因结果集收缩而跳项的 OFFSET。

### 3.5 支付发起与切换门禁

- `PaymentServiceImpl#createAlipayPayment` 在订单行锁内要求 `status = 1 AND pay_status = 0 AND stock_deducted = 1`，无论新建还是复用活动支付流水都先执行该校验。
- 异步成功回调的首次 `markPaid` 同样要求 `stock_deducted = 1`，与支付发起形成一致的前后门禁；已成功支付的精确重复通知不再调用 `markPaid`。
- 已成功支付记录的精确重复通知先按订单已支付事实和相同 `trade_no` 返回幂等成功，再进入首次支付的库存门禁；历史 `stock_deducted = 0` 已支付订单因此不会因精确重复通知回归为失败。已成功记录收到不同 `trade_no` 仍按冲突拒绝，不能借幂等分支覆盖支付事实。
- 切换 B2 前暂停普通订单下单与支付发起，查询并盘点所有 `stock_deducted = 0` 的待支付订单及其活动支付流水；历史待支付订单统一关闭/失效支付入口，只允许取消，不允许继续付款。
- 已经送达支付宝但尚未回调的活动流水必须等待最终结果或按网关查询/人工对账处置。无法证明已关闭或未付款的历史活动流水时不得部署 B2；晚到成功通知不得直接丢弃，必须进入人工对账和退款处置记录。
- 切换前还必须将所有历史 `status IN (2, 3)` 普通订单完成履约，使其不再处于待发货或待确认收货状态；不得把这些历史订单盲目回填为 `stock_deducted = 1`。增量迁移发现 `status IN (2, 3) AND stock_deducted = 0` 时必须显式失败，阻止产生无法继续履约的半切换状态。

### 3.6 合法状态迁移

| 动作 | 前置条件 | 成功后 | 库存动作 |
|---|---|---|---|
| 创建普通订单 | 登录、购物车归属合法、所有条件扣减成功 | `status=1, pay_status=0, stock_deducted=1` | 已扣减 |
| 支付成功 | `status=1, pay_status=0, stock_deducted=1` | `status=2, pay_status=1` | 无 |
| 用户/超时取消 | `status=1, pay_status=0` | `status=5, stock_deducted=0` | 仅原值为 `1` 时回补 |
| 发货 | `status=2, pay_status=1, stock_deducted=1` | `status=3` | 无 |
| 确认收货 | 当前用户所有且 `status=3, pay_status=1, stock_deducted=1` | `status=4` | 无 |

- 支付和取消都先锁订单行，再执行带前置状态的更新；支付/取消竞态只可能有一个迁移成功。
- 发货和确认收货使用专用 CAS Mapper，不再通过通用实体 `update` 写状态。
- 管理端通用状态接口不得直接写“待发货”、绕过物流信息发货或把已支付订单改成取消；目标状态 `3` 使用发货接口，目标状态 `4` 仅允许从 `3` CAS，目标状态 `5` 在 B3 退款设计前拒绝。

## 4. Contracts and state transitions

### Mapper 原子契约

- `ProductMapper.deductStock(productId, quantity) -> int`：仅 `stock >= quantity` 且商品启用时返回 `1`。
- `ProductMapper.restoreStock(productId, quantity) -> int`：商品存在时返回 `1`，否则失败并触发事务回滚。
- `OrderMapper.cancelPending(...) -> int`：同时校验待支付状态、未支付状态和期望库存标识，并原子写取消状态/时间/库存标识。
- `OrderMapper.markPaid(...) -> int`：增加 `stock_deducted = 1` 前置条件。
- `OrderMapper.markDelivered(...) -> int`：仅 `2/已支付/已扣库存` 可转 `3`，同时写物流信息和发货时间。
- `OrderMapper.confirmReceived(id, userId, time) -> int`：仅订单所有者的 `3/已支付/已扣库存` 可转 `4`。
- `UserCouponMapper.setUseOrderId/useCoupon/releaseCoupon(...) -> int`：订单声明使用通用券时必须返回 `1`，否则调用方抛异常。

所有调用方必须检查影响行数；`0` 表示状态已变化、库存不足或非法前置状态，不能当成幂等成功继续执行补偿。

### 服务契约

- 用户未登录统一抛出“用户未登录”，不会查询或修改用户 `1` 的数据。
- 普通订单 API 接收到伪造金额或秒杀 ID 时不采用这些值；响应和落库金额均来自服务端计算。
- 重复取消返回状态冲突，不重复回补；超时任务遇到已经支付/取消的候选订单记录跳过。
- 支付发起和复用活动支付流水都拒绝 `stock_deducted != 1` 的订单；不能只在成功回调阶段拒绝。
- 订单声明使用通用券时，绑定、核销或释放零行不是幂等成功；只有明确的无券订单可以无券写入。

## 5. File-level change surface

- `backend/fashion-server/.../OrderService.java`、`OrderServiceImpl.java`：创建事务、逐单超时取消、登录校验和合法迁移编排。
- 新建 `OrderCancellationService`：提供经 Spring 代理调用的用户/超时逐单事务边界，禁止同类自调用规避代理。
- `backend/fashion-server/.../PaymentServiceImpl.java`：支付发起/复用活动流水前校验库存已经扣减。
- `backend/fashion-server/.../OrderMapper.java` / `resources/mapper/OrderMapper.xml`：订单插入字段、行锁、取消/支付/发货/确认 CAS、超时候选查询。
- `backend/fashion-server/.../ProductMapper.java` / `resources/mapper/ProductMapper.xml`：条件扣减与受检回补。
- `backend/fashion-server/.../OrderDetailMapper.java` / XML：取消回补所需的明细聚合/稳定读取。
- `backend/fashion-server/.../ShoppingCartMapper.java` / XML：批量读取并校验当前用户选择的购物车项，消除逐条读取造成的快照不一致窗口。
- `backend/fashion-server/.../CouponService.java`、`CouponServiceImpl.java`、`UserCouponMapper.java` / XML：收紧锁券、绑定、核销、释放的影响行数契约，并在订单声明有券时对零行抛错。
- `backend/fashion-server/.../OrderTimeoutTask.java`：分页扫描所有普通待支付订单，并逐笔调用独立取消 Bean。
- `backend/fashion-pojo/.../OrderCreateDTO.java`、`Orders.java`：标明普通订单忽略字段并增加库存标识。
- `mysql/final07.sql`、新增 B2 幂等 SQL：补齐字段与超时索引；生产执行留到 B10。
- 聚焦单元测试与 MySQL 8 集成测试：覆盖条件扣减、全事务回滚、重复取消和支付/取消竞态。
- `RefundServiceImpl` 本阶段不修改；其旧回补行为作为 B3 前部署阻断项进入 evidence 和阶段门禁。

## 6. Failure handling, idempotency, and compensation

- 创建路径中的库存扣减、券锁、订单/明细插入全部属于同一数据库事务，不使用异步补偿；任一未检查异常回滚全部写入。
- 取消路径中的订单 CAS、库存回补和券释放也属于同一数据库事务。若回补中途失败，已经回补的商品与订单取消状态一起回滚，下一次仍可重试。
- 重复取消依赖订单 CAS 影响行数判定；库存标识不作为单独的最终成功判断，避免“标识已清但库存未加”的提交窗口。
- 超时任务逐单事务隔离失败；调度层不吞掉订单 ID 和异常类型，但不得记录用户敏感信息。
- 支付回调已完成的 B1 幂等语义保留；新增库存前置条件不会把失败支付记录误更新为成功。
- 精确重复支付通知必须在首次支付的 `stock_deducted = 1` 门禁之前识别；仅订单已经支付、支付记录已经成功且 `trade_no` 一致时幂等返回，任何冲突通知仍失败。
- 有券订单的绑定/核销/释放若影响 `0` 行必须抛错，让订单、支付、库存与券状态一起回滚；重复支付回调仍在进入券核销前由支付/订单幂等分支返回。
- 订单明细为空、商品 ID 为空、数量非正或聚合溢出均视为库存事实损坏，取消回补必须失败并整体回滚，不能跳过异常明细后提交取消。
- B2 不修复旧退款审批；任何仍可触发 `RefundServiceImpl#approve` 的环境都不满足 B2 部署条件。

## 7. Migration, compatibility, and rollback

- 新增幂等 SQL 通过 `information_schema` 检查后添加 `orders.stock_deducted`、缺失的 `orders.user_coupon_id` 和 `idx_orders_timeout(status, pay_status, order_time)`；若同名列/索引/约束已存在，必须校验数据类型、空值约束、默认值、索引列顺序以及 `stock_deducted` 的 CHECK 表达式完全一致，不一致时失败而不是跳过。`stock_deducted` 增加 `CHECK (stock_deducted IN (0, 1))` 或 MySQL 8 等价约束；同步更新 `mysql/final07.sql` 新建库基线。
- 现有订单统一保持默认 `stock_deducted = 0`，不根据历史状态猜测扣库存事实。历史待支付订单可取消但不可继续支付；这是防止无库存占用订单成交的有意兼容限制。
- 历史待发货/待确认收货订单不能依靠默认值继续履约，也不能伪造库存事实。迁移脚本在加列后（以及重复执行时）检查 `status IN (2, 3) AND stock_deducted = 0`，存在任何记录即 `SIGNAL` 失败；发布操作必须先在旧版本完成这些订单的发货和确认收货，并保存盘点/处置审计证据。
- B2 只允许本地开发、合并和测试，不得单独部署；可部署版本必须同时满足 B3 已按阶段 B 需求封死审核通过时的直接库存回补（审核只做 `0 → 1`，不写退款完成、不改库存），B0/B11 发布门禁通过，以及历史活动支付流水清理完成。未来接入可信退款完成通知时，才允许把一次性回补接入同一 `stock_deducted` CAS；这不是当前 B3 的交付要求。
- 将来部署顺序为：停止普通订单下单/支付发起/用户取消/超时任务 → 盘点历史待支付订单与活动支付流水 → 在旧版本完成并清零所有历史 `status IN (2, 3)` 履约中订单 → 应用带履约前置检查的兼容性 DDL → 部署同时包含 B2 与“已移除审核回补”的 B3 安全逻辑版本 → 运行校验查询 → 恢复流量。生产 DDL 执行仍属于 B10/发布流程，本阶段只提供和在测试库验证脚本。
- 纯 B1 代码不是有效回滚目标，不设置“排空待支付订单后可回 B1”的例外，因为已支付、已发货和已完成的 `stock_deducted = 1` 订单仍会被 B1 退款审批直接回补且不清标识。任何回滚制品必须保留 B2 的库存标识、支付发起/回调门禁、取消回补逻辑，并至少保留 B3 对旧退款审批回补的封堵；新增列和索引保留，不在紧急回滚中删除。
- 脚本必须在干净 schema、已有部分字段/索引 schema 和重复执行三种场景验证；任何定义冲突都中止并给出人工修复提示。

## 8. Verification gates

- 测试先行：先写失败测试，证明当前代码会超卖、不会扣库存、重复/竞态迁移和非法状态更新存在缺口。
- Mapper/Service 聚焦测试：普通订单忽略客户端金额和秒杀 ID；未登录不回退用户 `1`；所有 CAS 影响行数均受检。
- 真实 MySQL 8：
  - 多线程争抢有限库存，成功订单数量与库存扣减一致且最终库存不为负。
  - 在第二个商品扣减、锁券后、订单插入后和明细插入时注入失败，验证订单、明细、券、所有库存均回滚。
  - 并发重复取消只回补一次；支付与取消竞态最终只存在一个合法终态。
  - 验证超时查询使用目标复合索引或记录 `EXPLAIN` 结果。
  - 超时批次中间订单注入失败，验证前后订单各自提交/重试，证明调用确实经过独立 Bean 的事务代理。
- 支付 Service 测试：历史 `stock_deducted = 0` 订单无论新建还是复用活动流水都被拒绝；`stock_deducted = 1` 的合法订单才可取得支付表单；首次成功回调满足相同库存前置条件。另直接覆盖三例：①历史订单已支付、支付记录成功、`stock_deducted = 0` 且 `trade_no` 相同，零写入幂等成功；②相同状态但 `trade_no` 不同，拒绝；③尚未支付且 `stock_deducted = 0` 的首次成功通知，拒绝。
- 优惠券事务测试：绑定、核销、释放各自返回零行时，订单/支付状态、库存和券状态全部回滚。
- 迁移测试：校验列/索引定义、部分 schema 恢复、脚本重跑，以及部署前未决订单/活动支付流水查询。
- 残余风险门禁：evidence 必须明确记录 B3 未完成时 `RefundServiceImpl#approve` 与库存标识冲突，因此 B2 状态只能是“本地完成/不可部署”，不得写“可上线”；B3 的解除条件是移除审核阶段直接回补，不是提前实现真实退款完成回补。
- 交付前执行后端聚焦测试、`backend/` 下 `mvn test`、`git diff --check`、限定范围 diff 与敏感信息复核。
- 独立审查必须 PASS；Design 确认后另建 `docs/workpack/B2-order-inventory-state/plan.md`，产品代码仍需再次等待该 plan 的用户确认。

## 9. Decisions requiring user confirmation

1. 采用 `stock_deducted` 布尔列作为普通订单库存占用事实源；历史订单默认 `0`，不推断、不补扣。
2. 历史 `stock_deducted = 0` 待支付订单允许取消但禁止支付，以避免无库存占用订单成交。
3. 普通订单保留但忽略 DTO 中客户端金额和秒杀 ID，固定落为非秒杀订单；秒杀下单留给 B5 专用链路。
4. 超时取消扩大到所有待支付普通订单，并按订单独立事务处理。
5. 管理端通用状态更新收紧：发货走专用接口，完成仅允许 `3 → 4`，取消/退款在 B3 前不允许绕过专用流程。
6. 支付发起/复用与首次成功回调要求 `stock_deducted = 1`；已经成功的精确重复通知按订单/支付事实和相同 `trade_no` 零写幂等返回，冲突 `trade_no` 拒绝。历史待支付订单只允许取消，既有活动支付流水必须在部署前关闭、等待终态或人工对账。
7. B2 可以本地实现和合并，但不得单独部署；必须等 B3 按阶段计划移除审核阶段直接回补。当前 B3 不实现真实退款完成回补，未来可信退款完成路径才复用库存 CAS。纯 B1 永远不是有效回滚目标。
8. 有券订单的绑定、核销、释放必须各影响恰好一行，否则整个交易事务回滚。

## 10. Independent review

- Verdict: PASS（第三次独立审查）
- Findings:
  - P0：支付发起未校验库存标识，可能真实扣款后回调失败；已补充支付入口、既有活动流水盘点和切换门禁。
  - P1：纯 B1 回滚会漏回补；最终结论为纯 B1 不是有效回滚目标。
  - P1：旧退款审批与库存标识冲突；已明确 B3 完成前禁止部署 B2。
  - P1：逐单超时事务缺少代理边界；已固定独立 `OrderCancellationService` Bean。
  - P1：优惠券零行更新被静默接受；已改为有券订单必须恰好一行的事务契约。
  - 二次 P1：B3 门禁误写成当前阶段实现回补 CAS；已改为 B3 仅封死审核回补，未来可信完成路径再复用 CAS。
  - 二次 P1：纯 B1 回滚例外仍会破坏已支付/完成订单库存标识；已删除例外并明确纯 B1 不是有效回滚目标。
  - 二次 P2：超时扫描改为 keyset 限批；DDL 同名定义校验补充 CHECK 约束表达式。
  - 第三次审查：首轮及二次审查问题均已闭合，剩余 P0/P1/P2 均为零。
