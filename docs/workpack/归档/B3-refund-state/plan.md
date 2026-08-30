# B3-refund-state · Workpack plan

> Status: 已归档（2026-08-30；本地已验证，独立实现审查 PASS；未执行远程交付）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B3 / GitHub Issue #10
> Design: `docs/design/refund/B3-refund-state-design.md`（已确认，独立审查 PASS）
> Baseline: `master` @ `26394d64c7ce11dcee31a9154bd6ee939d8ad61e`
> Plan review: PASS（第五轮独立审查，P0/P1/P2/P3 均为 0；用户已确认）

## Scope

### In scope

- 用 Mapper CAS 替换退款审核的先查后通用更新。
- 同意只执行 `0 → 1`，移除审核阶段库存回补、完成时间和虚假完成状态。
- 拒绝执行 `0 → 3` 并在同一事务恢复申请前订单状态，失败整体回滚。
- 申请退款增加订单 `3/4 → 6` CAS，保存可靠的申请前状态。
- 更新后端成功文案及管理端/用户端四状态展示。
- 新增可重复执行的 B3 已有库脚本，并更新干净建表脚本和说明。
- 以 TDD、真实 Spring/MyBatis/MySQL、前端构建和独立实现审查完成验收。

### Out of scope

- 真实退款 API、退款完成通知、`1 → 2`、支付状态更新和库存回补。
- 部分/多次退款、退款对账任务与生产历史数据人工处置。
- B4-B11、B0-AC6、生产迁移和生产 CD。
- 与 B3 AC 无关的订单、支付、商品或前端重构。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B3-AC1 | 同意仅把退款 `0 → 1`；订单/支付/商品库存/`stock_deducted`/`refund_time` 不变 | Service/Mapper 测试；真实 Spring/MyBatis/MySQL 状态快照前后对比 |
| B3-AC2 | 重复或并发同意、拒绝竞态最多一个 CAS 成功 | Mapper 合约测试；MySQL 多线程同意与同意/拒绝竞态 |
| B3-AC3 | 拒绝仅 `0 → 3`，订单 `6 → order_status(3/4)`；任一零行整体回滚 | Service 单测；真实代理事务成功/故障注入测试 |
| B3-AC4 | 阶段 B 不存在退款服务写 `2`、`refund_time` 或调用库存回补的路径；不存在动态通用状态更新入口 | 专用 Mapper SQL 静态契约、依赖边界测试、源码限定扫描和审查 |
| B3-AC5 | 申请退款通过订单 CAS 可靠记录申请前状态 | Service/Mapper 测试；申请与确认双方均经真实 Spring 代理、生产 MyBatis XML 和 MySQL 的竞态测试 |
| B3-AC6 | 前后端状态 `1` 显示“已同意，等待退款处理”，审核成功不显示“退款成功” | Controller 合约测试、源码扫描、两端生产构建 |
| B3-AC7 | 首次迁移阻断旧库 `1/2`、非法/缺失 `order_status`、不可恢复待审核记录和“已拒绝但订单未合法恢复”的记录；升级库收敛为与干净库等价的 `order_status NOT NULL + CHECK(3,4)`，双 marker 重跑接受合法新状态 | SQL 静态测试；MySQL 首次/双 marker 重跑、单 marker/错误定义/非 ENFORCED 约束、历史事实阻断、clean/upgrade 元数据等价和非法 `order_status` 拒绝测试 |

发布门禁（不阻塞 B3 本地实现/合并，但阻塞部署）：B11 必须留存入口停写、旧实例清零、B3 制品版本和全量可达实例证据，禁止旧新版本重叠处理退款。

## Slices

### Slice 1 — 审核与申请 CAS

1. RED：新增测试证明当前同意写 `2`、完成时间并恢复库存；重复/并发审核不是单赢家；拒绝订单恢复零行不会回滚；申请可覆盖并发状态。
2. GREEN：新增退款同意/拒绝固定 CAS、订单申请/拒绝恢复 CAS；严格检查影响行数，并删除动态 `RefundMapper.update` 状态/完成时间入口。
3. GREEN：移除 `RefundServiceImpl` 的订单明细与商品库存依赖；同意无订单/支付/库存副作用，拒绝在同一代理事务中原子恢复。
4. REFACTOR：用退款状态常量和明确异常收敛魔法值，保持 API 路径不变。
5. 运行 Slice 1 聚焦测试并记录真实 RED/GREEN 输出。

### Slice 2 — 契约文案与数据库兼容

1. RED：增加 Controller/前端/SQL 合约测试，证明缺少状态 `1`、仍展示“退款成功”，以及旧 `1/2`、不可恢复待审核记录、已拒绝但订单仍为 `6` 等历史异常未被迁移阻断。
2. GREEN：更新管理端成功响应和两端四状态映射，状态 `1` 使用确认文案。
3. GREEN：新增 `add_refund_review_state.sql`，同步 `refund_table.sql` 与 `mysql/README.md`；使用状态/申请前状态双 B3 CHECK marker，把升级库 `order_status` 收敛为 `NOT NULL + CHECK(3,4)`，首次迁移阻断旧 `1/2`、非法申请前状态、异常完成时间、不可恢复待审核事实和非法已拒绝恢复状态。
4. 在隔离 MySQL 8 schema 运行脚本首次/双 marker 重跑、单 marker/错误定义/非 ENFORCED 约束、历史阻断/处置后重跑、clean/upgrade 元数据等价和迁移后非法 `order_status` 拒绝用例；不修改生产数据库。

### Slice 3 — 并发、事务与交付证据

