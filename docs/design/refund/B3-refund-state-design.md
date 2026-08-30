# B3 退款真实状态与审核边界 · Design

> Status: 已确认（2026-08-30；独立审查 PASS）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B3 / GitHub Issue #10
> Baseline: `master` @ `26394d64c7ce11dcee31a9154bd6ee939d8ad61e`
> Updated: 2026-08-30

## 1. Goal and scope

### In scope

- 将退款审核与真实外部退款完成拆成两个状态边界。
- 审核同意仅通过数据库 CAS 完成 `0 → 1`，不修改订单、支付、库存或退款完成时间。
- 审核拒绝通过数据库 CAS 完成 `0 → 3`，并在同一 MySQL 事务内把订单从退款中状态恢复到申请前状态。
- 退款申请使用订单 CAS 保存可靠的申请前状态，避免申请与确认收货并发时记录错误状态。
- 前后端统一展示 `1 已同意，等待退款处理`，不把审核同意描述为退款成功。
- 提供干净建表与已有库增量脚本，显式阻断旧 `status IN (1,2)`、不可恢复待审核记录和已拒绝但订单仍滞留退款中的历史异常。
- 使用真实 Spring 事务代理、生产 MyBatis Mapper 和 MySQL 8 验证 CAS、并发单赢家和拒绝回滚。

### Out of scope

- 调用支付宝或其他支付渠道的真实退款 API。
- 接收可信退款完成通知，以及执行 `1 → 2`。
- 退款完成后的支付状态更新、`stock_deducted` 迁移和一次性库存回补。
- 部分退款、多次退款、退款重试/对账任务和人工补单。
- B4-B11、B0-AC6 外部凭据、生产迁移和生产 CD。

## 2. Current behavior and constraints

- `RefundServiceImpl#approve` 当前先查后写 `status=2`，填写 `refund_time`，并按订单明细调用 `ProductMapper#restoreStock`；这会把审核同意伪装为真实退款完成，并与 B2 的库存事实冲突。
- `approve` / `reject` 都依赖“先查状态，再通用 update”，并发审核可能都通过前置检查，不能证明最多一次成功。
- `reject` 先更新退款，再通过订单通用 update 恢复状态；订单恢复失败不会因影响行数为零而失败，可能留下退款已拒绝但订单仍为退款中。
- `apply` 先读订单再通用 update 为 `status=6`，与确认收货等并发时可能覆盖新状态，导致 `order_status` 不再代表真实申请前状态。
- 管理端控制器和两端退款列表把审核通过返回或展示为“退款成功/已退款”。
- `refund.status` 当前注释只有 `0/2/3`；已有 `2` 由旧代码写入，但系统未调用真实退款网关，因此不能自动认定外部退款已完成，也不能安全地盲改成 `1`。
- `refund.order_id` 已有唯一索引，当前模型是一张订单最多一条退款记录；B3 不改变该产品约束。
- B2 已引入 `orders.stock_deducted`，但 B3 只封死审核阶段回补，不实现未来真实退款完成时的库存 CAS。

## 3. Design decisions

### 3.1 状态常量与单一写入边界

退款状态固定为：

| 状态 | 含义 | B3 是否可写 | 写入入口 |
|---|---|---|---|
| `0` | 待审核 | 是 | 新建退款申请 |
| `1` | 已同意/待外部退款 | 是 | 管理端审核同意 CAS |
| `2` | 退款完成 | 否 | 仅未来可信退款完成通知 |
| `3` | 已拒绝 | 是 | 管理端审核拒绝 CAS |

- 在 `Refund` 中定义有语义的状态常量，业务代码不散落魔法值。
- B3 产品代码中不存在写入 `2` 的服务或 Mapper 方法。
- `refund_time` 只属于未来 `1 → 2` 完成路径；B3 的申请、同意和拒绝均不写该字段。

### 3.2 审核同意

新增 Mapper 原子契约：

```text
approvePending(id, opinion, auditTime, updateTime) -> affected rows
UPDATE refund
SET status = 1, audit_opinion = ?, audit_time = ?, update_time = ?
WHERE id = ? AND status = 0
```

