# B6-rabbitmq-reliability · Evidence

> Workpack status: CI 验证中；本地真实 MySQL 8、Redis 7.0.15、RabbitMQ 3.12.14、三存储联合与 broker restart 均通过
> Baseline: `master` @ `b07f9ed1a21d09c0c8eefae93a84ae10407d00d0`
> Branch/worktree: `codex/b6-rabbitmq-reliability` / `D:\market-handsome\Final-StandMarket-worktrees\b6-rabbitmq-reliability`

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B6-AC1 | `B6SeckillSubmitOrchestratorTest`、reservation/Lua 合约及 `B6CrossStoreReliabilityIntegrationTest` 真实验证 MQ 不可达后 token-aware 回滚库存、用户占用与 registry | PASS |
| B6-AC2 | 单元测试覆盖 callback 重复/乱序/timeout；`B6RabbitPublisherIntegrationTest` 和 MySQL/RabbitMQ 联合测试真实验证 correlated ack、mandatory return+ack、reject-publish nack 及 callback 状态落库 | PASS |
| B6-AC3 | 单元测试覆盖 manual ack、MySQL 不可用时先 requeue 再异步 pause；MySQL/RabbitMQ 联合测试真实验证 attempt 1–3 持久重试、第 3 次唯一业务死信、原消息逐次 ack 且不无限 requeue | PASS |
| B6-AC4 | `B6OrderConsumerMysqlIntegrationTest` 真实 MySQL 验证串行/并发重复只落一个订单、扣一次库存、生成一条 timeout log；真实 Rabbit consumer 重试链路另由联合测试覆盖 | PASS |
| B6-AC5 | 订单消费事务、after-commit 单测与 MySQL/RabbitMQ 联合测试验证数据库回滚不产生 delay、成功提交后才由产品 publisher 写入 delay queue | PASS |
| B6-AC6 | timeout PREPARED、immutable due-at 和到期兜底单测通过；真实 MySQL + RabbitMQ latch 证明初始 publish 被阻塞时独立数据库连接已可见 PREPARED | PASS |
| B6-AC7 | reconciliation、scanner、anomaly、compensation 单测与真实 Redis 7.0.15/MySQL 悬空预扣发现、修复、留痕通过 | PASS |
| B6-AC8 | claim/CAS/lease/token 单测及真实两线程并发对账、重复补偿、新 executor 重启重跑验证一个 claim/一次恢复 | PASS |
| B6-AC9 | 统一取消、删除 evidence 门禁、B5 真实 MySQL 12/12 回归及取消提交后 Redis 清理前崩溃恢复/物理删除/重启幂等通过 | PASS |
| B6-AC10 | `B6ReliabilityMigrationMysqlIntegrationTest` 在真实 MySQL 8 首次、重跑、合法中断前滚、错误/部分定义、脏数据门禁共 8/8 通过 | PASS |
| B6-AC11 | topology/config 合约、随机 vhost、无 tag 最小权限用户、持久 failure queue、三种 failure routing 及 RabbitMQ 3.12.14 容器重启后持久消息保留均通过；B11 zero-inflight 仍是独立发布门禁 | PASS |
| B6-AC12 | B6 聚焦 161、完整后端 395、真实三依赖集成 34、Git 范围/空白/敏感检查和三轮独立 Review 已执行 | PASS |

## TDD and review corrections

