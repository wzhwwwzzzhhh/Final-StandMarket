# B7-coupon-review-integrity · Workpack plan

> Status: CI 验证中（2026-09-04）；commit `413b345` / PR #19
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B7 / GitHub Issue #18（Stage B 总跟踪 #3）
> Design: `docs/design/order/B7-coupon-review-integrity-design.md`（已确认，2026-09-03；独立 Review P0/P1/P2/P3 均为 0）
> Baseline: `master` @ `657286e6f95f362e6408699967e16db5f1eb21cf`
> Branch/worktree: `codex/b7-coupon-review-integrity` / `D:\market-handsome\Final-StandMarket-worktrees\b7-coupon-review-integrity`

## Scope

### In scope

- 固化 B2 普通订单信任边界：忽略客户端 `amount/activityId/秒杀 couponId`，只按服务端购物车、商品价格和合法 `userCouponId` 计价，秒杀数据库/Redis 状态不变。
- 使用 MySQL current locking read、锁后 `NOW(3)`、共享模板锁和持有券 CAS，完整校验归属、状态、持有券/模板有效期、模板启用、合法规则、全量商品范围与服务端金额。
- 让预览和真实创建共用购物车规模/归属门禁与券计价规则；锁券、库存、订单、券绑定、明细保持同一真实事务，失败整体回滚。
- 评价提交改用输入 DTO 和受约束写入，校验本人已完成订单与订单商品；数据库 `(order_id,product_id)` 唯一键保证重复/并发幂等。
- 评价检查改为 `orderId + productId`；公开列表/统计只显示 `status=1`，公开列表返回白名单 DTO 与脱敏名；本人和管理端继续使用独立受保护契约。
- 增加非破坏性 B7 增量迁移和一致的新建库基线，覆盖合法 legacy、重复执行、错误/部分定义和脏数据阻断。
- 按 TDD 留存 RED/GREEN、真实 MySQL 8/Redis 7 隔离证据、前端生产构建、完整后端回归和独立 Review。

### Out of scope

