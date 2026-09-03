# B6-rabbitmq-reliability · Workpack plan

> Status: 进行中（用户已确认，2026-09-01）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B6 / GitHub Issue #16（Stage B 总跟踪 #3）
> Design: `docs/design/seckill/B6-rabbitmq-reliability-design.md`（已确认，2026-09-01；独立 Review P0/P1/P2/P3 均为 0）
> Baseline: `master` @ `b07f9ed1a21d09c0c8eefae93a84ae10407d00d0`
> Branch/worktree: `codex/b6-rabbitmq-reliability` / `D:\market-handsome\Final-StandMarket-worktrees\b6-rabbitmq-reliability`

## Scope

### In scope

- 按已确认 Design 为秒杀预扣增加订单号 reservation token 和 registry；同步发送异常立即、原子、幂等恢复 Redis stock/ZSET/HASH。
- 增加 MySQL 消息日志、补偿记录和 reconciliation anomaly 三表及严格、可重跑、可阻断脏状态的 B6 迁移；收紧 `seckill_order.order_number NOT NULL`。
- 启用 correlated publisher confirm、return、mandatory 和 persistent delivery；以稳定 logical messageId、attempt 唯一 CorrelationData 和数据库 CAS 收敛 ack/nack/return/timeout 的乱序与重复。
- 将订单和超时消费者改为持久 attempt、显式 manual ack、最多三次处理；失败事实由 MySQL 接管后 ack 原消息，耗尽后进入独立业务 DLQ，禁止默认热 requeue。
- 订单、MySQL 券库存、ORDER_CREATE 消费状态和 ORDER_TIMEOUT/PREPARED 在同一事务提交；只在提交后按 immutable due_at 发送延迟消息。
- 主动/超时取消统一事务内持久化 `RELEASE_RESERVATION + CANCEL_COMMITTED`，提交后执行 Redis；删除已取消订单受 evidence 门禁保护。
- 增加可靠发布恢复、补偿和定时对账任务；以 MySQL 订单事实、Redis reservation 二元组和 registry 找出悬空预扣，依靠唯一键、CAS、lease 和 token 实现重复/并发/重启幂等。
- 增加真实 MySQL 8、Redis 7、RabbitMQ 3.12 的隔离集成环境说明/编排、行为测试和故障注入证据；无法启动时如实保留 blocker。

### Out of scope