- 为 callback 审计先增加真实 MySQL 负例，复现旧 attempt 审计覆盖 `last_error`；实现改为有界追加后转绿。
- 为 listener pause 时序先增加 in-order 负例；实现改为先 `basicReject(requeue=true)`，再通过 `stop(Runnable)` 异步暂停，避免消费线程自等待。
- 为 PREPARED 提交可见性增加真实 MySQL/Rabbit 联合 latch fixture；依赖缺失时条件跳过，不冒充执行成功。
- 删除未使用的 consumer policy 和测试专用非 owner 补偿入口，所有补偿完成更新必须携带 lease owner。
- 为迁移补偿脏状态先增加真实 MySQL 负例，复现非运行态残留 lease、终态残留 retry 时间未阻断；补齐 SQL 门禁后转绿。
- 首次真实 Rabbit 声明因应用用户没有 queue.bind 所需 write 权限 RED；将权限收紧为仅覆盖 B6 既定 topology 的同一正则后转绿。
- 首次真实 publisher fixture 复用一个 `RabbitTemplate` 导致 confirm callback 重复注册 RED；改为每个测试创建独立 template。随后修正异步 purge 竞态、单测试 callback 重复注册和 received delivery-mode 断言，最终真实 broker 5/5 GREEN。
- 最终独立 Review round 1 发现 Redis fixture 会接受 7.2.x、仅凭 DB15 即允许写入，以及联合 Rabbit fixture 仍有异步 purge。修正为写入前强制 `Redis 7.0.x + database=15 + exclusive=true + DBSIZE=0`，并将 5 个 purge 全部改为同步；新增安全门 3/3 单测，联合真实测试修正后 5/5 GREEN。
- 首次三依赖联合执行真实暴露 reservation 时间早于活动开始及 SCAN 原生命令被 Lettuce 标量解码的问题；先保留 RED 证据，再修正 fixture 时间建模，并以 exposed/decorated Lettuce connection + `NestedMultiOutput` 最小修复，聚焦单测 3/3 与真实 SSCAN/HSCAN/ZSCAN 转绿。
- 真实两线程对账暴露 claim 败者把另一实例有效 lease 误报为基础设施故障；增加并发 loser 单测后，二次读取仅将 `SUCCEEDED`/有效 `IN_PROGRESS` 识别为等待收敛，真实 infra failure 仍抛出；真实跨存储 5/5 转绿。
- 首次最终 34 用例执行因临时 Rabbit provision PowerShell URL 拼接错误未实际创建用户而认证失败；修正测试编排、先核验 password hash/permissions 后重跑 34/34。该失败未修改产品逻辑。

## Verification runs

