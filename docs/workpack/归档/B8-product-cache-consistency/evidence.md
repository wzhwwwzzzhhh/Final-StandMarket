# B8 商品缓存版本化与 ES 可恢复同步 · Evidence

> Status: 已归档（2026-09-04）；[PR #23](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/23) @ `f2f4a2c` 首轮 5 项 checks 全绿，等待归档提交复验后合并；保留已披露的 MySQL 8.0.15 tooling blocker
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Current remote master observed: `0512000cf2e80eb7c9ec9c3441e3b5b310845d0d`（含 B9）

## Evidence policy

- 只记录实际执行的命令、时间、环境版本、隔离标识、退出码和原始结果摘要。
- Mock/静态契约测试不得标成真实 MySQL/Redis/ES 证据；依赖不可用时如实记录 blocker。
- 不记录密码、token、private key、完整敏感响应或真实生产 endpoint。
- 每个 TDD 行为保留首次有效 red 原因及对应 green 命令；最终完成结论只基于 fresh rerun。

## Workflow gates

- Formal Design: 用户于 2026-09-04 确认。
- Design independent Review: PASS，P0=0、P1=0、P2=0、P3=0，reviewer 文件修改 0。
- Implementation plan：用户于 2026-09-04 确认。
- Implementation Review Round 1：FAIL，P0=0、P1=5、P2=5、P3=1；修复见 `review.md`。
- Implementation Review Round 2：FAIL，P0=0、P1=2、P2=3、P3=0；修复与剩余环境 blocker 见 `review.md`。
- Implementation Review Round 3：FAIL，P0=0、P1=0、P2=1、P3=0；reviewer 指出双 worker 仅覆盖同 task 行竞争，已补同商品不同版本 task 的 revision lease 竞争测试。
- Implementation Review Round 4：PASS，P0=0、P1=0、P2=0、P3=1；reviewer 文件修改 0。P3 为 evidence 状态/数量过时，已按最终结果更正。
- Commit/push/PR/merge/生产变更：未授权，均未执行。

## Acceptance results

| AC | 当前结果 | 可执行证据 |
| --- | --- | --- |
| AC1/AC3/AC5 | PASS | key 规范化/golden vector；MySQL+Redis 并发旧读 V1 与新读 V2 隔离；旧 fill 不能进入新代际 |
| AC2 | PASS | 真实 Spring 事务代理 + MyBatis：目录更新推进一次，stock-only 不推进；version/revision/Redis task/ES task 四个注入点均整体回滚且不发布 after-commit 事件 |
| AC4 | PASS | Redis missing/behind/ahead/corrupt/unavailable fail-safe；max-publish 单调；降级/发布结果 counters |
| AC6/AC7 | PASS | 唯一 token、Lua compare-delete、fenced set、A/B 锁接管、真实 PTTL 与抖动边界 |
| AC8/AC9 | PASS | durable ES task/tombstone；timeout/429/503 可重试，400/401/403 终态；真实 MySQL 同商品不同版本 task 竞争 revision lease、lease expiry/stale owner/current supersede；旧 version 拒绝，重上架保留新事实 |
| AC10 | PASS | task/run 查询、active-or-latest 脱敏 DTO、不可变 metrics snapshot、异常摘要与 HTTP 消息脱敏 |
| AC11 | PASS | stock 不进入长 TTL cache/ES，响应从 MySQL补齐；sales null→0，目录/库存混合分类明确 |
| AC12 | PASS | 真实 MySQL 8.0.x、Redis 7.0.x、兼容 ES 8.x（IK/Pinyin 插件）新鲜矩阵 32/32 |
| AC13 | PASS | 最终 B8 baseline 551/0/0/147；detached current-master+B8 572/0/0/147；两处真实依赖均 33/33；diff/scope/sensitive gates 全绿 |
| AC14 | PASS | Round 4 独立只读 Review：P0=0、P1=0、P2=0、P3=1；P3 文档数量已更正；reviewer 文件修改 0 |

## TDD log

### Slice 1 — cache identity, authority, TTL and ownership

- RED（2026-09-04 14:18-14:19 CST）：`mvn -pl fashion-server -am '-Dtest=ProductCacheKeyTest,ProductCacheTtlPolicyTest,CacheClientOwnershipTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 在 testCompile 失败，缺少 `NormalizedProductQuery`、`ProductCacheKeys`、`ProductCacheProperties`、`ProductCacheTtlPolicy` 及 token lock API；23 个预期编译缺口。
- GREEN（2026-09-04 14:21 CST）：同一命令退出码 0；Tests run: 8, Failures: 0, Errors: 0, Skipped: 0。

### Slice 1 / MySQL-authoritative Redis version gate

- RED（2026-09-04 14:23 CST）：`mvn -pl fashion-server -am '-Dtest=ProductCatalogVersionGateTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 在 testCompile 失败，缺少 authority/gate 行为；2 个预期编译缺口。
- GREEN（2026-09-04 14:24 CST）：同一命令退出码 0；Tests run: 4, Failures: 0, Errors: 0, Skipped: 0。覆盖 missing、behind、publish failure、Redis unavailable/corrupt、unexplained ahead 与 MySQL invalid/unavailable fail-closed。
- Review-driven RED：释放异常与 executor rejection 两条测试均先失败。
- GREEN：`ProductCatalogCacheServiceTest` 6/6；释放故障不破坏 MySQL 响应，提交拒绝立即释放 owner token。

### Slice 2 — transaction facts, task rows and migration

- 先以 service/mapper/transaction 行为测试建立目录、stock-only、mixed、no-op 和逐故障点回滚缺口，再加入最小事务协调实现。
- 迁移 exact signature 新增测试首次得到 3 个预期失败：额外列/缺失索引、弱化 CHECK、缺失 singleton。
- GREEN：补齐四表列/索引/CHECK/generated expression/engine/collation 签名后真实 MySQL 10/10；随后加入逆序 partial-order 回归，最终 11/11。
- Round 2 RED：保留原 token 的 `CHECK(id=1 OR id=2)`、revision 额外状态、错误 task default 与错误 active-slot generated expression 均被旧迁移错误接受；真实迁移套件 2 个断言失败。
- Round 2 GREEN：canonical equality 与逐列 default/extra 签名生效；真实 MySQL 迁移套件 14/14。真实 8.0.15 因本机无 Docker CLI 无法启动，单独记为 blocker。
- Round 2 事务故障矩阵：真实 Spring 代理在 version、revision、Redis task、ES task 四个失败点均回滚 product/version/revision/task，且 after-commit 事件为空。
- 真实端到端首次 RED：对账 run 第一批完成后仍为 `RUNNING` 且无法再次 claim。
- GREEN：owner CAS 保存中把未完成 run 重新置为 `PENDING`；repository 单测和端到端恢复测试 1/1。

### Slice 3 — ES delivery, reconciliation and visibility

- 受控 HTTP transport 先建立 timeout/429/503 与 mapping/auth 4xx 分类缺口，再实现 retryable/terminal 映射；最终 1/1（单测试内覆盖 6 种状态）。
- 旧 `/admin/es/sync` 异常消息测试先出现 1/2 失败；改为固定用户消息并仅记录异常类型后 2/2。
- owner/current-revision fence、lease 恢复、max-attempt terminal、指标、active-or-latest status 与脱敏序列化均有聚焦行为测试。
- Round 2 RED：旧 ES task 因 current-revision 检查过早而无法进入 supersede；task/reconcile outcome metrics 缺少行为契约。
- Round 2 GREEN：owner lease/current revision 两层 fence；owner CAS 后按 target 记录 task outcome、记录 reconcile outcome，stale CAS 不计数。真实 MySQL 双 worker、过期接管、stale completion 与旧任务让位测试全绿。
- Round 3 review-driven regression：构造同商品 101/102 两条 pending ES task，双真实 Spring/MyBatis worker 并发 claim；结果精确为一个 `PROCESSING/attempt=1`、一个 `PENDING/attempt=0`，证明 task `SKIP LOCKED` 之后 revision-row lease 仍只授权一个 owner；释放后另一 task 立即 claim。聚焦真实 MySQL 类退出码 0。
- 真实 ES 验证 UPSERT→DELETE→更高版本重上架，以及旧 DELETE/UPSERT 不得覆盖。

## Real dependency verification

### 2026-09-04 17:03 CST — final real-dependency matrix

命令范围：显式 `-Db8.integration=true`，loopback-only 配置，独占空 Redis、UUID MySQL schema、UUID ES index；未输出任何凭据。

首次执行因非提交测试配置目录名写错而退出 1：22 tests 中 2 个 setup error（`B8 config is missing`），属于命令配置错误，不是产品断言失败。定位正确路径后完整重跑，退出码 0：

- `B8ProductCacheMigrationMysqlIntegrationTest`：11/11。
- `B8ProductCatalogSpringMysqlIntegrationTest`：6/6。
- `B8ProductCacheRedisIntegrationTest`：2/2。
- `B8ProductCacheConsistencyIntegrationTest`：2/2。
- `B8ProductProjectionEsIntegrationTest`：2/2。
- `B8ProductProjectionRecoveryIntegrationTest`：1/1。
- 合计：24 tests，Failures=0，Errors=0，Skipped=0。

环境门禁由测试直接断言：

- MySQL version 以 `8.0.` 开头；每场景只创建/删除正则限定的 UUID schema。
- Redis version 以 `7.0.` 开头、exclusive flag=true、启动 `DBSIZE=0`；只操作精确测试 key。
- ES version 为 8.x、exclusive flag=true，存在 `analysis-ik` 和 `analysis-pinyin`；只操作 `products_b8_it_<uuid>` 或 recovery UUID index。

## Backend regression

### 2026-09-04 17:07 CST

- Command: `cd backend; mvn test`
- Exit code: 0
- Result: Tests run 543, Failures 0, Errors 0, Skipped 141；Reactor 4/4 SUCCESS；BUILD SUCCESS。
- Spring 的 YAML loader 输出确认 B8 `fashion.product-projection` 与已合入 master 的 B9 `fashion.agent` 均处于正确 binding 层级。
- 说明：141 个 skip 包含必须显式启用的真实依赖 suites；真实依赖已由上一节单独 24/24 证明。

### 2026-09-04 18:17-18:20 CST — Round 2 修复后新鲜验证

- 聚焦 B8 单元/契约测试命令退出码 0；包含缓存 identity/TTL/锁、事务协调、任务/对账 repository、worker、ES transport、控制器与迁移静态门禁。
- 真实依赖命令显式使用分离的 datasource/Redis 非提交配置、`b8.integration=true`、loopback ES 与 exclusive flags；退出码 0：
  - migration 14/14；Spring/MyBatis/MySQL 11/11；Redis 4/4；ES 2/2；恢复链路 1/1。
  - 合计 32 tests，Failures=0，Errors=0，Skipped=0。
- `cd backend; mvn test` 退出码 0：Tests run 550, Failures 0, Errors 0, Skipped 146；Reactor 4/4 SUCCESS；BUILD SUCCESS。
- 两次配置错误/环境误选重跑不计作产品 PASS：首次遗漏 Redis/ES 参数产生 4 setup error；第二次误用共享 6379 配置被 Redis 7.0.x 门禁拒绝。切换回 36379 隔离 Redis 后完整矩阵全绿。

## Parallel B9 / master integration

- B8 基线是任务指定的 `9845417`；实现期间远程 master 前进到 `0512000`（B9）。
- 只读 diff 确认重叠文件为 `application.yml` 与 `docs/workpack/README.md`。
- 已人工保留 B9 的 agent 五项配置和 B9 归档行，并新增 YAML 层级回归测试。
- 已在临时共享 clone（detached `0512000`）叠加全部 B8 tracked/untracked 文件；没有修改 B9 worktree。
- `mvn test` 退出码 0：Tests run 564, Failures 0, Errors 0, Skipped 141；其中包含 B9 与 B8 测试，BUILD SUCCESS。
- 同一临时副本真实 MySQL/Redis/ES 矩阵再次退出码 0：24/24。

## Fresh final verification

### 2026-09-04 18:26-18:33 CST

- Round 4 独立只读 Review：PASS；P0=0、P1=0、P2=0、P3=1，reviewer 文件修改 0。P3 已在本次归档纠正。
- 实际 B8 worktree：
  - 新增同商品多版本并发测试的聚焦真实 Spring/MyBatis/MySQL 类退出码 0。
  - `cd backend; mvn test` 退出码 0：Tests run 551, Failures 0, Errors 0, Skipped 147；Reactor 4/4 SUCCESS。
  - 真实依赖矩阵退出码 0：migration 14、Spring/MyBatis/MySQL 12、Redis 4、ES 2、恢复链路 1；合计 33/33，Failures=0，Errors=0，Skipped=0。
- detached `Final-StandMarket/master` `0512000` + B8 临时副本：
  - `cd backend; mvn test` 退出码 0：Tests run 572, Failures 0, Errors 0, Skipped 147；Reactor 4/4 SUCCESS。
  - 同一真实依赖矩阵 33/33，Failures=0，Errors=0，Skipped=0。
- 静态门禁：
  - `git diff --check` 退出码 0；仅 Git 的 LF→CRLF 工作区提示，无 whitespace error。
  - 91 个 untracked text 文件逐行 trailing-whitespace 扫描为 0；全部 106 个变更文件仅位于 `backend/`、`docs/`、`mysql/`，outside scope=0。
  - 敏感模式候选 5 个文件均为从非提交 YAML 读取 `password` 的测试变量，没有硬编码值；`gitleaks` 在当前主机不可用，未声称其通过。
  - 危险命令候选仅为计划中明确禁止的 `FLUSHDB` 文本，以及四个经正则限定 UUID schema 的集成测试 `DROP DATABASE` 清理；无宽泛清理命令。
  - 主工作区受保护的 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md`、`docs/prototypes/` 状态保持原样，未暂存、覆盖或混入 B8。
- 远程刷新：`git fetch Final-StandMarket master` 因当前主机无法连接 GitHub 443 失败；没有把失败描述为最新远端。兼容回归基于本地已获取的 `0512000`（已含 B9，且高于任务指定最低基线）。

## Remote delivery verification

### 2026-09-04 18:48-19:01 CST

- GitHub CLI 认证为仓库账号 `wzhwwwzzzhhh`；HTTPS Git 端点超时后，只读验证 GitHub 官方 SSH 通道认证成功，并通过该官方通道获取远端 `master` 与无强推推送功能分支。
- 最新远端 `master` 为 `0512000cf2e80eb7c9ec9c3441e3b5b310845d0d`；B8 单一功能提交无冲突变基，提交由 `d8138b8` 重写为 `f2f4a2c`。
- 变基后 `cd backend; mvn test` 新鲜退出码 0：Tests run 572, Failures 0, Errors 0, Skipped 147；Reactor 4/4 SUCCESS；`git diff --check Final-StandMarket/master...HEAD` 退出码 0；106 个文件均为 B8 已审查范围。
- [PR #23](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/23) 首轮 checks 5/5 PASS：Java backend、Python agent、fashion-admin build、fashion-client build、Gitleaks。
- 用户已明确授权 commit、push、PR、merge；本归档提交仍须再次通过同一组 GitHub checks，禁止绕过失败或缺失检查。
- 未执行生产 MySQL/Redis/ES 操作、生产迁移或部署；B0-AC6、B10、B11 与 B8 的生产发布门禁仍保持关闭。

## Blockers

- `tooling_blocked`：当前 Windows 主机没有 Docker CLI，无法启动真实 MySQL 8.0.15 证明 `<8.0.16` 实例拒绝。迁移脚本存在版本门禁，MySQL 8.0.x 上的首次/重复/部分定义/错误签名/脏数据行为已真实验证；未把版本字符串 Mock 记为真实低版本证据。
- 该 blocker 不掩盖或替代已完成的真实 MySQL 8.0.x、Redis 7.0.x、兼容 ES 8.x 验证；部署门禁仍保持关闭。