- 完整通用 outbox、跨业务消息平台、新业务能力或 B7-B10 功能。
- B11 生产切流、现有队列排空/删除/重建、生产告警平台接入和部署；B6 只交付声明、信号与切换门禁。
- 生产数据库迁移、生产 Redis 修复、生产 RabbitMQ vhost/exchange/queue 操作。
- B0-AC6、B10、B11 或 B6 发布门禁的关闭；本地验证或未来 PR 合并均不代表可部署。
- 主工作区 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md` 三项用户修改，以及现有 B2-B5 worktree 的任何清理或改写。
- 未经另行授权的 commit、push、PR、merge 和远程仓库设置修改。

## Acceptance mapping

| AC | Planned behavior | Test-first verification |
|---|---|---|
| B6-AC1 | MQ 同步不可达时登记失败事实并立即执行 token-aware Lua，stock、ZSET、HASH 一次性恢复 | 先写 publisher 编排 RED 单测；真实 Redis + 关闭端口联合测试断言三个键恢复、API 失败、补偿可查 |
| B6-AC2 | correlated ack/nack/return/timeout 使用 attempt 唯一 correlation；旧 attempt、重复和乱序 callback 只审计、不回退当前事实 | 先写 callback 状态表 RED 测试；真实 RabbitMQ 验证 ack、mandatory return、reject-publish nack、跨 attempt 乱序 |
| B6-AC3 | 消费最多三次；失败先持久化后 ack，耗尽进入业务 DLQ；非法 identity 稳定隔离；MySQL 不可用才暂停并 requeue | 先写 attempt/ack 决策 RED 测试；真实 RabbitMQ + MySQL 故障验证 attempt 1–3、DLQ、无热 requeue、非法消息去重 |
| B6-AC4 | 同 messageId/orderNumber 的串行、并发和不确定重发最多生成一个订单、扣一次 MySQL 库存、建一条 timeout log | 先写唯一冲突分类 RED 测试；真实 MySQL/RabbitMQ 并发重复投递测试 |
| B6-AC5 | 订单事务任一步回滚时订单、库存、CONSUMED、timeout log 一起回滚，且没有孤立延迟消息 | 先写 after-commit 调用点 RED 测试；真实 MySQL 故障 + Rabbit queue depth/basic.get 验证 |
| B6-AC6 | ORDER_TIMEOUT/PREPARED 在提交内写入，提交后按 immutable due_at/剩余 TTL 发送；到期直接走统一取消 | 先写事务可见性和测试时钟 RED 测试；独立连接+latch+真实 RabbitMQ 验证提交时序、补发和到期兜底 |
| B6-AC7 | 人工悬空 reservation 被 registry/HSCAN/ZSCAN 发现，唯一建补偿、原子修复并留下补偿/异常记录 | 先写对账分类 RED 测试；真实 MySQL/Redis 制造无订单旧 token 并验证修复和审计 |
| B6-AC8 | callback、取消、双实例对账、重复补偿和服务重启只领取一个动作；Redis 成功/MySQL 标记前崩溃不误伤新 token | 先写 claim/lease/token RED 测试；真实 MySQL/Redis 两线程/两 context、进程窗口模拟和重跑测试 |
| B6-AC9 | 主动/超时取消的状态 CAS、MySQL 回补、补偿行和 CANCEL_COMMITTED 同事务；提交后才 Redis，删除路径先验证 evidence | 先写统一取消/删除门禁 RED 测试；真实 MySQL/Redis 在提交后 Redis 前终止并删除、重启恢复 |
| B6-AC10 | order_number 门禁和三表迁移支持 clean、legacy、合法空中断前滚及重复执行；错误/逆序/部分定义和脏数据显式失败 | 先写迁移合约 RED 测试；真实 MySQL 执行首次、重跑、每个 DDL 中断点及坏 schema/data 矩阵 |
| B6-AC11 | 保留 B5 主/延迟/到期队列 wire 边界，新增独立 failure topology；B11 zero-inflight 未满足时不允许切换或降级 | 配置/拓扑合约 RED 测试；隔离 vhost 声明检查；不对生产队列执行操作 |
| B6-AC12 | 真实三依赖证据、完整 `mvn test`、范围/空白/敏感信息检查全部有新鲜记录；任何环境缺口保持 blocker | 显式 B6 集成门禁、Rabbit 重启持久性、完整后端回归和 Git 检查；Mock/源码扫描不得替代真实依赖 |

## Slices

### Slice 1 — Reservation、持久状态与可靠发布

1. RED：新增 B6 迁移、消息/补偿状态机、reservation Lua、同步异常回滚和 publisher callback 测试，逐项确认当前缺少 token ledger、三张持久表、mandatory/correlation 和失败收敛。
2. GREEN：以最小 DDL/POJO/Mapper/Service 实现三表、严格索引/CHECK/claim CAS；更新 clean baseline，收紧 order_number 并完成可重跑迁移门禁。
3. GREEN：扩展预扣/回滚 Lua 为 stock/ZSET/HASH/registry 原子协议；预扣前生成 orderNumber，PREPARED 由 `REQUIRES_NEW` 独立提交。
4. GREEN：集中配置一个 ConfirmCallback/ReturnsCallback，启用 mandatory、correlated confirms 和 persistent message；按 message type/publish purpose/attempt 执行 failure-dominant 状态迁移。
5. 先跑聚焦 RED/GREEN，再运行真实 MySQL/Redis 和 Rabbit publisher 测试；若 Redis 7/RabbitMQ 3.12 尚不可用，只能记录 blocker，不能把 Mockito callback 当作集成通过。

### Slice 2 — 消费事务、有限重试、业务死信与提交后延迟消息

1. RED：新增订单重复、唯一冲突、事务回滚、PREPARED 可见性、delay after-commit、attempt claim、manual ack、非法消息和 DLQ 测试。
2. GREEN：监听层只负责 envelope 校验、persistent attempt 和 broker ack 决策；订单事务原子完成订单、MySQL 库存、CONSUMED、ORDER_TIMEOUT/PREPARED。
3. GREEN：失败事实持久化成功后 ack 当前消息并按退避重发，最多三次；消费耗尽创建独立 BUSINESS_DEAD_LETTER，非法消息使用稳定 SHA-256 quarantine identity。
4. GREEN：订单事务提交后按 immutable due_at 发送 timeout；due_at 已过直接调用可信取消。主动/超时取消共用事务并同写 CANCEL_COMMITTED，Redis 在提交后执行。
5. 用真实 MySQL/RabbitMQ 验证重复投递、消费者失败、DLQ、事务回滚无孤立 delay、提交可见性和取消崩溃窗口；测试失败注入使用测试作用域 DataSource/连接工厂代理或真实约束，不向产品代码加入测试后门。

### Slice 3 — 恢复对账、迁移/拓扑门禁与完整验证

1. RED：新增恢复 lease、并发补偿、悬空预扣、registry 部分损坏、ACKED 老化、删除券残留、服务重启和 anomaly 收敛测试。
2. GREEN：实现有限批量 publisher recovery、compensation 和 reconciliation task；使用 SSCAN/HSCAN/ZSCAN、MySQL 事实优先、唯一键/CAS/lease/token，禁止 KEYS 和全局库存猜测对齐。
3. GREEN：增加仅供隔离测试的 `docker/compose/b6-integration.yml` 和无凭据启动说明；固定 MySQL 8.0.x、Redis 7.0.x、RabbitMQ 3.12.x，测试编排不承担生产部署。
4. 运行下面的完整故障注入矩阵、三表迁移矩阵、broker 重启持久性和全后端回归；逐条把命令、测试数、退出码和 blocker 写入 `evidence.md`。
5. 完成后状态改为“待审查”，启动独立只读实现 Review；P0/P1/P2 全部清零并新鲜重跑全部验证后，才可标记“本地已验证”。

## Repeatable dependency startup conditions

### Supported isolated environment

- 计划新增 `docker/compose/b6-integration.yml`，只绑定 `127.0.0.1` 的测试端口，镜像固定为 MySQL 8.0.36、Redis 7.0.15、RabbitMQ 3.12.14-management；Compose project 固定为 `fsm-b6-it`，使用独立测试 volume，绝不连接生产地址。
- 凭据从被 Git 忽略的绝对路径 env/config 注入，Compose 和日志不得包含真实值。启动前必须用 `git check-ignore` 验证配置未被跟踪；Java 测试读取 `-Db6.config=<absolute ignored path>`，不打印 URL 密码。
- Rabbit provisioner 管理身份仅创建/删除匹配 `^fsm_b6_it_[0-9a-f]{32}$` 的 vhost 和最小 configure/write/read 测试用户；应用测试用户没有全局管理权限。MySQL 每类测试创建匹配 `fsm_b6_*_[0-9a-f]{32}` 的 schema。由于产品 Lua 必须验证真实 `seckill:*` wire key，Redis 配置必须同时满足 `database: 15`、`exclusive: true`、启动时 `DBSIZE=0` 和 7.0.x；fixture 只使用 B6 保留的高位 coupon/user ID 并清理精确键，禁止 FLUSHDB/KEYS 清库。
- 被忽略的 Compose 环境固定放在本 worktree 的 `docker/compose/.env`，Java 连接配置固定放在 `backend/fashion-server/src/test/resources/application-test.yml`；两者都先用 `git check-ignore` 证明不被跟踪。启动命令为 `docker compose --project-name fsm-b6-it --env-file docker/compose/.env -f docker/compose/b6-integration.yml up -d --wait`。停止命令只针对同一显式 project/file：`docker compose --project-name fsm-b6-it --env-file docker/compose/.env -f docker/compose/b6-integration.yml down -v`。
- 也允许使用外部管理的隔离测试实例，但必须通过同一版本、localhost/测试网络、随机 schema/vhost/key prefix、最小权限和清理校验；任何生产连接信息都会使测试立即失败。

### Preflight and current blocker

- Preflight 必须核验：MySQL `SELECT VERSION()` 为 8.0.x；Redis `INFO server` 为 7.0.x、PING 成功、配置显式声明 `exclusive: true` 且 DB15 的 `DBSIZE=0`；RabbitMQ management/AMQP 报告 3.12.x，能够声明/绑定、publish/consume/basic.get 和读取测试 vhost 队列深度；三者时钟可比较。
- 2026-09-01 计划阶段实测：`127.0.0.1:3306` 可达；6379、5672、15672 不可达；Docker/Compose/RabbitMQ CLI 不存在；本机仅有 Redis 5.0.14 可执行文件，不满足 Redis 7 门禁。因此真实 Redis/RabbitMQ 测试当前是明确 blocker，不能标 PASS。计划确认后可先实施测试与代码，但在合规三依赖可用并完成新鲜集成验证前不能“本地已验证”。

## Fault injection matrix

| Scenario | Repeatable injection | Pass condition |
|---|---|---|
| MQ 不可达 | B6 测试连接工厂指向已确认关闭的 localhost 端口，或停止隔离 Rabbit 容器；不改生产配置 | 预扣后同步异常，Redis stock/ZSET/HASH 恢复且补偿有记录 |
| publisher ack | 发布 persistent 消息到正常测试 binding | 当前 attempt 单调到 BROKER_ACKED；重复 ack 无副作用 |
| publisher return | mandatory 发布到存在 exchange 但无 binding 的随机 routing key | return 详情持久化；后到 ack 不覆盖失败；未落单 reservation 释放一次 |
| publisher nack/channel failure | 在隔离 vhost 建 `x-max-length=1,x-overflow=reject-publish` 队列并填满；另向不存在 exchange 发布 | 当前 correlation 得到 nack/失败；重复/乱序 callback 不重复补偿 |
| callback 跨 attempt | attempt 1 timeout，attempt 2 ack 后注入 attempt 1 return/nack | logical ID 不变、correlation 不同；当前状态不回退 |
| 消费者临时失败 | 使用 test-scope DataSource 代理在订单事务确定点抛异常，或制造可回滚的真实 DB 条件失败 | attempt 1–3 持久且退避；原消息由 MySQL 接管后 ack，无热 requeue |
| 毒消息/业务死信 | 连续三次事务失败；另发永久坏 payload、缺失/超长/控制字符 messageId 和超限 body | failure queue 有唯一 envelope，主消费者不被无限占用，非法内容不完整复制 |
| DLQ 不可达 | 让 failure routing return/nack，同时保持 MySQL/Redis 可用 | DLQ 待恢复，但 ORDER_CREATE 无订单时的 reservation 独立释放 |
| 重复投递 | 并发发送同 messageId/orderNumber 两次并制造 confirm 不确定重发 | 一个订单、一次 MySQL 扣减、一条 timeout log |
| 事务回滚/after-commit | 在订单插入后令库存条件扣减 0 或抛异常；用 latch 在 publish 前由独立连接观察 | 回滚时无订单/timeout/delay；正常时 PREPARED 和订单先提交再 publish |
| timeout publish 失败 | 提交后停止 broker并推进可控时钟超过 due_at | 按剩余 TTL 补发，或到期直接取消；不重置 30 分钟 |
| 取消提交后崩溃 | 取消事务返回后、Redis 前终止测试进程/抛模拟进程终止；验证删除 evidence 门禁并重启 | 同一补偿行恢复一次，删除订单后仍凭 CANCEL_COMMITTED 收敛 |
| 悬空预扣 | 真 Redis Lua 写入过安全窗口 token，不写订单/消息日志 | 对账发现、唯一建补偿、修复三个 Redis 事实并留记录 |
| 对账并发/重启 | 两线程和两个 Spring context 同时扫描；Redis 成功后、MySQL 标记前中断 | 一个 claim/一次库存恢复；重跑不误伤新 token，未知窗口人工门禁 |
| registry 部分损坏 | 保留目标完整 token，同时给同券另一用户制造 ZSET-only/HASH-only | 目标安全释放；registry 保留；独立 anomaly 幂等累加并告警 |
| broker 重启 | persistent 消息入 durable 隔离队列后仅重启 `fsm-b6-it` Rabbit 服务 | 重启后消息仍可消费并可关联日志；不能重启时记录 blocker |
| 迁移 | clean、legacy、second run；在 order_number 和三张表每个 DDL 后中断；构造逆序/部分/错误定义和脏数据 | 合法首次/重跑/空中断前滚；未知状态显式 SIGNAL；clean/upgrade 元数据等价 |

## File-level change surface

### Expected production and migration files

- `backend/fashion-server/src/main/resources/application.yml`
- `backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java`
- 新增 MQ confirm/return、manual listener container 和 B6 内部常量配置。
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java`
- 新增 reservation、可靠 publisher、订单消费事务、timeout 发布/消费、统一取消、恢复、补偿和对账 service/task。
- `backend/fashion-server/src/main/resources/lua/seckill.lua`
- `backend/fashion-server/src/main/resources/lua/seckill_rollback.lua`
- 新增消息日志、补偿和 anomaly POJO/Mapper/XML；订单、券 Mapper 仅增加已确认 Design 所需的严格查询/CAS。
- `mysql/add_seckill_mq_reliability.sql`、`mysql/final07.sql`、`mysql/README.md`。