- 影响恰好一行才成功；零行统一表示记录不存在或已经处理，Service 抛出业务异常。
- 不读取订单明细，不调用 `ProductMapper`，不更新订单或支付记录，不写 `refund_time`。
- 重复和并发同意只能有一个请求把 `0` 改为 `1`。

### 3.3 审核拒绝与事务原子性

拒绝流程在一个真实 Spring 事务代理中执行：

1. 读取退款记录，校验 `order_status` 只能是 `3` 或 `4`。
2. 执行退款 CAS `0 → 3`，零行立即失败。
3. 执行订单 CAS：`WHERE id=? AND status=6`，恢复为退款记录保存的 `order_status`；目标状态在 SQL/Service 双重限制为 `3/4`。
4. 订单 CAS 不等于一行时抛出异常，事务回滚步骤 2。

本阶段不把零行处理成幂等成功；验收口径是最多一次成功，重复审核明确返回“已处理或状态已变化”。

### 3.4 退款申请与申请前状态

为保证拒绝时可恢复真实状态，申请流程使用订单 CAS：

```text
markRefunding(orderId, userId, expectedStatus) -> affected rows
UPDATE orders SET status = 6
WHERE id = ? AND user_id = ? AND status = ? AND expectedStatus IN (3,4)
```

- Service 先读取当前用户订单并保存 `expectedStatus`，插入退款记录后执行订单 CAS；CAS 零行使整个申请事务回滚。
- 既有 `refund.order_id` 唯一约束继续作为并发重复申请的数据库边界；唯一冲突使事务整体回滚。
- B3 不改变“仅已发货或已完成订单可申请”的现有产品规则。

### 3.5 文案与响应契约

- 管理端审核同意成功响应：`已同意，等待退款处理`。
- 管理端筛选、状态标签和成功提示对状态 `1` 统一使用精确文案 `已同意，等待退款处理`；`2` 只显示为 `退款完成`，不提供写入操作。
- 用户端状态 `1` 显示 `已同意，等待退款处理`，状态 `2` 显示 `退款完成`。
- 前后端不得使用“退款成功”描述审核同意。

### 3.6 历史数据与迁移

新增 `mysql/add_refund_review_state.sql`，并同步 `mysql/refund_table.sql` 与 `mysql/README.md`：

- 要求目标库存在 `refund` 表及预期 `status` 列；错误或部分定义显式失败。
- 使用一对专用、精确命名且 `ENFORCED` 的约束作为完整 B3 schema marker：`chk_refund_status_b3 CHECK (status IN (0,1,2,3))` 与 `chk_refund_order_status_b3 CHECK (order_status IN (3,4))`。两者均不存在才进入首次迁移分支；只存在一个属于部分迁移，必须显式失败。
- 首次迁移检查任何 `status NOT IN (0,1,2,3)`，存在即失败。
- 首次迁移同时检查任何历史 `status IN (1,2)`，存在即失败并要求人工对账。旧系统既未定义/写入 `1`，也没有可信外部退款完成来源；脚本不得把旧值自动解释为待外部退款或退款完成。
- 首次迁移检查 `status IN (0,1,3) AND refund_time IS NOT NULL`，存在即失败，禁止把完成时间与非完成状态静默混用。
- 首次迁移检查每条待审核退款：`order_status IN (3,4)`、订单存在、`refund.user_id=orders.user_id` 且订单当前 `status=6`；任何不满足项都阻断迁移，避免审核拒绝永久无法恢复。
- 首次迁移检查每条已拒绝退款：`order_status IN (3,4)`、订单存在且用户归属一致；若 `order_status=3`，订单当前只允许 `3` 或后续合法确认后的 `4`，若 `order_status=4`，订单当前只允许 `4`。任何订单仍为 `6`、缺失或处于其他状态的记录都阻断迁移并要求人工对账，禁止脚本自动恢复或覆盖订单事实。
- 首次预检必须覆盖全部可迁移的现存退款，确认 `order_status` 非空且只为 `3/4`；随后把列改为 `TINYINT NOT NULL`，更新状态注释，并创建两个 B3 CHECK。数据库约束从此持续阻止 `NULL/2/5` 等不可恢复的申请前状态。
- 两个约束均已正确存在才表示 B3 迁移完成；重跑允许 B3 后合法产生的状态 `1`，以及未来可信完成设计产生的 `2`，但仍校验 `status`/`order_status` 的类型与空值属性、两个 CHECK 的名称、类型、`ENFORCED` 状态和规范化表达式。
- 同名 CHECK、列定义、单 marker 或 marker 定义不一致时显式失败，不自动删除约束或改写业务记录。
- `refund_table.sql` 用于干净测试库，直接采用四状态注释、`order_status NOT NULL`、`chk_refund_order_status_b3` 与 `chk_refund_status_b3`；MySQL 测试必须比较干净建表与已有库升级后的关键列和约束元数据等价，并证明两者都拒绝非法 `order_status`。`final07.sql` 当前不包含 refund 表，不在 B3 中伪造重复定义。