| Time | Command/inspection | Exit/result | Notes |
|---|---|---|---|
| 2026-09-01 | Design independent Review round 5 | PASS；P0/P1/P2/P3=0；reviewer 修改 0 文件 | 用户已确认 Design |
| 2026-09-01 | 用户确认 `plan.md` | confirmed | TDD 实现门禁解除；隔离 Compose 测试配置例外获确认 |
| 2026-09-02 16:55 | `mvn -pl fashion-server -Dtest=B6ReliabilityMigrationMysqlIntegrationTest#dirtyCompensationLeaseAndTerminalRetryAreRejected -Db6.integration=true ... test` | 1/1 PASS | 新增门禁先 RED，再最小 SQL 修复后 GREEN |
| 2026-09-02 16:56 | `mvn -pl fashion-server -Dtest=B6ReliabilityMigrationMysqlIntegrationTest -Db6.integration=true ... test` | 8/8 PASS | 真实 MySQL 8；首次、重跑、合法部分前滚、错误定义和脏数据阻断 |
| 2026-09-02 16:57 | `mvn -pl fashion-server -Dtest=B6OrderConsumerMysqlIntegrationTest -Db6.integration=true ... test` | 11/11 PASS | 真实 MySQL 8；重复/并发消费、事务、owner CAS、callback 审计 |
| 2026-09-02 16:58 | `mvn -pl fashion-server -Dtest=SeckillStateSpringMysqlIntegrationTest,SeckillStateMigrationMysqlIntegrationTest -Db5.integration=true ... test` | 12/12 PASS | B5 真实 MySQL 回归 |
| 2026-09-02 16:58 | `mvn test` from `backend/` | 390 tests；0 failures；0 errors；93 skipped；BUILD SUCCESS | 完整后端；条件集成测试在未显式启用时跳过 |
| 2026-09-02 17:00 | `mvn -pl fashion-server '-Dtest=B6*Test' test` | 156 tests；0 failures；0 errors；34 skipped；BUILD SUCCESS | B6 聚焦；34 个真实依赖测试按门禁跳过，不计作行为 PASS |
| 2026-09-02 17:00 | tool/TCP preflight | MySQL present/3306 reachable；Docker、Compose、Rabbit CLI missing；6379/5672/15672 unreachable；Redis executable 5.0.14.1 | 不满足 Redis 7 / RabbitMQ 3.12 启动条件 |
| 2026-09-03 | 用户建立服务器连接后的只读协议 preflight | Redis `PONG` / 7.2.14；Rabbit AMQP handshake + authenticated management OK / 3.12.14；Elasticsearch 19200 / 8.11.0 / yellow / 1 node；9200 非 ES | 连通性通过；Redis 小版本不符合计划固定 7.0.x，Rabbit 当前为 `/` vhost + administrator，未满足隔离随机 vhost/最小权限，不能据此运行写入式 B6 集成测试 |
| 2026-09-03 14:40 | `B6RabbitPublisherIntegrationTest`，外层脚本创建随机 vhost/无 tag 应用用户并在 finally 清理 | 5/5 PASS；BUILD SUCCESS | RabbitMQ 3.12.14；真实 ack、mandatory return+ack、reject-publish nack、持久业务 DLQ 独立客户端读取、越权 configure 拒绝；最终 run 的 vhost/user/temp 均二次确认不存在 |
| 2026-09-03 14:43 | `B6RabbitMysqlReliabilityIntegrationTest`，随机 MySQL schema + 随机 Rabbit vhost/最小权限用户 | 5/5 PASS；BUILD SUCCESS | 产品 publisher callback 落库、毒消息三次有限重试与业务死信、回滚无 delay、提交后 delay、PREPARED 提交可见性、三种 failure routing；Rabbit vhost/user/temp 二次确认不存在，测试 `@AfterAll` 正常执行 schema drop；额外 schema 元数据二次查询受本机无效 mysql CLI/JShell prefs 限制，未作为独立清理证据 |
| 2026-09-03 14:48 | `mvn -pl fashion-server '-Dtest=B6*Test' test` | 156 tests；0 failures；0 errors；34 skipped；BUILD SUCCESS | B6 新鲜聚焦回归；真实依赖测试默认条件跳过，未重复计算为真实行为证据 |
| 2026-09-03 14:49 | `mvn test` from `backend/` | 390 tests；0 failures；0 errors；93 skipped；BUILD SUCCESS | 完整后端新鲜回归；四模块 reactor 全绿 |
| 2026-09-03 14:50 | `git diff --check`、未跟踪文本检查、冲突标记与高置信敏感扫描 | PASS | tracked diff 无 whitespace error；84 个未跟踪文本文件 trailing/conflict 均为 0；101 个改动/新增文件无 private key、AWS/GitHub token finding。全文件扫描发现的 37 个空白行均位于两个已跟踪文件的既有未修改行，不属于本次 diff |
| 2026-09-03 14:50 | 主工作区只读复核 | 受保护三项修改仍未暂存；用户 `docs/prototypes/` 仍为未跟踪 | 主工作区仍在 `codex/b1-payment-trust-boundary` @ `26374cb...`；未 reset、stash、stage、覆盖或混入 B6 |
| 2026-09-03 14:56 | 最终独立实现 Review round 1 | FAIL；P0=0、P1=0、P2=3、P3=2；reviewer 修改 0 文件 | P2 均为集成 fixture 安全/确定性：Redis 版本、专属空库、异步 purge；产品状态机和迁移未发现 P0/P1 |
| 2026-09-03 14:58 | `mvn -pl fashion-server '-Dtest=B6IntegrationSafetyTest' test` | 3/3 PASS；BUILD SUCCESS | 固化只接受 Redis 7.0.x、显式 exclusive 配置和空 DB 的安全门 |
| 2026-09-03 14:58 | 当前开发 Redis 配置的 gated `B6ReservationRedisIntegrationTest` 负向执行 | 预期拒绝；BUILD FAILURE | 在连接/写入前因 database 非 15 被拒绝；证明误配不会进入 cleanup 或产品 key 写入。该预期失败不计作行为 PASS |
| 2026-09-03 15:01 | 修正同步 purge 后重跑 `B6RabbitMysqlReliabilityIntegrationTest` | 5/5 PASS；BUILD SUCCESS | 全新随机 vhost/无 tag 用户；vhost/user 二次确认不存在；临时配置经 `apply_patch` 创建和删除 |
| 2026-09-03 15:02 | 最终独立实现 Review round 2 | PASS；P0=0、P1=0、P2=0、P3=1；reviewer 修改 0 文件 | 上轮三个 P2 全部关闭；P3 仅保留 Compose 本地测试凭据可能出现在 command/inspect 元数据 |
| 2026-09-03 15:04 | `mvn -pl fashion-server '-Dtest=B6*Test' test` | 159 tests；0 failures；0 errors；34 skipped；BUILD SUCCESS | P2 修正后的最终 B6 聚焦回归；新增 3 个安全门测试 |
| 2026-09-03 15:04 | `mvn test` from `backend/` | 393 tests；0 failures；0 errors；93 skipped；BUILD SUCCESS | P2 修正后的最终完整后端回归；四模块 reactor 全绿 |
| 2026-09-03 15:05 | 最终 Git/资源/主工作区检查 | PASS | `git diff --check` exit 0；17 tracked + 85 untracked（102 total）；未跟踪文本 trailing/conflict 0；高置信敏感 finding 0；staged 0；临时配置目录不存在；分支/基线正确；主工作区受保护修改与用户 prototypes 原样保留 |
| 2026-09-03 15:14 | Redis 7.0.x / broker restart 隔离环境发现 | BLOCKED | Windows 无 Docker/Compose/Rabbit CLI，仅有 Redis 5.0.14；WSL 功能存在但已安装发行版数为 0；6379/5672/15672/19200 均由 FinalShell `fjava.exe` 隧道转发。无法在 Windows 侧建立或重启隔离实例；不得把共享隧道后的现有 Rabbit 节点当作可安全重启的测试节点 |
| 2026-09-03 15:43 | 隔离依赖 preflight | PASS | Redis `PONG` / 7.0.15 / DB15 `DBSIZE=0`；RabbitMQ management 3.12.14 可达；均为 B6 专属实例 |
| 2026-09-03 15:46 | 首次 `B6*IntegrationTest` 三依赖联合执行 | 34 tests；3 failures | 保留 RED：reservation fixture 时间早于活动开始；真实 Lettuce SCAN 返回标量解码而非 cursor/list；未将失败冒充 PASS |
| 2026-09-03 15:52–16:01 | `SeckillRedisScanPageReaderTest` 与真实 `B6ReservationRedisIntegrationTest` | 单元 3/3 PASS；真实 1/1 PASS | `NestedMultiOutput` 修复后真实 SSCAN/HSCAN/ZSCAN 均通过 |
| 2026-09-03 16:02–16:05 | `B6CrossStoreReliabilityIntegrationTest` | 首轮 1 error；修复后 5/5 PASS | RED 暴露并发 claim loser 误报 infra failure；增加有效 lease/SUCCEEDED 二次判定后，真实悬空预扣、并发/重复补偿与重启重跑通过 |
| 2026-09-03 16:09 | `mvn -pl fashion-server '-Dtest=B6*IntegrationTest' '-Db6.integration=true' ... test` | 34 tests；0 failures；0 errors；0 skipped；BUILD SUCCESS | 真实 MySQL 8 + Redis 7.0.15 + RabbitMQ 3.12.14 全量 B6 集成验证 |
| 2026-09-03 16:10 | B6 RabbitMQ 容器 restart 与清理复核 | PASS | RabbitMQ 3.12.14 重启后 durable message 仍保留，message count=1；随机 vhost/临时无 tag 应用用户随后删除并以 404 复核 |
| 2026-09-03 16:14 | 最终独立实现 Review round 3 | PASS；P0=0、P1=0、P2=0、P3=1；reviewer 修改 0 文件 | 新增真实环境修正未引入状态误判、lease 绕过、异常吞噬或幂等破坏；P3 仍仅为 Compose 测试凭据元数据暴露可能性 |
| 2026-09-03 16:15 | `mvn -pl fashion-server '-Dtest=B6*Test' test` | 161 tests；0 failures；0 errors；34 skipped；BUILD SUCCESS | B6 最终聚焦回归；34 个条件集成测试已在上一行单独显式运行并全绿 |
| 2026-09-03 16:16 | `mvn test` from `backend/` | 395 tests；0 failures；0 errors；93 skipped；BUILD SUCCESS | 最终完整后端回归；四模块 reactor 全绿 |
| 2026-09-03 16:18 | 最终 Git/资源/主工作区检查 | PASS | `git diff --check` exit 0；17 tracked + 85 untracked（102 total）；85 个未跟踪文本文件 trailing/conflict 均为 0；102 个改动/新增文件高置信敏感 finding 0；staged 0；临时配置目录不存在；分支/基线正确；主工作区三项受保护修改及用户 `docs/prototypes/` 原样保留 |
| 2026-09-03 | 远程基线与授权核验 | PASS | `Final-StandMarket/master` 仍为 `b07f9ed1...`，与 B6 merge-base 一致；用户明确授权 commit、push 和创建 PR，未授权 merge |
| 2026-09-03 | `f9345b6 feat(seckill): 完善 RabbitMQ 可靠性闭环` | PASS | 仅暂存 102 个 B6 文件；禁止配置 0；staged `diff --check` 通过；无 force 推送到专用分支 |
| 2026-09-03 | GitHub PR #17 | OPEN；CI 验证中 | `codex/b6-rabbitmq-reliability` → `master`；Closes #16，Related to #3；未执行 merge |
| 2026-09-02 17:01 | `git diff --check` + tracked diff/untracked text scan | PASS | tracked diff 无 whitespace error；untracked trailing/conflict count 0；CRLF conversion warnings非 diff error |
| 2026-09-02 17:01 | 限定范围复核 | 101 changed/untracked files；均映射 B6 AC1–AC12、Design/workpack 或隔离测试基础设施 | 无前端、新 API、支付/退款新能力、部署或生产操作 |
| 2026-09-02 17:01 | 高置信敏感信息扫描 | 0 findings | 扫描 private-key、AWS key、GitHub token 标记；未输出配置凭据 |
| 2026-09-02 17:01 | 主工作区只读核验 | 三项受保护修改仍在且未暂存；另有用户侧 `docs/prototypes/` 未跟踪内容 | 未 reset、stash、stage、覆盖或混入 B6；未触碰 B2–B5 worktree |

## Not run or blocked

- B11 的 zero-inflight 上线切换没有运行；它属于独立发布门禁，不影响 B6 本地行为验证结论。
- 未运行生产数据库迁移、生产 RabbitMQ 队列操作或部署。
- 已按授权执行 commit、push 和创建 PR #17；merge、远程仓库设置修改仍未获授权。

## Local delivery summary

- 产品实现、单元/合约测试、真实 MySQL 8、Redis 7.0.15、RabbitMQ 3.12.14、三存储联合故障注入及 broker restart 均已验证；独立 Review 为 P0/P1/P2=0，状态为“本地已验证”。
- B0-AC6、B10、B11 以及 B6 的远程交付状态继续阻止生产发布；本地验证或后续 PR 合并都不代表可以部署。
