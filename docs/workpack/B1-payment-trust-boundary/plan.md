# B1-payment-trust-boundary · Workpack plan

> Status: 本地已验证（待用户决定远程交付）
> Requirement source: [阶段 B：B1 支付入口、通知校验与幂等](../../plans/阶段B-P0P1交易链路修复.md#b1支付入口通知校验与幂等p0)
> Tracking: [GitHub Issue #6](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/6)
> Design: [B1 支付可信边界与并发幂等](../../design/payment/B1-payment-trust-boundary-design.md)（已确认，Design Review PASS）

## Execution gates

- Issue #6 已说明 B0-AC6 不再阻塞 B1-B10 本地开发；项目维护者于 2026-08-28 再次明确确认阻塞解除并要求开始开发。
- 本计划与 Design 已于 2026-08-28 经用户确认。
- 实现阶段遵循 `test-driven-development`，每个行为先得到有效失败测试。
- 现有 B1 未提交产品修改必须原样保留，不 reset、不丢弃、不擅自 stash、不静默吸收范围外改动。

## Current inventory

现有 B1 未提交修改共 10 个产品文件，`103 insertions(+), 59 deletions(-)`：

| Area | Existing local change | Remaining gap |
|---|---|---|
| 普通订单状态查询 | 校验当前用户，按 `order_type=0` 查询 | 缺测试；创建支付仍存在 Controller 先查后的竞态 |
| 同步回跳 | 已改为只读，不再直接更新订单 | 缺只读/归属/类型测试，前端仍传入兼容字段 |
| 异步通知 | 已增加普通订单类型检查，保留金额和 `app_id` 校验 | 仍可能记录完整参数；缺必填字段、订单状态、事务与重复通知测试 |
| 支付/订单更新 | 已增加支付和订单 CAS 雏形 | 锁顺序和回调事务前置状态需收口，缺并发测试 |
| 支付创建 | 已实现“先查再插”复用 | 无数据库活动流水唯一约束，并发仍可双插入 |
| 管理端/取消旁路 | 已新增 `cancelPending` Mapper 但尚未使用 | 通用管理状态接口仍可写支付字段；用户/超时取消仍可用旧快照覆盖支付结果 |

本地还存在 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md` 的范围外修改；本 workpack 不修改、不提交这些文件。

## Scope

### In scope

- 收口普通订单支付宝发起、本人/类型状态查询和同步回跳只读契约。
- 完整校验支付宝异步通知，并以同一事务内的支付/订单 CAS 保证一次迁移。
- 通过订单行锁和 MySQL 唯一约束实现支付创建并发幂等。
- 移除普通订单与用户秒杀随机模拟支付入口、管理端普通订单确认支付入口及对应前端调用。
- 封死管理端通用状态接口的支付字段/`status=2` 旁路；用户取消和超时取消使用待支付 CAS，避免覆盖有效支付回调。
- 未接入真实网关的前端支付方式不再展示虚假成功。
- 配置 HTTP 连接与读取超时。
- 新增聚焦自动化测试、MySQL 8 约束验证和前端生产构建证据。

### Out of scope

- B0 外部凭据轮换/证明与 Issue #4 关闭动作。
- 管理端秒杀确认、秒杀支付/取消/库存状态机（B5）。
- 新增微信支付、人工补单、支付宝主动查询/对账或退款。
- Flyway 基线与生产数据库执行（B10）。
- B2-B11 其他需求、范围外清理和工作流文件修改。
- B2 的库存回补和完整取消/确认收货/发货状态机；B1 只补与支付回调直接竞争的取消 CAS，不宣称全订单状态机闭合。
- commit、push、PR、merge；除非用户后续逐项明确授权。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B1-AC1 | 无有效异步通知不能更新支付记录或普通订单 | 通知测试覆盖验签、字段、`app_id`、金额、成功状态、类型、关联和订单状态；验证零迁移；管理端通用状态接口不能写支付字段/设置 `status=2` 或推进未支付订单 |
| B1-AC2 | 重复或并发有效通知只迁移一次 | MySQL 8/Testcontainers 双回调验证支付、订单和券核销各一次；已发货/已完成/退款后的同交易号一致重复 `success` 且零写，不一致终态 `failure` |
| B1-AC3 | 并发创建不会产生多个活动支付流水 | MySQL 8/Testcontainers 覆盖并发 service 创建、真实唯一索引、目标冲突后锁定 current read、历史冲突迁移重试；仅 mock 不足以标记通过 |
| B1-AC4 | 所有 B1 随机模拟支付 URL 不再可用 | Web 层映射测试 + `rg` 证明普通/用户秒杀随机 service 与路由移除；管理端普通确认路由移除，通用状态路由无支付旁路 |
| B1-AC5 | 状态查询只允许本人普通订单和正确 `order_type` | `PaymentControllerTest` 覆盖未登录、他人订单、秒杀类型和正常查询 |
| B1-AC6 | 同步回跳只展示状态，不修改订单 | Controller 测试校验验签、归属并验证无状态更新调用；前端只轮询只读状态 |
| B1-AC7 | 异步通知校验后才允许普通订单合法 CAS | Testcontainers 覆盖 `payment 0/1→2`、`orders (1,0)→(2,1)`、回调/取消竞争无死锁、后续失败整体回滚；精确响应矩阵单测 |
| B1-AC8 | HTTP 客户端配置有限连接/读取超时 | `RestConfigTest` 检查默认连接 3000 ms、读取 30000 ms，并验证外部配置可覆盖 |

## Slices

### Slice 1 — 移除不可信入口并收口只读查询

1. 先写 Web/Controller 失败测试，证明模拟和管理端普通确认路由当前仍存在、通用管理状态接口仍可写支付字段，以及越权/类型错误仍需保护。
2. 删除普通订单与用户秒杀随机模拟 Controller/Service/API 调用；删除管理端普通确认 Controller/API/按钮和无人使用的支付直写 service；通用管理状态接口改专用 DTO，拒绝 `status=2` 且不得推进未支付订单。
3. 收口普通订单支付宝发起、状态查询、同步回跳归属/类型检查；前端禁用未接真实网关的支付方式。
4. 运行聚焦 Controller 测试和用户端/管理端构建。

### Slice 2 — 异步通知与事务性一次迁移

1. 先写通知响应矩阵、一致/不一致重复、非法订单前置状态、回调/取消竞争和事务回滚失败测试；一致重复覆盖订单已发货、已完成、退款中和真实退款完成。
2. 规范化通知必填字段与安全日志；完整校验签名、`app_id`、金额、`TRADE_SUCCESS/TRADE_FINISHED`、记录类型和订单关联；其他已签名状态确认收到但零迁移。
3. 统一订单锁顺序，以支付 CAS、订单 CAS 和券核销完成同一事务的一次性迁移；用户/超时取消改用待支付 CAS，只有赢家释放优惠券。
4. 运行通知单测与 MySQL 8/Testcontainers 双回调、回调/取消、回滚测试。

### Slice 3 — 支付创建数据库幂等与超时

1. 先写并发创建、金额不一致、目标唯一冲突一次锁定 current read、DDL 重跑和超时配置失败测试。
2. 在订单行锁事务内创建/复用支付记录，新增 MySQL 8 活动流水生成列唯一索引及可检测未应用/已应用/部分应用状态的已有库增量 SQL；部分 schema 必须显式失败。
3. 配置 HTTP 连接/读取超时；不扩展到 B9 Agent 专用契约。
4. 运行 Payment Service/Mapper/MySQL 8 聚焦验证、后端全量测试和两端前端构建。

## File-level change surface

### Preserve and complete existing B1 changes

- `backend/fashion-server/src/main/java/com/fashion/controller/admin/OrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/notify/PayNotifyController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/PaymentController.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/OrderMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/PaymentMapper.java`
- `backend/fashion-server/src/main/java/com/fashion/service/PaymentService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/PaymentServiceImpl.java`
- `backend/fashion-server/src/main/resources/mapper/OrderMapper.xml`
- `backend/fashion-server/src/main/resources/mapper/PaymentMapper.xml`

### Expected additional product/test files

- 普通/秒杀模拟入口相关 Controller、Service 接口与实现；管理端订单状态专用 DTO/Service。
- `backend/fashion-server/src/main/java/com/fashion/config/RestConfig.java` 及必要非敏感配置。
- `mysql/payment_table.sql` 和新增 B1 已有库增量 SQL。
- 用户端 `Order.vue`、`CreateOrder.vue`、`PayResult.vue`、`api/payment.js`、`api/product.js`、`api/seckill.js`、`SeckillOrder.vue`。
- 管理端 `api/order.js`、`views/OrderList.vue`。
- 新增 `PaymentControllerTest`、`PayNotifyControllerTest`、`PaymentServiceImplTest`、回调事务测试、`RestConfigTest` 和 MySQL 8 约束验证。

实际文件若超出上述范围，先判断是否为满足 B1 AC 的必要改动；涉及新的支付契约、状态机或迁移选择时停止并更新 Design，不能顺带实现。

## Branch and dirty-worktree handling

- 当前 dirty checkout 保留在原位置，不为了规避 B1 存量修改而强制新建 worktree。
- 已在不改动存量文件内容的前提下从当前 checkout 创建专用分支 `codex/b1-payment-trust-boundary`，继续保留现有 B1 修改。
- 当前本地基线落后远端 B0。未经单独授权不 stash、不临时提交；进入远程交付前必须安全合入远端 `master`，显式解决 `PayNotifyController` 与 B0 安全日志的重叠，并重新执行 Review 和全部验证。
- 仅暂存 workpack 所有文件；工作流文件等范围外修改不得进入 B1 提交。

## Risks and rollback

- **支付误迁移**：签名之外仍需校验应用、金额、成功状态、类型、关联和订单前置状态；任何失败零写入；一致重复还需核对 `trade_no`。
- **并发双流水**：订单行锁负责正常路径串行化，MySQL 唯一索引负责数据库兜底；真实 MySQL 8 验证未通过则 AC3 阻塞。
- **重复核销**：只有首个支付/订单 CAS 事务执行券核销；整个事务失败一并回滚。
- **取消覆盖**：用户取消和超时取消只有待支付 CAS 赢家才释放优惠券；Testcontainers 并发测试限定完成时间并验证无死锁。B2 仍负责库存与完整状态机。
- **迁移冲突**：先列出重复活动流水并停线，不自动删除生产数据；B1 不执行生产迁移。
- **B0 回归**：远端 B0 的支付回调脱敏日志必须在最终集成 diff 中保留。
- **前端能力收缩**：只移除虚假成功路径，不宣称已接入微信或秒杀真实支付。
- 回滚优先回滚应用路由和服务实现，保留加法唯一约束；结构回滚需单独停写和数据核对。

## Verification commands

计划在实现期间根据新增测试类运行精确的 Maven `-Dtest=... test` RED/GREEN 循环；交付前至少执行：

```powershell
Set-Location backend
mvn test

Set-Location ../frontend/fashion-client
npm run build

Set-Location ../fashion-admin
npm run build

Set-Location ../..
git diff --check
git diff --stat
rg -n "processPayment\(|/user/order/pay|/user/seckill/order/pay|/admin/order/.*/confirm-payment" backend frontend
```

MySQL 8 验证必须使用真实 MySQL 8 实例或 Testcontainers，并覆盖：并发 service 创建复用一个活动流水；直接并发插入只有一条成功；目标唯一冲突后通过一次锁定 current read 看见赢家；双回调只迁移/核销一次；回调与取消只有一个赢家且无死锁；后续失败使 payment/order 一起回滚；DDL 遇历史冲突失败、清理后可重试、成功后重跑 no-op，并拒绝只有部分生成列/缺少索引的 schema。具体命令在执行环境确认后写入 `evidence.md`，不可预填通过。