历史 `status IN (1,2)`、不可恢复待审核记录和异常已拒绝记录的人工处置必须核对支付渠道、退款流水、库存和订单事实，形成独立审计记录；在所有遗留值被处置前 B3 首次迁移保持失败。处置策略属于生产发布/B10-B11，不由 B3 测试脚本代替；若发现确有可信完成事实且无法在现有模型中无损表达，必须修订 Design，而不是绕过门禁。

## 4. Contracts and state transitions

### 4.1 状态迁移表

| 动作 | 前置条件 | 成功后 | 订单 | 支付 | 库存 | `refund_time` |
|---|---|---|---|---|---|---|
| 申请退款 | 当前用户订单 `3/4` | 退款 `0`，订单 `6` | CAS `3/4 → 6` | 不变 | 不变 | 空 |
| 同意审核 | 退款 `0` | 退款 `1` | 不变，保持 `6` | 不变 | 不变 | 空 |
| 拒绝审核 | 退款 `0`，订单 `6` | 退款 `3`，订单恢复 `3/4` | CAS `6 → order_status` | 不变 | 不变 | 空 |
| 完成退款 | 退款 `1` | 退款 `2` | B3 不实现 | B3 不实现 | B3 不实现 | B3 不实现 |

### 4.2 Mapper 影响行数契约

- `RefundMapper.approvePending(...) == 1`：唯一成功同意。
- `RefundMapper.rejectPending(...) == 1`：唯一成功拒绝；后续订单恢复失败时由事务回滚。
- `OrderMapper.markRefunding(...) == 1`：申请时订单仍处于读取到的合法状态。
- `OrderMapper.restoreRejectedRefundOrder(...) == 1`：订单仍为退款中且目标状态合法。
- 所有零行或多行均视为失败，不静默继续。
- 删除 `RefundMapper.update` 及 XML 动态 `status/refund_time` 通用写入口；阶段 B 只保留固定目标状态的专用 SQL，避免运行时参数绕过源码字面扫描。

## 5. File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/service/RefundService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/RefundServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/RefundMapper.java`
- `backend/fashion-server/src/main/resources/mapper/RefundMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/mapper/OrderMapper.java`
- `backend/fashion-server/src/main/resources/mapper/OrderMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/controller/admin/RefundController.java`
- `backend/fashion-pojo/src/main/java/com/fashion/entity/Refund.java`
- `frontend/fashion-admin/src/views/RefundList.vue`
- `frontend/fashion-client/src/views/RefundList.vue`
- `mysql/refund_table.sql`
- 新增 `mysql/add_refund_review_state.sql`
- `mysql/README.md`

`PaymentServiceImpl`、`OrderServiceImpl`、`ProductMapper`、用户端 `RefundController`、`Orders` 与 `Payment` 只做范围审计；没有满足 B3 AC 的必要变化时不修改。

### Expected tests

