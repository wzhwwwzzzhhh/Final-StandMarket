# B8 商品缓存版本化与 ES 可恢复同步 · Implementation Plan

> Status: 已确认，进行中（2026-09-04）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B8 / GitHub Issue #20
> Confirmed Design: `docs/design/cache/B8-product-cache-consistency-design.md`（2026-09-04，独立 Review P0/P1/P2/P3=0）
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Branch: `codex/b8-product-cache-consistency`
> Worktree: `D:\market-handsome\Final-StandMarket-worktrees\b8-product-cache-consistency`
> Updated: 2026-09-04

## 1. Delivery boundary

### 1.1 In scope

- 以 MySQL 单调版本和商品 revision 为事实源，落地版本化商品列表/详情缓存、参数规范化、详情回写 fence、唯一锁 token 与 Lua compare-delete。
- 把商品新增、目录字段更新、上下架和删除的商品事实、全局版本、revision、Redis/ES 恢复任务纳入一个 Spring/MyBatis/MySQL 本地事务；区分 stock-only、mixed、catalog 和 no-op。
- 落地 REDIS/ES 两类持久任务的 claim/lease/CAS、有限重试、终态、人工重放审计和查询/指标；ES 使用不可变 snapshot/tombstone、external version 与当前 revision 门禁。
- 落地 durable reconciliation run，检测 MySQL/ES 缺失、孤儿、旧 version/hash 和 tombstone 窗口外漂移，并以幂等 task 修复。
- 新增四张 B8 表的前向迁移、严格部分定义/脏数据门禁及回滚说明；保留 `sales` nullable 的旧应用兼容边界。
- 用真实 Spring 事务代理、MyBatis、MySQL 8.0.16+、Redis 7.0.x、兼容 ES 实例验证关键行为，并归档可重复证据。

### 1.2 Explicit exclusions

- 不新增销量累计、支付后销量更新、秒杀库存或其他业务能力。
- 不重构搜索相关性、IK/Pinyin mapping，不修改前端或 `agent-service`，不处理 B9 契约。
- 不把 B8 扩展为通用 outbox/分布式事务框架，不处理 B10 Flyway/baseline 总治理。
- 不宣称 MySQL、Redis、ES 原子提交；依赖失败通过 fail-safe 读取和持久任务最终收敛。
- 不执行生产迁移、生产 Redis/ES 操作、部署或发布切换；B0-AC6、B10、B11 和 B8 本身仍是发布门禁。
- 未经后续单独授权，不 commit、push、创建 PR 或 merge。

### 1.3 Workspace and parallel-work safeguards