1. 使用真实 MySQL 8 验证并发同意、同意/拒绝竞态最多一个合法迁移；申请与确认收货分别经 `RefundService`、`OrderService` 真实 Spring 代理和生产 MyBatis XML 参与竞态。
2. 使用真实 Spring AOP 代理和生产 MyBatis XML 验证拒绝正常提交及订单 CAS 零行时退款 CAS 回滚。
3. 运行后端聚焦测试、显式 MySQL 门禁、完整 `mvn test` 与两端 `npm run build`。
4. 运行专用 Mapper SQL 静态契约、退款服务依赖边界测试、限定源码扫描、`git diff --check`、敏感信息和范围复核。
5. 完成独立实现 Review；P0/P1 清零后补齐 `review.md` 和 `evidence.md`。

## File-level change surface

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
- `mysql/add_refund_review_state.sql`（新增）
- `mysql/README.md`

### Expected tests

- `RefundServiceImplTest`：申请/同意/拒绝和零行失败。
- `RefundControllerContractTest`：审核成功/失败文案。
- `RefundStateMapperContractTest`：固定 Refund/Order CAS SQL、通用动态 update 已删除、`refund_time` 无 B3 写入口。
- `RefundStateMigrationSqlTest`：脚本静态契约。
- `RefundStateMysqlIntegrationTest`：数据库 CAS、并发与迁移。
- `RefundStateSpringMysqlIntegrationTest`：真实代理事务提交/回滚。

文件名可以按现有测试包结构微调，但验收维度不得删减。`PaymentServiceImpl`、`OrderServiceImpl`、`ProductMapper`、用户端 Controller、`Orders`、`Payment` 仅审计；如发现必须修改的新范围，先停下并修订 Design/plan。

## Branch and dirty-worktree handling

- B3 在 `D:\market-handsome\Final-StandMarket-worktrees\b3-refund-state`、分支 `codex/b3-refund-state` 开发，基线为 `master@26394d64`。
- 主工作区仍在 B1 分支，三项未提交工作流文档保持原状，不 reset、stash、暂存或混入 B3。
- B2 与 `stage-b-ac6-gate` worktree 保留原状。
- 本 workpack 只暂存 B3 Design、workpack、必要产品代码、测试和 SQL；不得纳入本地敏感配置。

## Risks and rollback

- **虚假完成状态**：审核路径通过专用 CAS 限制为 `1`，源码审查禁止 B3 写 `2`。
- **重复审核**：数据库 `WHERE status=0` 是并发正确性边界，Service 先查结果不作为正确性依据。
- **拒绝半完成**：退款 CAS 与订单恢复 CAS 同事务，订单零行必须抛错回滚。
- **申请前状态漂移**：申请使用订单状态 CAS，避免并发确认收货后仍保存旧状态。
- **历史状态不可证明**：首次迁移遇到历史 `1/2`、非法/缺失 `order_status`、异常完成时间、不可恢复待审核记录或已拒绝但订单未合法恢复的记录主动失败；双 B3 marker 与 `order_status NOT NULL + CHECK(3,4)` 保证升级库持续维持恢复事实并区分合法重跑。
- **旧实例越界写入**：切换必须停写、排空并停止全部旧实例；全量核验 B3-safe 制品后才能开放，禁止旧新版本滚动重叠处理退款。
- **错误回滚**：首次切换失败时退款入口保持关闭，旧制品只有在 user/admin 退款路由全部硬隔离时可承载其他流量；重新开放后故障也先关入口、排空，再切换到已验证且 schema 兼容的 B3-safe 制品。不得恢复 B2 退款路径或回退数据库约束。
- **配置泄漏**：MySQL 测试只引用已被 Git 忽略的本地配置路径，不输出或复制凭据值。

## Verification commands

实现阶段先按新增测试类执行精确 RED/GREEN。最终命令至少包括：

```powershell
$B3_MYSQL_CONFIG = 'D:\market-handsome\Final-StandMarket\backend\fashion-server\src\main\resources\application-dev.yml'
if (-not (Test-Path -LiteralPath $B3_MYSQL_CONFIG)) { throw 'B3 MySQL config is missing' }
git -C 'D:\market-handsome\Final-StandMarket' check-ignore --quiet -- 'backend/fashion-server/src/main/resources/application-dev.yml'
if ($LASTEXITCODE -ne 0) { throw 'B3 MySQL config is not ignored by Git' }

Set-Location backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=RefundServiceImplTest,RefundControllerContractTest,RefundStateMapperContractTest,RefundStateMigrationSqlTest' test
mvn -pl fashion-server '-Db3.mysql.integration=true' "-Db3.mysql.config=$B3_MYSQL_CONFIG" '-Dtest=RefundStateMysqlIntegrationTest,RefundStateSpringMysqlIntegrationTest' test
mvn test

Set-Location ../frontend/fashion-admin
npm run build
Set-Location ../fashion-client
npm run build

Set-Location ../../..
git diff --check
git diff --stat
git diff --name-only
rg -n '退款成功|setStatus\(2\)|refund_time|restoreStock\(' backend/fashion-server/src/main backend/fashion-pojo/src/main frontend
```

源码扫描结果必须人工区分普通订单取消的合法 `restoreStock` 与退款审核路径；不得把全局无匹配作为错误目标。`RefundStateMapperContractTest` 必须解析生产 XML，证明同意/拒绝是固定目标 CAS、动态退款状态/完成时间 update 已删除；退款服务依赖测试证明不再注入 `OrderDetailMapper`/`ProductMapper`。真实 MySQL 测试只创建并清理符合固定随机命名规则的临时 schema，不执行生产迁移。