- 退款申请、同意、拒绝 Service 单元测试。
- 管理端响应文案与退款状态契约测试。
- Refund/Order Mapper SQL 合约测试。
- 退款迁移脚本静态与 MySQL 8 执行测试。
- 显式属性启用的真实 Spring/MyBatis/MySQL 集成测试，覆盖并发审核和拒绝回滚。
- 两端前端生产构建。

## 6. Failure handling, idempotency, and compensation

- 审核没有跨系统副作用；MySQL CAS 是唯一并发正确性边界。
- 同意 CAS 失败时不做补偿，因为没有任何订单、支付或库存写入。
- 拒绝的退款 CAS 与订单恢复在同一事务中；订单恢复失败通过事务回滚退款 CAS，不使用异步补偿。
- 申请的退款插入与订单 CAS 在同一事务中；唯一键冲突或订单 CAS 失败整体回滚。
- 日志只记录退款 ID、退款单号和结果，不记录支付凭据、回调原文或敏感配置。

## 7. Migration, compatibility, and rollback

- B3 代码依赖状态 `1` 语义；数据库脚本必须与 B3 代码同一发布窗口执行，旧前端/旧后端不应继续处理新状态。
- B3 采用非滚动重叠切换：先从网关/入口暂停退款申请和审核，等待在途请求排空并停止全部旧后端实例，核验负载均衡目标、进程/容器清单与旧版本均不可达；之后才执行历史预检和 B3 迁移。
- 迁移后只启动 B3-safe 制品，逐个核验构建提交/制品版本、健康状态和审核契约；确认全部可达实例都不会写 `2` 或调用退款库存回补后，才重新开放退款入口。相关运行清单和版本证据属于 B11 发布证据。
- 首次切换前必须准备退款端点硬隔离能力（同时阻断 `/user/refund/**` 与 `/admin/refund/**`）或一个已验证的 B3-safe 回滚制品。若首个 B3 制品迁移后无法启动或验证失败，退款入口保持关闭；旧制品只有在两类退款路由均持续不可达时才可承载其他流量，schema 不回退。修复并重新验证 B3-safe 制品后才允许恢复退款入口。
- 若退款入口重新开放后出现运行故障，立即再次关闭两类退款路由并排空在途请求，只能切换到已验证、兼容现有 schema 且保留 `0 → 1`/无库存回补边界的 B3-safe 制品；没有可用安全制品时保持退款功能不可用，不能回到 B2 退款实现。
- 发布前盘点历史 `status IN (1,2)`、异常 `refund_time`、不可恢复待审核记录，以及已拒绝但订单未处于合法恢复状态的记录；未完成对账时迁移必须失败。
- 先在隔离 MySQL 8 schema 验证首次执行、重复执行、错误定义、历史状态阻断及处置后重跑，再进入后续 B10/B11 发布流程。
- 紧急代码回滚不得回到会在审核阶段写 `2` 和恢复库存的 B2 退款路径；旧制品若承载非退款流量，必须由网关持续硬隔离全部退款端点。回滚制品必须保留 B3 的审核 CAS、schema 兼容性与“无库存回补”边界。
- 新增 CHECK 和状态注释不在紧急回滚中删除；未来真实退款完成设计可兼容地增加 `1 → 2`，但必须另行设计支付与库存一次性事实。
- B3 合并后，B2/B3 组合才解除“旧审核直接回补”这一部署阻塞；阶段 B 仍受 B0、B10、B11 等发布门禁约束。

## 8. Verification gates