- 优惠叠加、部分商品分摊、退款优惠分摊、新券类型、领取限量模型重构、新营销页面或全局前端 API 重构。
- B8 商品缓存/ES、B9 AI/全局 Axios、B10 Flyway、B11 生产切换/阶段级交付。
- 秒杀 Redis/MQ/MySQL 状态机修改；RabbitMQ 和 Elasticsearch 不是 B7 测试依赖，也不进行任何队列、索引操作。
- 自动清洗历史评价、生产迁移、生产数据变更、部署或发布。
- 未经另行明确授权的 commit、push、PR、merge 或远程仓库设置修改。
- 主工作区用户未提交的 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md` 和 `docs/prototypes/`，以及现有 B2-B6/AC6 worktree 的任何改写或清理。

## Acceptance mapping

| AC | Planned behavior | Test-first verification |
|---|---|---|
| B7-AC1 | 普通订单忽略 `amount/activityId/秒杀 couponId`，持久化非秒杀默认值且不触碰秒杀表/Redis | 先写订单 Service RED；真实 MySQL + 隔离 Redis 哨兵测试比较金额、字段及 `seckill:*` 前后快照 |
| B7-AC2 | 他人券、状态 1/2/3、持有券过期、模板停用、固定期未开始/已结束、非法规则均不能抵扣；领券/使用时间来自锁后 DB 时间 | 先写规则/Service RED；真实 MySQL 状态矩阵、JVM 时钟偏移和跨时间边界锁等待测试 |
| B7-AC3 | 全店/分类/商品范围按订单全部商品判断，门槛和折扣只用服务端原始总额，统一 `HALF_UP` | `CouponPricingPolicyTest` 参数矩阵；预览/创建共用策略测试；真实混合订单与伪造金额测试 |
| B7-AC4 | 同一持有券并发下单最多一个成功；任一创建步骤失败不留券锁、孤单、半明细或库存变化 | 真实 MySQL 双线程竞争；锁券后/订单插入后/券绑定后/明细插入触发器或测试数据源故障注入 |
| B7-AC5 | 评价只允许当前用户对已完成订单中的商品提交 | 先写 Review Service/Mapper RED；真实 MySQL 覆盖他人订单、未完成订单、订单外商品和客户端伪造字段 |
| B7-AC6 | 同一 `(order_id,product_id)` 串行/重复/并发最多一条，同订单不同商品可分别评价 | 真实 MySQL 唯一冲突和并发插入；断言稳定业务错误且不泄露约束/SQL |
| B7-AC7 | 检查接口按订单+商品；公开列表仅白名单/脱敏名且隐藏评价不进入列表或统计 | Controller/Mapper 行为测试和 JSON 序列化测试；匿名/登录路由测试；用户端按商品入口与生产构建 |
| B7-AC8 | 迁移前阻断重复、NULL、孤立订单/商品、用户不匹配和商品不在明细，不自动修改数据 | SQL 合约 RED；真实 MySQL 每类脏数据执行脚本并断言 `SQLSTATE 45000`、数据/索引未改 |
| B7-AC9 | clean/合法 legacy/重复执行/正确约束成功，错误或部分定义阻断；新建基线与增量最终结构一致 | 真实 MySQL 迁移矩阵和 `SHOW CREATE TABLE` 签名比较；升级脚本扫描确认无 `DROP TABLE` |
| B7-AC10 | 相关聚焦测试、真实依赖、完整 `mvn test`、前端 build、独立 Review 和 Git 检查有新鲜证据 | 按本计划命令执行；任何真实依赖不可用在 `evidence.md` 保留 blocker，不以 Mock 冒充 |

## Slices

### Slice 1 — 通用券时间、计价与订单事务

1. RED：为共享 `CartSelectionValidator`、`CouponPricingPolicy`、领券 DB 时间、持有券/模板 locking read、最终 CAS 和安全异常映射编写最小行为测试，确认当前 JVM 时间、模板门禁、未知范围放行和客户端预览金额缺口。
2. GREEN：实现 1–100 个正数无重复购物车项门禁，在 Redis 订单号生成和任何数据库写入前用于预览/创建；以服务端购物车与商品快照计算原始金额。
3. GREEN：领券取得模板 `FOR SHARE` 后单独读取 DB 时间；下单按 `user_coupon FOR UPDATE → coupon_template FOR SHARE → SELECT NOW(3)` 取单版本规则，再只对持有券 CAS。
4. GREEN：预览/创建复用失败关闭的规则与舍入策略；`lockAndDiscount`、`bindUseOrder` 强制加入外层事务；claim/available/create 只回显安全业务异常。
5. 运行单元/Mapper 聚焦 GREEN，再执行真实 MySQL 并发、时间栅栏、故障回滚和隔离 Redis 非干扰验证。

### Slice 2 — 评价授权、幂等与公开契约

1. RED：新增输入伪造、订单归属/完成状态/商品归属、二元检查、公开字段、隐藏状态、匿名路由和并发重复评价测试，逐项观察当前错误行为。
2. GREEN：引入 `ReviewCreateDTO`、`ReviewPublicVO`、`ReviewMineVO`；Service 从登录态取用户，Mapper 用受约束 `INSERT ... SELECT` 写入。
3. GREEN：增加 `uk_review_order_product(order_id,product_id)` 依赖和精确 DuplicateKey 映射；公开列表/统计 SQL 强制 `status=1`，公开 VO 仅白名单并按 Unicode code point 脱敏。
4. GREEN：检查接口保留路径并要求 `productId` 参数；用户端改为按不同 `productId` 提供评价入口和二元检查，缺参/非法分页与评分失败关闭。
5. 运行聚焦测试、真实 MySQL 串行/并发用例和用户端生产构建；不以源码字符串测试替代可执行后端行为或真实数据库证据。

### Slice 3 — 迁移矩阵、回归、独立审查与证据

1. RED：编写迁移 SQL 合约和真实 MySQL fixture，证明当前缺少唯一约束、可重复升级和脏数据前置门禁。
2. GREEN：新增 `mysql/add_review_integrity.sql`，在任何 DDL 前验证表/列/数据/同名索引；合法 legacy 添加唯一键，正确结构 no-op，错误/部分定义 `SIGNAL`；同步两个初始化基线。
3. 对 clean、legacy、second run、正确/错误/部分索引、每类脏数据运行真实迁移；比较最终元数据，确认增量脚本无破坏性 `DROP TABLE`。
4. 新鲜运行全部 B7 聚焦/真实依赖、后端 `mvn test`、用户端 build、diff/敏感扫描，把命令、退出码、测试数和 blocker 写入 `evidence.md`。
5. 状态改为“待审查”后启动独立只读实现 Review；修复并复审至 P0/P1/P2 全部为 0，之后再次新鲜验证，才能标记“本地已验证”。

## File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/service/OrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/CouponService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/ReviewService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/CouponServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/ReviewServiceImpl.java`
- 新增 `CouponPricingPolicy`、`CartSelectionValidator` 和明确安全业务异常（最终包名遵循现有分层）。
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserCouponController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/ReviewController.java`
- `backend/fashion-server/src/main/java/com/fashion/config/Webconfig.java`
- `UserCouponMapper.java`、`ReviewMapper.java`、必要时的 `OrderDetailMapper.java` 及对应 XML。
- `backend/fashion-pojo/src/main/java/com/fashion/dto/ReviewCreateDTO.java`
- `backend/fashion-pojo/src/main/java/com/fashion/vo/ReviewPublicVO.java`
- `backend/fashion-pojo/src/main/java/com/fashion/vo/ReviewMineVO.java`
- 可用券响应 VO（名称按现有 VO 规范确定）。
- `frontend/fashion-client/src/api/coupon.js`
- `frontend/fashion-client/src/api/review.js`
- `frontend/fashion-client/src/views/CreateOrder.vue`
- `frontend/fashion-client/src/views/AddReview.vue`
- `frontend/fashion-client/src/views/Order.vue`
- `mysql/add_review_integrity.sql`、`mysql/review_table.sql`、`mysql/final07.sql`、必要的 `mysql/README.md` 说明。

### Expected tests

- `CartSelectionValidatorTest`、`CouponPricingPolicyTest`、`CouponServiceImplB7Test`、`OrderCouponIntegrityTest` 和 B7 用户券/订单 Controller 安全响应测试。
- `ReviewServiceImplTest`、`ReviewControllerTest`、`ReviewMapper`/路由/序列化行为测试。
- `B7CouponOrderMysqlIntegrationTest`、`B7CouponTemplateConcurrencyMysqlIntegrationTest`、`B7ReviewMysqlIntegrationTest`、`B7ReviewMigrationMysqlIntegrationTest`、`B7CouponRedisNonInterferenceIntegrationTest`（名称可按实际紧密合并）。
- `ReviewIntegrityMigrationSqlTest` 只承担 SQL 静态安全合约，不能替代真实 MySQL 迁移测试。

文件超出本节时先核对是否为 B7 AC 的必要依赖；触及秒杀状态机、缓存/ES、全局 Axios、Flyway 或新业务范围时立即停止并回到 Design/需求确认。

## Repeatable dependency startup conditions

### MySQL 8

- 仅使用 MySQL 8.0.x 隔离测试实例。连接配置固定放在本 B7 worktree 被 Git 忽略的 `backend/fashion-server/src/test/resources/application-test.yml`，由 `-Db7.mysql.config=<absolute path>` 传入；测试只读取 `fashion.datasource`，不得输出密码或带凭据 URL。
- 启动/连接前对配置执行 `git check-ignore`；配置不受 Git 忽略、主机/库名疑似生产、版本不是 8.0.x时，测试在建表和写入前失败。
- 每个测试类只创建名称匹配 `^fsm_b7_[a-z0-9_]+_[0-9a-f]{32}$` 的随机临时 schema；清理前再次校验绝对 schema 名，只删除本测试创建的 schema，不操作既有库。
- 测试账号需具备临时 schema 的 CREATE/DROP/DDL/DML/触发器权限；若不能满足，在 evidence 记录具体阻塞，不降级为 H2/Mock 结论。

### Redis 7

- 只接受 Redis 7.0.x 隔离库；复用同一被忽略的 B7 测试配置，由 `-Db7.redis.config=<absolute path>` 传入，并显式要求 `database=15`、`exclusive=true`、首次写入前 `DBSIZE=0`。
- 预置 B7 保留高位 coupon/user ID 对应的精确 `seckill:coupon:stock/users/reservations` 哨兵 Key；普通订单测试后逐值比较不变。
- 只清理测试明确创建的哨兵 Key 和当日 `order:number:seq:*` 测试 Key；禁止 `FLUSHDB`、`KEYS *` 或模糊删除。若 DB15 非空或版本不符，在任何写入/清理前失败。

### 当前连接与预检

- B6 在 2026-09-03 留下的证据表明：经用户建立的隧道曾可访问 MySQL 8、专属 Redis 7.0.15 DB15 和 RabbitMQ 3.12.14。B7 不继承“当前仍可用”的结论，实现阶段必须重新执行版本、PING、空库和配置忽略检查。
- RabbitMQ/Elasticsearch 与 B7 无关，不作为通过条件，也不执行写入式探测。
- 若使用 Docker 重新提供依赖，必须固定 MySQL 8.0.x、Redis 7.0.x，只绑定 localhost/测试网络和显式测试 volume；启动/停止命令、镜像版本、健康检查及清理结果在实际采用后写入 evidence，不预先虚构。

## Fault injection matrix

| Scenario | Repeatable injection | Pass condition |
|---|---|---|
| 伪造普通订单优惠 | 请求携带极小 `amount`、有效 `activityId/秒杀 couponId`，MySQL/Redis 预置秒杀哨兵 | 按服务端普通原价；秒杀字段默认；秒杀库存/占用/预扣逐值不变 |
| 券状态/归属 | 构造他人券和 status 1/2/3 | 全部零锁定、零订单、库存不变；统一安全业务错误 |
| DB/JVM 时间偏移 | 注入偏移 JVM Clock，数据库时间不变 | 领取时间、到期时间和使用资格仅服从锁后 DB 时间 |
| 跨有效期锁等待 | 连接 A 对模板持排他锁跨过 start/end；请求线程先阻塞，A 提交后继续 | 请求使用释放锁后单独读取的 DB 时间；开始后可用、结束后拒绝 |
| 同模板并发 | 两个外层事务使用不同持有券但同一模板，调用后在提交前 barrier | 两者都能越过模板共享锁栅栏；模板更新等待；无全局热行串行化 |
| 同券并发订单 | 两线程使用同一持有券和独立购物车/库存 | 最多一个订单提交；失败方库存、券、订单、明细全部回滚 |
| 创建事务故障 | 测试 schema 触发器分别在锁券后、订单插入、券绑定、明细插入抛错 | 各阶段均不留永久锁券、孤立订单/明细或库存变化 |
| 底层异常脱敏 | claim/available/create 注入含 SQL、约束名和模拟凭据片段的 DataAccess 异常 | 响应只有固定安全文案；服务端凭 trace ID 诊断，响应无底层文本 |
| 评价授权 | 构造他人订单、状态 1/2/3/5/6、订单外商品和伪造 user/status/time | 全部零插入；只有本人 status=4 且商品在明细成功 |
| 重复评价 | 相同 `(order,product)` 串行两次和并发多线程；同订单另一个商品 | 同键一条且失败文案稳定；不同商品各一条 |
| 隐藏/公开评价 | 同商品插入 status 0/1，用户资料含手机号等字段和 emoji 名称 | 公开列表/统计只计 status=1；JSON 仅白名单；名称按 code point 脱敏 |
| 迁移脏数据 | 分别制造重复、NULL、孤立订单/商品、用户不匹配、商品不在明细 | 每例 DDL 前 `SQLSTATE 45000`；原数据与索引不变，诊断含类型/计数/样例 ID |
| 迁移错误定义 | 同名非唯一、反序、单列、前缀或不可见索引；另测正确约束重复执行 | 错误/部分定义阻断；正确结构 no-op；clean/legacy 最终签名一致 |

生产版本错配和维护窗口不可达证据属于 B11：B7 只实现并测试新后端对旧缺参请求失败关闭，不会声称本地后端测试证明“新前端 + 旧后端”不可达。

## Risks and rollback

- **热门模板锁竞争**：持有券独占、模板共享；不同券同模板可并发，模板管理更新等待。真实 MySQL 栅栏测试必须证明，不用 Mock 推断。
- **时间线性化**：`NOW(3)` 必须在全部 locking read 返回后单独读取；若读时间失败则事务回滚，不退回 JVM 时间。
- **事务原子性**：失败方不局部重试锁券或订单写入；死锁/锁等待整体回滚并返回安全文案。
- **重复评价**：应用检查仅用于友好提示，唯一约束才是并发正确性门；非目标完整性异常不能误映射成重复。
- **迁移脏数据**：B7 不自动清洗；发现任何异常即阻断，维护者需另行审批数据处置。
- **不安全回滚**：B7 前优惠券代码不是有效回滚目标。安全回滚制品必须保留 DB 时间、locking read/CAS、失败关闭、服务端金额、评价白名单与唯一约束；否则关闭普通订单创建、可用券预览及评价写入，不能恢复旧逻辑。
- **发布切换**：未来由 B11 在维护窗口关闭相关流量，先迁移、再同时部署匹配前后端、烟测后恢复；B7 本地验证或未来合并都不代表已完成该门禁。

## Verification commands

实现阶段按每个新行为先运行精确 RED，再最小 GREEN。计划中的最终命令至少包括：

```powershell
$B7_TEST_CONFIG = 'D:\market-handsome\Final-StandMarket-worktrees\b7-coupon-review-integrity\backend\fashion-server\src\test\resources\application-test.yml'
$B7_MYSQL_CONFIG = $B7_TEST_CONFIG
$B7_REDIS_CONFIG = $B7_TEST_CONFIG
if (-not (Test-Path -LiteralPath $B7_MYSQL_CONFIG)) { throw 'B7 MySQL config is missing' }
if (-not (Test-Path -LiteralPath $B7_REDIS_CONFIG)) { throw 'B7 Redis config is missing' }
git check-ignore --quiet -- $B7_MYSQL_CONFIG
if ($LASTEXITCODE -ne 0) { throw 'B7 MySQL config is not ignored by Git' }
git check-ignore --quiet -- $B7_REDIS_CONFIG
if ($LASTEXITCODE -ne 0) { throw 'B7 Redis config is not ignored by Git' }

Set-Location backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=CartSelectionValidatorTest,CouponPricingPolicyTest,CouponServiceImplB7Test,OrderCouponIntegrityTest,ReviewServiceImplTest,ReviewControllerTest,ReviewIntegrityMigrationSqlTest' test
mvn -pl fashion-server '-Db7.integration=true' "-Db7.mysql.config=$B7_MYSQL_CONFIG" "-Db7.redis.config=$B7_REDIS_CONFIG" '-Dtest=B7*IntegrationTest' test
mvn test

Set-Location ../frontend/fashion-client
npm run build

Set-Location ../..
git diff --check
git status --short
git diff --stat
git diff --name-only
rg -n "BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|password\s*[:=]\s*[^$<{]|secret\s*[:=]\s*[^$<{]|AKIA[0-9A-Z]{16}" backend frontend mysql docs/workpack/B7-coupon-review-integrity docs/design/order/B7-coupon-review-integrity-design.md
```

实际类名和命令可在保持 AC 一一映射时收敛，但不能删除真实 MySQL/Redis、完整 `mvn test`、用户端 build、diff 和敏感扫描门禁。所有命令只有实际运行并读完退出码后才能记为通过。