### Expected test infrastructure and tests

- `docker/compose/b6-integration.yml` 与对应非敏感测试启动说明；不写入凭据、不作为生产 compose。
- B6 publisher、callback、consumer attempt/ack、after-commit、取消、补偿、对账和迁移单元/合约测试。
- 条件启用的真实 MySQL、Redis、RabbitMQ 以及三依赖联合集成测试，统一使用 `B6*IntegrationTest` 命名和 `-Db6.integration=true` 门禁。

## Risks and rollback

- 跨存储无法原子：只承诺本存储原子提交和另一存储幂等恢复；所有失败入口共用消息/补偿 CAS，不新增旁路库存写入。
- ack/return/confirm 乱序：每次发布使用 attempt 唯一 correlation，旧 attempt 只审计；失败/消费/补偿终态不可被迟到 ack 覆盖。
- 消费毒消息：MySQL 接管失败事实后 ack 并有限重发；只有 MySQL 无法接管才暂停容器并 requeue，避免热循环。
- 延迟消息孤儿/延期：timeout log 与订单同事务，提交后才发布；due_at 不可变，恢复按剩余 TTL 或直接取消。
- 补偿误伤新预扣：Redis token 必须匹配 orderNumber，所有释放来源共享唯一补偿键；token 不一致进入人工门禁。
- 迁移半完成：只允许精确定义且为空的合法前缀前滚；错误/脏/逆序状态显式阻断。生产回滚保留表和 NOT NULL，不执行自动破坏性逆迁移。
- Rabbit topology 冲突：不修改旧 market.mq arguments；B11 zero-inflight 前不重建 delay.queue。代码回滚保留 durable failure queue 和数据库证据，不删除生产消息。
- 测试基础设施不可用：当前 blocker 不影响 plan 确认，但阻止最终本地验证；不得用 mock、源码字符串或声明检查冒充真实 MySQL/Redis/RabbitMQ 行为。