- 只在本 worktree 修改；主工作区中的 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md`、`docs/prototypes/` 不读取后覆盖、不暂存、不提交、不清理。
- 不修改、清理或回退 B2-B7、B9 及 stage-b-ac6 worktree。
- `application.yml`、`docs/workpack/README.md`、Design/Stage 索引和共享配置类可能与 B9 重叠。实际编辑前重新查看 B9/最新 master 的同路径 diff；只做 B8 最小增量，集成时人工合并，禁止复制或覆盖 B9 内容。
- 计划确认前只允许当前 Design/workpack 文档变更；任何 Java、XML、SQL、YAML、Lua 或测试代码都属于实现阶段。

## 2. Acceptance criteria and test mapping

| ID | Issue #20 acceptance | Test/evidence required before local completion |
| --- | --- | --- |
| B8-AC1 | 列表 key 含单调版本和确定性规范化参数，不使用伪通配符 `DEL`、`KEYS` 或全量扫描失效 | `ProductCacheKeyTest` 覆盖字段顺序、长度前缀、UTF-8、合法域、SHA-256 golden vector；真实 Redis 命令观测证明只访问当前版本精确 key |
| B8-AC2 | 新增、目录修改、上下架、删除成功时版本恰好推进一次；事务回滚不推进、不留下假任务 | `ProductCatalogServiceTest` 先测分类；`B8ProductCatalogSpringMysqlIntegrationTest` 逐故障点断言商品/state/revision/两类 task 同提交或同回滚 |
| B8-AC3 | 依赖健康时，写成功后才开始的新列表请求不能命中旧版本；旧 key 只按有界 TTL 消亡 | 可控 latch 的 MySQL+Redis 并发集成测试，断言旧读只能回写 V1、新读选 V2、V1 `PTTL` 有界 |
| B8-AC4 | Redis 不可用、版本丢失/behind/ahead/corrupt 或发布失败时安全回源 MySQL，故障可查询、可重试 | `ProductCatalogVersionGateTest` + `B8ProductCacheRedisIntegrationTest` 故障矩阵；REDIS task 状态、attempt、last error、next retry 和降级指标断言 |
| B8-AC5 | 更新/下架/删除后的详情不返回旧对象；并发旧读不能覆盖新代际；空值使用独立短 TTL | 真实 MySQL+Redis latch race；ACTIVE/INACTIVE/DELETED/never-existed 分支与 `PTTL` 断言 |
| B8-AC6 | 锁使用唯一 token，释放为 Lua compare-delete；A 不能删除后继 B 的锁 | `CacheLockTest` + 真实 Redis A 租约过期/B 接管测试，断言 A 释放返回 0、B token 仍存在 |
| B8-AC7 | 实际值、空值、逻辑/物理 TTL、单位和随机抖动可配置且严格生效 | 可注入 jitter 的 0/max 确定性单元测试；真实 Redis 精确单位和物理 TTL 上下界断言；启动配置非法值测试 |
| B8-AC8 | ES UPSERT/DELETE 失败有持久恢复事实；已删除商品仍可重放 DELETE；只有文档 404 可视为 delete idempotent success | `ProductProjectionWorkerTest` + 真实 ES 删除源行后重放；区分 document 404、index 404、连接/超时/4xx/5xx |
| B8-AC9 | ES 重试幂等、有序、有限、有失败终态；重复、并发、乱序安全 | 双 worker claim、revision lease、external version、旧 owner CAS、进程崩溃/lease expiry、attempt=8 终态和人工重放测试 |
| B8-AC10 | pending、attempts、last error、next retry、terminal 可观察；日志不含凭据或完整敏感响应 | task/reconciliation 查询与指标测试；异常摘要长度/脱敏断言；最终敏感信息扫描 |
| B8-AC11 | 明确并实现 stock/sales 边界 | stock-only 扣减/回补/admin 更新不推进目录版本且响应从 MySQL补齐；mixed 只推进一次；cache/ES 无 stock，sales null→0 且保留排序/响应契约 |
| B8-AC12 | 使用真实 Spring 代理、MyBatis、MySQL、Redis 7 和兼容 ES 验证，mock 不冒充集成证据 | 显式断言 `AopUtils.isAopProxy`；真实依赖套件记录版本、隔离标识和命令输出；ES 不可用时在 evidence 记 blocker |
| B8-AC13 | 聚焦测试、完整后端测试、diff/scope/sensitive checks 全部新鲜执行 | 运行第 7 节命令并把时间、退出码、测试数/失败数和依赖版本归档到 `evidence.md` |
| B8-AC14 | 独立只读实现 Review PASS，P0/P1/P2=0，evidence 完整 | 实现后独立 reviewer 只读检查；所有 finding 修复并重跑，`review.md` 才可标 PASS |

## 3. TDD slices

实施严格遵守 red → green → refactor。每个行为先保留可执行失败证据，再做满足该行为的最小实现；源码字符串/静态契约测试只能补充，不能代替行为或真实依赖测试。

### Slice 1 — Versioned cache primitives and read paths

**Behavior first**

1. 为规范化查询和 key golden vectors 写失败测试：非法 page/pageSize/category/sort、用户端固定 `isSale=true`、Unicode/空白、长度前缀、字段顺序和 SHA-256。
2. 为 list/detail MySQL authority gate 写失败测试：Redis equal/missing/behind/ahead/corrupt/unavailable；MySQL 缺行/多行/非法/不可达必须 503 fail closed。
3. 为详情 generation、旧读 race、INACTIVE/DELETED empty、never-existed 不缓存写失败测试。
4. 为唯一 token、`SET NX PX`、Lua compare-delete/fenced set、失锁线程和代际推进写失败测试。
5. 为 `Duration` 配置、逻辑/物理/empty TTL 和实际/empty jitter 的 0/max 边界写失败测试。

**Minimal implementation surface**

- cache query/value projection、properties、version/revision gate、Redis max-publish/fenced-write/compare-delete Lua 和 `CacheClient` 最小重构。
- 用户列表/详情读路径改为先取 MySQL authority，健康 Redis 才访问当前命名空间；响应前单次批量/单项补齐 MySQL stock，不把 stock 写回长 TTL payload。
- 建立稳定 503 错误码和必要的降级指标/日志，但不在本切片引入 ES worker。

**Green gate**

- 单元测试全绿；真实 Redis 7 集成覆盖 key、PTTL、max-publish、锁接管和 stale-fill race。
- Slice 1 结束时不允许出现无物理 TTL 的 B8 value key，也不允许 `KEYS`、通配符 `DEL` 或 Redis 版本反向覆盖 MySQL。

### Slice 2 — Transactional catalog facts, task rows, and migration

**Behavior first**

1. 为目录字段、stock-only、mixed、no-op 分类写失败测试；证明 mixed 只推进一次，stock-only/no-op 不推进。
2. 通过真实 Spring bean proxy 和 MyBatis 为新增/修改/上下架/删除写 commit/rollback 测试；在 product 写后、version 后、revision 后、REDIS task 后/ES task 前分别注入 SQL failure。
3. 为 afterCommit 的 claim DB、Redis I/O、completion CAS、executor wakeup 分别写失败测试，证明提交成功不被伪装成失败且 poller 可恢复。
4. 为新增 `image`、显式 `sales=0`、legacy null→0 规范化及 cache/task snapshot/hash 一致写失败测试。
5. 为迁移首次执行、完整重跑、三个允许的 exact-empty prefix、中断恢复、反向/非空/错误定义、低版本和脏数据门禁写真实 MySQL 失败测试。

**Minimal implementation surface**

- `product_catalog_state`、`product_catalog_revision`、`product_projection_task`、`product_projection_reconcile_run` 的实体、Mapper/XML、约束和手工前向迁移。
- ProductService 事务用例接管 Controller 写路径，事务内推进版本/revision并写 REDIS/ES task；删除保留 tombstone，ACTIVE 为 UPSERT、INACTIVE/DELETED 为 DELETE。
- afterCommit 仅通过同一 claim/token/CAS 处理器执行短超时 REDIS fast path 和 worker wakeup；所有异常捕获并由固定 poller 兜底。
- stock 从 cache/ES payload 移除；sales 保留且 legacy null 规范为 0，不新增 sales 业务写入。

**Green gate**

- 单元/真实 MySQL 事务套件全绿；迁移矩阵逐项独立 schema 执行并记录结果。
- 所有目录事务的业务事实和两类 task 同提交/同回滚；Controller 不再直接拼接跨存储顺序。

### Slice 3 — Recoverable ES delivery, reconciliation, and operational visibility

**Behavior first**

1. 为 canonical projection exact bytes/hash 写 golden vectors：null、中文/emoji、转义字符、两位小数和固定字段顺序。
2. 为 task claim/attempt/lease/CAS、REDIS/ES lease 分流、重复回调、两个 worker、进程死亡、旧 owner 完成和 max-attempt terminal 写失败测试。
3. 为真实 ES UPSERT/DELETE、document 404、index 404、429/5xx/timeout、mapping/auth 4xx、重复同版本、旧 UPSERT/DELETE 晚到、下架后重上架写失败测试。
4. 为 reconciliation active_slot、PIT/search_after cursor、MySQL-only、ES-only、旧 version/hash、INACTIVE 残留、tombstone 窗口外漂移、phase 中断重启和 clean verify 写失败测试。
5. 为 pending/attempt/error/next retry/terminal 查询、指标、脱敏日志和 destructive live index rebuild 禁用/替代写失败测试。

**Minimal implementation surface**

- 有限退避任务 worker、每商品 ES revision-row lease、external/external_gte 版本写入、错误分类、人工重放审计和管理可观察面。
- durable reconciliation run 只创建/重开幂等 task；完整双向扫描和 clean verify 前不标完成。
- 移除或安全替代会 delete/recreate live index 的 `/admin/es/sync` 行为；baseline/backfill 也只通过 task pipeline。

**Green gate**

- 真实 ES 兼容套件与故障分类通过；如果兼容实例客观不可用，停止“本地已验证”声明并在 evidence 标 blocker。
- 运行全部聚焦测试、完整 `backend/mvn test`、独立只读 Review 和最终新鲜验证。

## 4. Expected file surface

路径以 confirmed Design 为约束，类名可在不改变契约时小幅调整：

- `backend/fashion-common/`：CacheClient、Duration 配置、Redis Lua 资源和通用缓存投影原语。
- `backend/fashion-pojo/`：catalog state/revision/task/run、缓存与 ES projection DTO；HTTP VO 字段保持兼容。
- `backend/fashion-server/`：Product Controller/Service/Mapper/XML、版本门禁、task worker、ES service、reconciliation、管理查询与调度。
- `backend/fashion-server/src/main/resources/application.yml`：仅非敏感 B8 默认值；修改前先协调 B9 同路径变更。
- `mysql/add_product_cache_consistency.sql` 与 `mysql/README.md`：B10 前的前向手工迁移和验证说明。
- `backend/fashion-server/src/test/`：聚焦单元、Spring/MyBatis/MySQL、Redis 7、ES 兼容实例和迁移集成测试。
- 必要时新增 `docker/compose/b8-integration.yml` 作为测试基础设施：固定兼容镜像、无静态共享 container name、端口仅绑定 loopback、全部名字/卷带运行 UUID；不修改或启动生产 compose。

Plan 确认即表示同意上述测试基础设施/测试配置属于实现所需的有限例外；凭据仍只放被 ignore 的本地 `.env` 或进程环境变量，绝不提交真实 secret。

## 5. Repeatable real-dependency setup

### 5.1 Isolation and preflight shared rules

- 每次生成 32 位小写十六进制 `B8_RUN_ID`，并在 evidence 记录非敏感标识。
- 所有 endpoint 必须是 loopback；启动前解析 host，若非 `127.0.0.1/localhost` 立即拒绝。禁止对用户现有共享/生产依赖做 destructive cleanup。
- 本地凭据使用 ignored `backend/fashion-server/src/test/resources/application-test.yml` 或环境变量；先用 `git check-ignore` 证明配置不会入库。若文件已存在，只读取所需键并保留用户内容，不覆盖。
- 集成测试必须显式 `-Db8.integration=true`；依赖缺失不得静默 skip 后宣称通过。

### 5.2 MySQL 8

- 版本要求 `>= 8.0.16`；推荐隔离容器固定 `mysql:8.0.36`，端口只绑定 loopback。
- 测试账号仅允许创建/删除 `fsm_b8_it_<B8_RUN_ID>`；测试启动断言版本、`CHECK` 被执行、schema 名正则和初始对象状态。
- 每个迁移场景使用独立 UUID schema；清理前再次验证 resolved schema 严格匹配正则，只删除本次 schema。
- 真实 Spring 测试使用项目 transaction manager、MyBatis Mapper 和从容器取得的 Spring bean；断言 `AopUtils.isAopProxy(productService)`。

### 5.3 Redis 7

- 推荐隔离容器固定 `redis:7.0.15-alpine`，端口只绑定 loopback，`--save '' --appendonly no`；使用独立随机测试密码或本机隔离无密码实例，凭据不落库。
- 启动断言 Redis 7.0.x、exclusive test flag 和 `DBSIZE=0`。若不能证明实例独占且为空，禁止运行破坏性集成步骤。
- key 全部带 `fsm:b8:it:<B8_RUN_ID>:` 前缀；清理只删除测试记录下来的精确 key，不用 `FLUSHDB`、`KEYS` 或宽泛 pattern。

### 5.4 Elasticsearch-compatible instance

- 以仓库兼容目标 ES 8.17.0 为首选；实例必须 loopback、专用测试节点并验证 IK/Pinyin 插件、版本和所需 REST contract。
- 索引固定 `products_b8_it_<B8_RUN_ID>`；启动/清理均验证 exact regex，禁止访问、别名切换或删除共享 `products`。
- 验证 metadata mapping：`catalogVersion` 为 long，`projectionHash` 为 keyword；验证 external version、`gc_deletes`、document 404/index 404 和 client 7.17.15 对服务端 8.17.0 的实际兼容性。
- 若需要新增测试 compose，使用独立 project name、UUID 容器/卷和健康检查；结束只停止本次 project。镜像/插件无法取得时如实记录 blocker，不用 mock 代替。

## 6. Failure-injection matrix

| Area | Injection | Required assertion |
| --- | --- | --- |
| MySQL transaction | product/version/revision/REDIS task/ES task 各阶段 SQL failure | 商品事实、全局版本、revision、两 task 全部回滚；afterCommit 未执行 |
| Mutation classification | stock-only、catalog-only、mixed、no-op | 版本推进分别为 0/1/1/0；mixed 仅一组 task；stock 响应从 MySQL补齐 |
| After commit | claim DB、Redis call、completion CAS、executor wakeup 抛错 | API 保持 DB 提交成功语义；task 可由 poller 恢复；异常不越过 Controller |
| List version | Redis unavailable/timeout/missing/behind/ahead/corrupt/max-publish fail | 不读取旧命名空间；安全读 MySQL；故障指标/task 可观察；MySQL failure 时 503 |
| Detail race | 旧 reader DB 查询后暂停，提交新 revision，再释放旧 reader | 旧值最多写旧 key；新请求不读取旧对象；所有 key 有物理 TTL |
| Lock | A token 到期，B 获取新 token，A 后释放 | compare-delete 返回 0，B token 未被删除；失锁线程不能 fenced-write |
| TTL | jitter supplier 分别返回 0/max，秒/毫秒配置边界 | actual/empty/logical/physical PTTL 落在精确边界；非法配置启动失败 |
| ES transport/status | connection refused、timeout、429、503、mapping 400、401/403、index 404、document 404 | 仅 document DELETE 404 成功；retryable 进入有限 RETRY_WAIT；不可重试进入 terminal |
| ES order/idempotency | 重复相同 version、旧 UPSERT/DELETE 晚到、下架再上架 | current revision + external version 不允许旧事实覆盖；窗口外漂移由 reconciliation 修复 |
| Worker lease | 两实例同时 claim、I/O 前后进程死亡、lease expiry、旧 owner CAS | 同 product ES 外呼序列化；每次可外呼 claim 消耗 attempt；最多 8 次后终止 |
| Sink independence | REDIS 与 ES 同 product 同时处理 | Redis task lease 不占 `es_*` lease；一个 sink 成败不掩盖另一个 |
| Reconciliation | 两实例建 run、PIT 失效/phase 中断、ES-only/MySQL-only/旧 hash/version/INACTIVE 残留 | active_slot 只一个；cursor 可恢复或该 phase 从头；只产生有界幂等 task；clean verify 前不完成 |
| Migration | first/repeat/3 allowed empty prefixes/sales update interruption | 幂等完成并验证 exact schema signature，不重复推进 seed/version |
| Migration guard | reverse/nonempty prefix、wrong column/index/check、MySQL<8.0.16、negative/out-of-range/null edge/invariant mismatch | 第一条不安全 DDL/DML 前 `SIGNAL`；未知对象不自动删除或修复 |
| Live sync | 调用旧 destructive ES sync surface | 不执行 live index delete/recreate；只允许 durable reconciliation/task 流程 |

故障注入不得针对生产或共享实例。网络故障优先通过测试 client/容器隔离网络或指向本次 loopback 未监听端口实现，不停止用户其他容器。

## 7. Verification and evidence commands

具体测试类名允许随最小实现微调，但 evidence 必须记录最终可复制的完整命令和原始摘要。

### 7.1 Preflight

```powershell
git status --short --branch
git rev-parse HEAD
git worktree list
git diff --name-only 98454176227bb0b0a936a0b6c58e35f3696e6788...HEAD
git check-ignore -v backend/fashion-server/src/test/resources/application-test.yml
```

另行记录 `mysql --version`/`SELECT VERSION()`、`redis-cli INFO server` 中的版本、ES `GET /` 版本及插件列表；输出中的密码/token 必须先脱敏。

### 7.2 Focused red/green tests

每个 slice 先运行单个新测试并保留 red 失败原因，再实现并重跑 green。最终聚焦集合预计为：

```powershell
cd backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server -Dtest=ProductCacheKeyTest,ProductCatalogVersionGateTest,CacheLockTest,ProductCatalogServiceTest,ProductProjectionWorkerTest,ProductReconciliationServiceTest test
mvn -pl fashion-server -Db8.integration=true -Dtest=B8ProductCatalogSpringMysqlIntegrationTest,B8ProductCacheRedisIntegrationTest,B8ProductProjectionEsIntegrationTest,B8ProductCacheConsistencyIntegrationTest,B8ProductCacheMigrationMysqlIntegrationTest test
```

若 Maven 的模块选择或测试名调整，以能够真实执行目标测试的命令替换，不能用 `-DskipTests` 作为验证证据。

### 7.3 Full and repository gates

```powershell
cd backend
mvn test
cd ..
git diff --check
git status --short
git diff --stat 98454176227bb0b0a936a0b6c58e35f3696e6788
git diff --name-status 98454176227bb0b0a936a0b6c58e35f3696e6788
git diff -- backend mysql docker docs/design/cache docs/workpack/B8-product-cache-consistency docs/workpack/README.md
```

敏感信息复核至少覆盖 tracked/untracked B8 文件中的密码、token、private key、真实 endpoint、`.env`/`application-*.yml`；不得把扫描工具“无输出”以外的 secret 值复制进 evidence。限定范围检查必须确认：

- 没有主工作区用户修改、B9 文件内容、build artifact、日志或本地配置混入。
- 没有 `KEYS`、`FLUSHDB`、伪通配符 `DEL`、共享 `products` 索引删除或 live index recreate。
- 没有 Controller 直接绕过事务 Service 写商品/版本/任务，也没有吞掉非幂等 ES 异常。

### 7.4 Independent implementation review

- 完成实现和第一轮验证后，安排独立只读 reviewer 检查 confirmed Design、Issue #20、全部 diff、测试和 evidence。
- `review.md` 记录每轮 P0/P1/P2/P3、文件修改数和关闭证据；P0/P1/P2 未全为 0 时继续修复并重新 review。
- Review 清零后必须新鲜重跑聚焦测试、真实依赖测试、完整 `backend/mvn test` 和 repository gates；只有这些 fresh outputs 全部归档后才标“本地已验证”。

## 8. Migration, declaration, and rollback verification

### 8.1 Forward migration

- 脚本在第一条 DDL/DML 前验证 MySQL>=8.0.16、目标库/product 表、四表 exact signature 和允许的空前缀。
- 建表顺序固定 state → revision → task → reconcile_run；四表完整后才规范化 nullable sales、插入 singleton seed 和按 status 创建 revision。
- 真实 MySQL 矩阵覆盖首次、完整重跑、三个允许前缀、sales 规范化中断、错误/非空/反向定义、低版本和脏数据。
- 不在脚本中连接 Redis/ES，不执行生产 backfill；baseline/reconciliation 进入正常 durable task pipeline。

### 8.2 Redis/ES declarations

- Redis 不预创建全局结构；只验证 Lua SHA/script、key schema 和所有 value key 有物理 TTL。旧 key 不在实现测试中广泛扫描/删除。
- ES 只在隔离 UUID index 创建 B8 metadata mapping并验证现有 analyzers；不删除/recreate live `products`，不改生产 alias。
- 启动时 mapping/版本/插件不兼容必须 fail closed 或把 task 置为可诊断终态，不能吞错后报告收敛。

### 8.3 Rollback proof

- 代码切流前：旧应用能忽略 additive tables，`sales INT NULL DEFAULT 0` 保持显式 NULL insert 兼容；新表保留、不反向 DROP。
- B8 写流量后：测试/文档证明必须先停商品写、记录/排空 task、核对 MySQL/ES 和受控处理遗留 cache 后才能切回；不能直接回滚旧应用。
- REDIS/ES 失败时不回滚已提交 MySQL，不删除 task/tombstone，不用全量 ES rebuild 掩盖 terminal task。
- 所有真实 rollback rehearsal 只在 UUID schema/key/index 完成；生产回滚、清理和切换仍需 B10/B11 及单独授权。

## 9. Documentation outputs

- 本计划确认后进入实现，将 `docs/workpack/README.md` 状态改为“进行中”。
- TDD red/green/refactor、真实依赖版本/隔离标识、故障注入输出和所有阻塞逐步写入 `evidence.md`。
- 独立实现 Review 逐轮写入 `review.md`，不预填 PASS。
- 若实现改变 confirmed Design 的关键契约、迁移或跨存储边界，立即停止产品代码，回到 Design review 和用户确认门；普通类名/文件拆分不需要重开架构门。

## 10. Confirmation gate

用户已于 2026-09-04 确认本 plan，允许按三个切片开始测试先行实现及上述本地隔离依赖验证。该确认仍不授权 commit、push、PR、merge、生产迁移、生产 Redis/ES 操作、部署或宣称可发布。
