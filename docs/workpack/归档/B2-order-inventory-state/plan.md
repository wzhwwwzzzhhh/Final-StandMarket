# B2-order-inventory-state · Workpack plan

> Status: 已归档（PR #9 首轮 checks 全绿，待合并；B3 前不可部署）
> Requirement source: [阶段 B：B2 普通订单库存与状态闭环](../../../plans/阶段B-P0P1交易链路修复.md#b2普通订单库存与状态闭环p0)
> Tracking: [GitHub Issue #8](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/8)
> Design: [B2 普通订单库存与状态闭环](../../../design/order/B2-order-inventory-state-design.md)（2026-08-30 原版及历史兼容修订均经用户确认，Design Review PASS）
> Baseline: `master` @ `df5a480d851b8802bdef6e8bf8aaf2d9d09b5736`
> Plan review: PASS（第三次独立复审，P0/P1/P2 均为零）
> Amendment: 2026-08-30 实现审查发现历史履约兼容缺口；修订经独立复审 PASS，并由用户回复“继续”确认后实施。

## Execution gates

- Design 已完成三轮独立审查，最终 `PASS`（P0/P1/P2 均为零），并于 2026-08-30 经用户确认。
- 本 `plan.md` 未经用户确认前，不修改后端、SQL、测试或其他产品代码。
- 实现阶段使用 `test-driven-development`：每个行为先运行能够证明当前缺口的 RED，再做最小实现并运行 GREEN。
- 真实库存正确性不能由 mock 单测单独证明；必须使用用户已运行的本地 MySQL 8。测试通过 `-Db2.mysql.config=D:\market-handsome\Final-StandMarket\backend\fashion-server\src\main\resources\application-dev.yml` 只读主工作树现有 ignored 配置，不复制到 B2 worktree、不输出 host/user/password/URL 的凭据内容，也不再重复请求用户开启 MySQL。
- MySQL 集成测试创建 `fsm_b2_it_<32位小写十六进制UUID>` 临时 schema；创建和删除前都校验 `^fsm_b2_it_[0-9a-f]{32}$`，并在 `finally`/`AfterAll` 中只按已保存的精确名称删除自身 schema，禁止通配符或删除其他库。
- B2 plan 确认只授权本地实现与验证；commit、push、PR、merge 仍需另行授权。即使后续经授权合并，在 B3 封死旧退款审批直接回补前仍不可部署或声明“可上线”。
- commit、push、PR、merge、生产迁移或远程设置变更均不在本计划授权内，需用户后续明确授权。

## Current inventory

| Area | Current behavior on `master` | Required closure |
|---|---|---|
| 创建订单 | 只读取 `product.stock` 判断，未扣库存 | 按商品聚合并升序执行 `stock >= quantity` 条件扣减，任一失败整单回滚 |
| 计价边界 | 普通订单会采用客户端 `activityId/couponId` | 只采用服务端商品价格和合法通用券，普通订单明确写空秒杀字段 |
| 订单插入 | 漏写 `original_price`、秒杀标记/ID 等实体字段 | 补齐插入列并写 `stock_deducted=1` |
| 取消/超时 | 仅状态 CAS 与释放券，不回补库存；超时仅扫描有券订单 | 订单行锁 + 待支付 CAS + 一次回补；所有普通待支付订单逐笔独立事务处理 |
| 支付 | 回调已有 B1 订单锁/CAS，但发起和首次成功回调均不校验库存事实 | 发起、活动流水复用和首次成功回调要求 `stock_deducted=1`；已经成功的精确重复通知按相同 `trade_no` 零写幂等返回，冲突拒绝 |
| 发货/收货 | 发货读后通用更新；确认收货无合法前置状态 CAS | 使用专用 Mapper CAS，限定已支付、已扣库存和合法前态 |
| 优惠券 | 绑定、核销、释放零行会静默提交 | 有券订单必须恰好影响一行，否则整个事务回滚 |
| 鉴权 | 列表、取消、确认收货会回退用户 `1` | 无登录上下文立即拒绝 |
| 数据库 | 无库存标识；超时缺复合索引；基线缺 `user_coupon_id` | 幂等 DDL + 定义校验 + CHECK + 超时索引；历史订单保持 `stock_deducted=0` |
| 退款残余风险 | 旧审批直接回补且非 CAS | B2 不越界实现 B3；evidence 持续记录“B3 前不可部署”门禁 |

主工作树现有 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md` 三项未提交用户修改，以及 `stage-b-ac6-gate` worktree 均为范围外状态；本 workpack 不 reset、不丢弃、不 stash、不暂存、不提交。

## Scope

### In scope

- 普通订单的服务端计价、确定性条件扣库存、订单/明细/通用券同事务提交和失败回滚。
- `stock_deducted` 库存事实标识、历史订单兼容规则和订单插入字段补齐。
- 用户取消、超时取消的订单行锁、CAS、一次库存回补、严格券释放与逐单事务边界。
- 支付发起/活动流水复用/回调、发货、确认收货的合法前置状态约束。
- 移除订单服务未登录回退用户 `1`。
- MySQL 8 幂等 DDL、索引审查、真实并发/回滚/竞态测试及证据。

### Out of scope

- B3 退款状态重构或可信退款完成回补；只保留部署阻断门禁。
- B5 秒杀 Redis/MQ/MySQL 库存闭环。
- B10 Flyway 基线、生产 DDL 执行和数据库发布。
- 地址归属、SKU 独立库存模型、购物车产品交互重构。
- 前端功能扩展、B0-AC6 外部凭据处置、B11 阶段交付。
- 纯 B1 回滚方案；Design 已明确纯 B1 不是有效回滚目标。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B2-AC1 | 并发普通下单不超卖 | MySQL 8 多线程争抢有限库存；成功订单/明细数量与库存变化一致，最终库存非负 |
| B2-AC2 | 任一创建步骤失败不留半订单、半扣库存或永久锁券 | 在第二商品扣减、锁券、订单插入、券绑定、明细插入注入失败；真实事务验证库存、订单、明细、券全部回滚 |
| B2-AC3 | 普通订单不信任客户端金额或秒杀 ID | Service 测试伪造 `amount/activityId/couponId`；落库金额来自服务端，秒杀字段为 `NULL/0`，`original_price` 完整 |
| B2-AC4 | 只有成功扣减过库存的订单可回补，重复取消只成功一次 | MySQL 8 并发双取消验证仅一个 CAS 成功、库存恢复一次、券释放一次；历史 `stock_deducted=0` 待支付订单可取消但商品库存保持不变、合法锁券按契约释放，支付发起/活动流水复用仍被拒绝 |
| B2-AC5 | 支付/取消竞态最多一个合法迁移 | MySQL 8 并发回调与取消，统一订单锁顺序，无死锁且只存在已支付或已取消一个终态 |
| B2-AC6 | 支付、发货、确认收货只接受合法前置状态 | Mapper/Service 测试覆盖支付发起/复用/首次成功回调的库存前置条件，以及 `2→3`、本人 `3→4` 专用 CAS；历史已支付且支付记录成功、`stock=0` 的相同 `trade_no` 重复通知零写幂等成功，不同 `trade_no` 拒绝，尚未支付 `stock=0` 的首次成功拒绝；其他非法状态零写 |
| B2-AC7 | 未登录不会读取或修改用户 `1` | 列表、取消、确认收货测试验证无上下文立即拒绝且 Mapper 零调用 |
| B2-AC8 | 有券和无券超时普通订单都可逐笔取消且失败隔离 | `user_coupon_id IS NULL` 无券订单必须被选中并取消；有券/无券混合批次使用 keyset 限批，中间订单注入失败时前后订单独立提交、失败订单留待重试 |
| B2-AC9 | 有券订单的绑定、核销、释放不能静默失败 | 各操作零影响行数时订单/支付/库存/券事务整体回滚；无券订单允许跳过 |
| B2-AC10 | 增量 SQL 对干净、部分、重复 schema 行为可判定 | MySQL 8 验证列/索引/CHECK 定义、重复执行 no-op、部分或错误定义显式失败；`EXPLAIN` 使用超时复合索引 |

实现审查后的兼容补充：历史已支付订单的相同 `trade_no` 重复回调必须保留 B1 幂等成功，冲突 `trade_no` 仍拒绝；增量迁移必须拒绝任何 `status IN (2,3) AND stock_deducted=0` 的履约中历史订单，发布前在旧版本完成其履约，不得盲目回填库存事实。

## Slices

### Slice 1 — 普通订单条件扣库存与事务创建

1. RED：新增 Service/Mapper 测试证明当前普通订单不扣库存、采用客户端秒杀 ID、漏写字段、重复购物车 ID/异常数量未被拒绝。
2. RED：新增真实 MySQL 8 并发测试，证明当前读库存检查不能阻止超卖；新增失败注入测试证明事务验收尚未满足。
3. GREEN：批量校验当前用户购物车项，拒绝重复 ID 和非法数量；按商品 ID 聚合、升序条件扣减。
4. GREEN：严格锁券并服务端计价，插入 `stock_deducted=1` 的非秒杀订单、绑定券和明细；所有影响行数受检。
5. REFACTOR：提取金额/明细/商品数量快照对象，保持事务编排可读且不引入跨事务补偿。
6. 运行 Slice 1 聚焦测试和真实 MySQL 并发/回滚用例，记录 RED/GREEN 命令与结果。

### Slice 2 — 取消、支付与订单合法状态机

1. RED：覆盖未登录回退、重复取消、回调/取消竞态、历史 `stock_deducted=0` 订单取消不回补且拒绝支付、非法发货/确认、优惠券零行更新；支付回调直接覆盖三例：历史已支付/支付记录成功/`stock=0`/相同 `trade_no` 应零写幂等成功，相同状态不同 `trade_no` 应拒绝，尚未支付 `stock=0` 的首次成功应拒绝；同时先证明当前 `user_coupon_id IS NULL` 超时订单不会被选中、整批事务无法隔离中间失败、keyset/限批契约尚未满足，并记录这些可观察 RED。
2. GREEN：新增独立 `OrderCancellationService` Bean；用户和超时取消通过代理进入逐单事务，锁订单后 CAS，赢家按明细聚合升序回补并严格释放券。
3. GREEN：支付发起/复用和首次成功回调统一要求 `stock_deducted=1`；已成功支付的精确重复通知先按订单/支付事实和相同 `trade_no` 零写幂等返回，冲突通知拒绝；发货、确认收货改专用 CAS；管理端通用状态入口拒绝绕过专用流程。
4. GREEN：超时查询覆盖所有普通待支付订单，包括 `user_coupon_id IS NULL`；有券/无券混合批次按 ID keyset 限批，单笔失败不影响同批其他订单。
5. 运行 Slice 2 Service/Mapper 测试及真实 MySQL 双取消、支付/取消竞态、批次失败隔离测试。

### Slice 3 — 迁移、回归与交付证据

1. RED：为缺失、正确、部分和错误列/索引/CHECK schema 编写 SQL 合约测试，证明当前基线没有库存标识和目标超时索引。
2. GREEN：新增 B2 已有库幂等 SQL并同步 `mysql/final07.sql`；同名定义不一致显式失败，不自动猜测或修复历史业务数据。
3. 在隔离临时 schema 执行脚本首次/重跑/错误定义/历史履约阻断测试和实际 Mapper 超时查询 `EXPLAIN`（不使用 `FORCE INDEX`）；测试结束删除临时 schema，不输出或提交本地配置。
4. 先预检外部配置存在且受主仓库 `.gitignore` 保护，再运行后端聚焦测试、显式 MySQL 8 门禁、`backend/mvn test`、限定范围 diff、空白与敏感信息检查；测试和证据只记录配置路径与预检结论，不记录配置值。
5. 使用真实 Spring 事务代理、实际 MyBatis Mapper 和本地 MySQL 8 验证创建故障点整体回滚、历史库存零标识、优惠券零行回滚，以及有券/无券超时混合批次的 `REQUIRES_NEW` 独立提交与重试；手写 JDBC 仅保留为数据库原语补充，不能代替这些验收。
6. 完成独立实现 Review；修正所有 P0/P1 后补齐 `review.md`、`evidence.md`。B3 未完成时交付状态只能写“本地已验证/不可部署”。

## File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/service/OrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/CouponService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/PaymentServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/CouponServiceImpl.java`
- 新建 `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderCancellationService.java`（名称可在实现时按项目接口/实现规范拆分，但必须保持独立代理边界）
- `backend/fashion-server/src/main/java/com/fashion/task/OrderTimeoutTask.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/OrderMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/OrderDetailMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/ProductMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/ShoppingCartMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/UserCouponMapper.java`
- 对应 `backend/fashion-server/src/main/resources/mapper/*.xml`
- `backend/fashion-pojo/src/main/java/com/fashion/dto/OrderCreateDTO.java`
- `backend/fashion-pojo/src/main/java/com/fashion/entity/Orders.java`
- `mysql/final07.sql` 与新增 B2 已有库幂等 SQL

### Expected tests

- 普通订单创建/信任边界/鉴权 Service 单测。
- 取消、支付、发货、确认收货和通用券影响行数合约测试。
- B2 SQL 静态合约测试。
- 显式属性启用的 `OrderInventoryMysqlIntegrationTest`（或等价命名），使用本地 MySQL 8 隔离临时 schema。

实际文件若超出上述范围，先判断是否为满足 B2 AC 的必要改动；涉及退款完成、秒杀库存、SKU 库存、公开 API 或新的迁移选择时停止并更新 Design，不能顺带实现。

## Branch and dirty-worktree handling

- B2 在 `D:\market-handsome\Final-StandMarket-worktrees\b2-order-inventory-state` 独立 worktree、`codex/b2-order-inventory-state` 分支开发，基线为远程 `master` 已核对提交 `df5a480`。
- 主工作树保持在现有 B1 分支，三项用户未提交工作流修改不移动、不暂存、不提交。
- `stage-b-ac6-gate` worktree 保留原状；B2 只同步 Issue #8 明确要求进入 `master` 的阶段门禁事实，不吸收其 B0 workpack 未提交内容。
- 本 workpack 只暂存 B2 Design、阶段事实源、B2 workpack、产品代码、测试和 SQL；任何本地敏感配置均不得进入 Git。

## Risks and rollback

- **并发超卖/死锁**：数据库条件更新是正确性边界，商品 ID 升序降低死锁；任何死锁使整单回滚，不做局部重试。
- **库存标识与实物不一致**：标识、订单 CAS、回补和券释放必须同事务；任一影响行数异常即回滚。
- **历史支付流水**：`stock_deducted=0` 历史订单禁止新建/复用支付；部署前仍需停流、盘点、关闭/等待终态和人工对账。B2 本地测试不执行外部网关操作。
- **退款路径冲突**：旧 `RefundServiceImpl#approve` 会直接回补库存；B3 修复前 B2 不可部署，不以 B2 workpack 越界修改退款状态机。
- **回滚不安全**：纯 B1 不是回滚目标；回滚制品必须保留 B2 库存/支付/取消语义和 B3 审核回补封堵。
- **迁移冲突**：部分或错误 schema 必须失败并提示人工核对；测试不自动删除业务数据，不执行生产迁移。
- **本地配置泄漏**：只读取项目现有忽略配置运行测试；日志和 evidence 不记录数据源密码、连接串中的凭据或其他 secret。

## Verification commands

实现阶段会先按新增测试类运行精确 RED/GREEN；最终命令至少包括：

```powershell
$B2_MYSQL_CONFIG = 'D:\market-handsome\Final-StandMarket\backend\fashion-server\src\main\resources\application-dev.yml'
if (-not (Test-Path -LiteralPath $B2_MYSQL_CONFIG)) { throw 'B2 MySQL config is missing' }
git -C 'D:\market-handsome\Final-StandMarket' check-ignore --quiet -- 'backend/fashion-server/src/main/resources/application-dev.yml'
if ($LASTEXITCODE -ne 0) { throw 'B2 MySQL config is not ignored by Git' }

Set-Location backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=OrderCreationInventoryTest,OrderCancellationStateTest,OrderAuthorizationTest,OrderTimeoutIsolationTest,OrderFulfillmentStateTest,PaymentServiceImplCreationTest,OrderServiceImplPaymentTest,CouponStrictMutationTest,ProductInventoryMapperContractTest,OrderInventoryPersistenceContractTest,OrderTimeoutMapperContractTest,OrderStateTransitionMapperContractTest,OrderCancellationMapperContractTest,ShoppingCartOrderSelectionContractTest,OrderInventoryMigrationSqlTest' test
mvn -pl fashion-server '-Db2.mysql.integration=true' "-Db2.mysql.config=$B2_MYSQL_CONFIG" '-Dtest=OrderInventoryMysqlIntegrationTest,OrderInventorySpringMysqlIntegrationTest' test
mvn test

Set-Location ..
git diff --check
git diff --stat
git diff --name-only
rg -n "BaseContext.getUserId\(\).*1L|applyDiscount\(|stock_deducted|selectTimeoutCouponOrders" backend mysql
```

真实 MySQL 8 门禁必须覆盖：有限库存多线程争抢；多商品中途失败；锁券/订单/券绑定/明细失败整体回滚；并发双取消一次回补；历史 `stock_deducted=0` 取消不回补且拒绝支付；支付/取消单赢家无死锁；有券/无券混合超时逐单失败隔离；有券零行更新整体回滚；DDL 干净/重跑/部分/错误 schema；目标索引 `EXPLAIN`。具体命令、测试数、schema 创建/删除结果和未决风险只在实际运行后写入 `evidence.md`，不可预填通过。