## Verification commands

实现阶段以实际落地类名为准；如需改名必须保持 AC 映射，至少执行以下新鲜命令并记录完整结果：

```powershell
$B6_ROOT = 'D:\market-handsome\Final-StandMarket-worktrees\b6-rabbitmq-reliability'
$B6_CONFIG = Join-Path $B6_ROOT 'backend\fashion-server\src\test\resources\application-test.yml'
$B6_ENV = Join-Path $B6_ROOT 'docker\compose\.env'
if (-not (Test-Path -LiteralPath $B6_CONFIG)) { throw 'B6 ignored integration config is missing' }
if (-not (Test-Path -LiteralPath $B6_ENV)) { throw 'B6 ignored compose env is missing' }
git -C $B6_ROOT check-ignore --quiet -- $B6_CONFIG
if ($LASTEXITCODE -ne 0) { throw 'B6 integration config must be ignored by Git' }
git -C $B6_ROOT check-ignore --quiet -- $B6_ENV
if ($LASTEXITCODE -ne 0) { throw 'B6 compose env must be ignored by Git' }

Set-Location (Join-Path $B6_ROOT 'backend')
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=B6*Test' test
mvn -pl fashion-server '-Db6.integration=true' "-Db6.config=$B6_CONFIG" '-Dtest=B6*IntegrationTest' test
mvn test

Set-Location '..'
git diff --check
git status --short
git diff --stat
git diff --name-only
$B6_SCAN_TARGETS = @((git diff --name-only), (git ls-files --others --exclude-standard)) | Where-Object { $_ } | Sort-Object -Unique
if ($B6_SCAN_TARGETS.Count -gt 0) {
  rg -n -i 'AKIA[0-9A-Z]{16}|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password\s*[:=]\s*[^$<{]|secret\s*[:=]\s*[^$<{]' -- $B6_SCAN_TARGETS
  if ($LASTEXITCODE -notin @(0,1)) { throw 'Sensitive scan failed to execute' }
}
```

RabbitMQ 拓扑、队列深度、publisher nack/return、业务 DLQ 和 broker restart 必须由真实 3.12 broker 测试输出证明；MySQL 迁移必须真实执行更新后的 clean baseline 与 upgrade SQL；Redis 必须真实执行 Lua 并验证所有相关 key。命令未运行、条件跳过或依赖不可用时在 `evidence.md` 标为 `BLOCKED/NOT RUN`。

## Confirmation gate

用户已于 2026-09-01 明确确认本 plan，workpack 已进入“进行中”。实现按每个 AC 的 RED → GREEN → REFACTOR 顺序推进。用户确认同时覆盖新增仅供隔离集成测试的 Compose 配置这一配置类 TDD 例外；所有行为代码仍必须先有可观察的 RED 测试。