- TDD：先用失败测试证明当前同意会写 `2`/完成时间/库存、并发审核可双成功、拒绝订单恢复失败不回滚。
- 聚焦单元/合约测试：验证所有 CAS 前置状态、影响行数检查、响应文案与状态映射。
- 真实 MySQL 8：并发同意/同意与拒绝竞态恰好一个成功；同意后订单支付状态、`stock_deducted`、商品库存和 `refund_time` 不变。
- 真实 Spring/MyBatis/MySQL：拒绝时订单恢复 CAS 注入零行，退款状态回滚为 `0`；正常拒绝同时提交退款 `3` 与订单原状态。
- SQL：干净 schema、首次执行/双 marker 重跑、单 marker/错误列/CHECK/非 ENFORCED marker、历史 `1/2`、异常 `refund_time`、不可恢复待审核记录、异常已拒绝记录和处置后重跑；比较 clean/upgrade 关键元数据等价，并验证迁移后 `order_status NULL/2/5` 均被拒绝。
- 切换门禁：以部署清单证明入口停写、旧实例全部停止、仅 B3-safe 制品可达后才重新开放；不得用“已部署一个新实例”代替全量核验。
- 后端执行聚焦测试和完整 `mvn test`；管理端与用户端分别执行 `npm run build`。
- `git diff --check`、限定范围 diff 和敏感信息扫描。
- 实现前 Design 与 workpack plan 均需独立审查 PASS，并由用户确认。

## 9. Decisions requiring user confirmation

1. 审核同意严格只做退款 `0 → 1` CAS，订单保持退款中，支付、库存和完成时间全部不动。
2. 审核拒绝的退款 CAS 与订单 `6 → 申请前状态` CAS 使用同一 Spring/MySQL 事务，任何零行整体回滚。
3. 退款申请增加订单状态 CAS，以保证 `order_status` 可被拒绝流程可靠恢复；不改变仅 `3/4` 可申请的产品规则。
4. 首次迁移发现任何历史 `status IN (1,2)`、非法/缺失 `order_status`、不可恢复待审核记录或“已拒绝但订单未合法恢复”的记录时显式失败，必须人工对账；迁移把 `order_status` 收敛为 `NOT NULL + CHECK(3,4)`，双 B3 CHECK marker 使迁移后合法 `1/2` 的重跑不被误判。
5. B3 不实现真实退款完成、支付状态更新或库存回补；未来可信通知另行设计 `1 → 2` 与一次性库存 CAS。
6. 紧急回滚不得恢复旧退款路径；首次切换失败时退款入口保持关闭，旧制品仅可在 user/admin 退款路由全部硬隔离时承载其他流量，schema 不回退。
7. 部署必须停止并排空全部旧后端后再迁移，只有全部可达实例均核验为 B3-safe 才能重新开放退款入口；开放后故障也必须先关入口、排空，再切换到已验证的 B3-safe 制品。

## 10. Independent review

- Verdict: PASS（第五轮独立审查，P0/P1/P2/P3 均为 0；用户已确认）
- Round 1 findings and resolutions:
  - P1：旧库 `status=1` 同样无可信语义；已改为首次迁移阻断 `1/2`，并以精确 B3 CHECK marker 区分合法重跑。
  - P1：旧后端可能在迁移后继续写 `2`/回补；已改为入口停写、排空并停止全部旧实例、迁移、全量 B3 制品核验后再开放的非重叠切换。
  - P2：待审核遗留记录可能无法拒绝恢复；已补订单状态、申请前状态、用户归属和订单存在性预检。
  - P2：管理端文案不精确；已统一为 `已同意，等待退款处理`。
  - P2：源码扫描无法封死动态通用更新；已要求删除通用退款 update，增加固定 CAS 静态契约和真实代理竞态验证。
  - 第二轮 P1：遗漏旧拒绝半完成记录；已增加 `status=3` 与订单恢复事实的首次迁移预检及阻断用例。
  - 第二轮 P3：摘要和 RED 描述仍只提旧 `2`；已同步为旧 `1/2`、待审核和已拒绝历史事实。
  - 第三轮：首轮和第二轮问题全部关闭，未发现新增 P0-P3。
  - 第四轮 P1：增量迁移未把 `order_status` 收敛到与干净库相同的 `NOT NULL + CHECK(3,4)`；已增加双 marker、列和约束强校验、clean/upgrade 元数据等价及非法值拒绝测试。
  - 第四轮 P2：首个 B3-safe 制品失败时缺少可执行回滚路径；已增加首次切换失败与重新开放后故障两类退款路由隔离/安全制品 runbook，并明确 schema 不回退。
  - 第五轮：第四轮 P1/P2 均完全关闭，未发现新增 P0-P3。
