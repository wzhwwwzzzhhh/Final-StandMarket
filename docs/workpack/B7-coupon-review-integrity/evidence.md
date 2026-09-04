# B7-coupon-review-integrity · Evidence

> Workpack status: 本地已验证（2026-09-04）；未 commit / push / PR

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B7-AC1 | 单元测试证明客户端金额/秒杀参数被忽略且非法购物车项在 Redis 前拒绝；真实 MySQL+Redis 7.0.15 成功/回滚 2/2 证明服务端金额、非秒杀字段及秒杀哨兵逐值不变 | PASS |
| B7-AC2 | 真实 MySQL 覆盖模板 X 锁跨开始/结束边界、管理更新等待、JVM 时区偏移及归属/状态/固定期/范围矩阵 | PASS |
| B7-AC3 | 统一 `CouponPricingPolicyTest` 与可用券 Service 测试覆盖服务端金额、范围、门槛、非法规则和 `HALF_UP` | PASS |
| B7-AC4 | 真实 MySQL 完整 `OrderService#create` 双线程同券竞争；订单写入、券绑定、明细写入 trigger 故障均整体回滚库存/券/订单/明细 | PASS |
| B7-AC5 | DTO 受约束 `INSERT ... SELECT`；真实 MySQL 拒绝他人、未完成、订单外商品 | PASS |
| B7-AC6 | 真实 MySQL 串行/并发唯一冲突，同订单不同商品分别成功；稳定业务错误 | PASS |
| B7-AC7 | 二元检查、公开 VO 白名单、Unicode 脱敏、精确匿名路由通过；真实 MySQL 证明同商品隐藏评价不进入公开列表和统计 | PASS |
| B7-AC8 | 真实 MySQL 阻断重复、NULL、孤立订单/商品、归属错误、订单外商品且 DDL 前数据/索引不变 | PASS |
| B7-AC9 | BTREE/可见性/列序/前缀最终复核及两个初始化基线真实签名比较通过；真实 MySQL 迁移 4/4 PASS | PASS |
| B7-AC10 | 最终聚焦 52/52、完整后端 453/453（117 条条件测试跳过）、用户端生产构建、最新独立 Review、真实 MySQL 22/22、MySQL+Redis 2/2、diff 与敏感扫描均通过 | PASS |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-03 | Design 独立只读 Review | PASS；P0=0、P1=0、P2=0、P3=0 | 四轮审查完成；用户已确认 Design |
| 2026-09-03 | Workpack plan 用户确认 | confirmed | 进入 TDD；尚无产品实现通过结论 |
| 2026-09-03 18:14 CST | `mvn -pl fashion-server "-Dtest=OrderCreationInventoryTest,CouponServiceImplB7Test,B7ControllerExceptionSafetyTest" test` | RED；11 tests，6 failures | 证明超大/非正购物车项在 Redis 序号生成前未拒绝、未知券类型/范围被放行、订单与领券基础设施异常原样泄露；这是实现前预期失败证据 |
| 2026-09-03 18:14–18:46 CST | B7 切片聚焦 RED/GREEN | GREEN | 购物车门禁、统一券计价、locking read/CAS、DB 时间、可用券服务端预览、评价 DTO/授权/白名单、迁移和异常脱敏逐项先红后绿；未以源码字符串替代可运行行为 |
| 2026-09-03 18:47 CST | `B7CouponReviewMysqlIntegrationTest` | 9/9 PASS；BUILD SUCCESS | 首轮真实 MySQL 8 券/评价串行与并发行为 |
| 2026-09-03 18:48 CST | claim watchdog 聚焦测试 | RED 1 error → GREEN 1/1 | 固定 5 秒 lease 会导致测试失败；改为 `tryLock(waitTime, unit)` 后由 Redisson watchdog 续期至事务完成 |
| 2026-09-03 18:51 CST | B7 Redis gated run（当前配置） | EXPECTED BLOCK；写入前停止 | 配置不是 DB15，安全门拒绝；未执行 Redis 测试写入 |
| 2026-09-03 18:52 CST | B7 Redis gated run（显式 DB15/exclusive） | BLOCKED；写入前停止 | 连接成功但真实版本为 Redis 7.2.14，不满足已确认的 7.0.x；`DBSIZE` 和哨兵写入均未继续，不以 7.2 冒充 7.0 证据 |
| 2026-09-03 18:54 CST | `B7CouponReviewMysqlIntegrationTest` | 12/12 PASS；BUILD SUCCESS | 真实 MySQL 8：外层事务 MANDATORY、同券完整下单一胜者、三处 trigger 故障整体回滚、同模板共享锁、评价并发唯一 |
| 2026-09-03 18:56 CST | 迁移列定义门禁 TDD | RED 1 failure → GREEN 4/4 | 脚本新增 `BIGINT NOT NULL` 校验；真实矩阵覆盖 clean/repeat、错误/部分列和索引、全部脏数据 |
| 2026-09-03 18:58 CST | 公开评价异常脱敏 TDD | RED 1 error → GREEN 5/5 | 基础设施错误不再逃逸或回显连接/密码文本，只返回固定文案并记录 trace ID/异常类型 |
| 2026-09-03 19:00 CST | `npm ci`；`npm run build` | 80 packages；BUILD PASS | 首次 build 因新 worktree 无 `node_modules` 在编译前阻塞；按锁文件安装后 Vite 6.4.2 成功转换 1728 modules |
| 2026-09-03 19:03 CST | B7 聚焦单元/契约/迁移静态测试 | 47/47 PASS；BUILD SUCCESS | 优惠、订单、评价、路由、异常与迁移静态合约新鲜通过 |
| 2026-09-03 19:04 CST | `cd backend; mvn test` | 441 tests；0 failures；0 errors；110 skipped；BUILD SUCCESS | 四模块完整后端新鲜回归；条件真实依赖测试默认跳过，未重复计为真实证据 |
| 2026-09-04 10:13 CST | 恶意通用异常回显负测 | RED；8 tests，3 failures | 普通 `BaseException`、任意 `IllegalArgumentException` 与评价 `BaseException` 可泄露敏感文本，确认第一轮 P1 |
| 2026-09-04 10:28 CST | `B7ControllerExceptionSafetyTest` | GREEN；8/8 | Controller 只回显专用公开异常；通用异常固定脱敏 |
| 2026-09-04 10:33 CST | 新增真实 MySQL 栅栏/矩阵测试 | BLOCKED；连接被拒绝 | localhost MySQL 隧道已不可达；未沿用 9 月 3 日结果冒充新增用例通过 |
| 2026-09-04 10:34 CST | 迁移 BTREE/ALTER 后复核静态测试 | RED；2 tests，1 failure | 证明原脚本未校验 `index_type` |
| 2026-09-04 10:36 CST | 初始化基线时间精度测试 | RED；1 failure | 证明 `review_table.sql` 与 `final07.sql` 的 `DATETIME(3)` 不一致 |
| 2026-09-04 10:42 CST | 异常、迁移、优惠聚焦 | GREEN；15/15 | BTREE 最终复核、基线精度、恶意异常脱敏通过 |
| 2026-09-04 10:44 CST | 公开异常封闭目录测试 | RED；1/1 failure | 证明任意字符串构造器仍开放 |
| 2026-09-04 10:51 CST | 安全目录与受影响服务聚焦 | GREEN；33/33 | `PublicBusinessException.Code` 封闭目录，无字符串构造入口；订单/优惠/评价行为通过 |
| 2026-09-04 10:41 CST | `mvn -pl fashion-server -am -DskipTests test` | BUILD SUCCESS | 包含重写后的真实 MySQL+Redis 联合测试在内的 111 个测试源编译成功；不等于真实依赖执行通过 |
| 2026-09-04 10:53 CST | 第二轮独立只读实现 Review | FAIL；P0=0、P1=1、P2=2、P3=0 | 发现隐藏评价夹具会违反唯一键，以及两处并发测试栅栏可能产生伪证据；真实 MySQL 恢复前先修正测试本身 |
| 2026-09-04 10:56 CST | `cd backend; mvn test` | 453 tests；0 failures；0 errors；117 skipped；BUILD SUCCESS | 修正安全异常边界后的完整回归；条件真实依赖测试默认跳过 |
| 2026-09-04 10:58 CST | Review 第二轮 findings 修订后测试源码编译 | BUILD SUCCESS | 隐藏评价唯一键夹具、DB 时间边界和更新线程栅栏均可编译；不等于真实 MySQL 执行通过 |
| 2026-09-04 10:59 CST | B7 最终聚焦单元/契约/迁移静态测试 | 52/52 PASS；BUILD SUCCESS | 优惠、订单、评价、路由、安全异常和迁移静态合约新鲜通过 |
| 2026-09-04 11:00 CST | `cd backend; mvn test` | 453 tests；0 failures；0 errors；117 skipped；BUILD SUCCESS | 第三轮 Review 修订后的完整后端新鲜回归；条件真实依赖测试默认跳过 |
| 2026-09-04 11:00 CST | `cd frontend/fashion-client; npm run build` | BUILD PASS；1728 modules | Vite 6.4.2 生产构建通过；仅有既有大 chunk 提示 |
| 2026-09-04 | 第三轮独立只读实现 Review | PASS；P0=0、P1=0、P2=0、P3=0 | 第二轮的唯一键夹具、DB 时间边界和更新线程栅栏 findings 全部清零；未修改文件 |
| 2026-09-04 11:02 CST | `git diff --check`、冲突标记、限定 B7 敏感信息扫描、暂存区/分支检查 | PASS | 无 whitespace error、无冲突标记、暂存区为空；唯一敏感词命中是异常脱敏负测中的显式假字符串 `password=secret`，无真实凭据/私钥/Access Key；分支仍为 `codex/b7-coupon-review-integrity` |
| 2026-09-04 11:03 CST | 主工作区保护复核 | PASS | 主工作区仍在 `codex/b1-payment-trust-boundary`；三项用户修改和未跟踪 `docs/prototypes/` 均保持存在，未暂存、未混入 B7 |
| 2026-09-04 11:03 CST | localhost TCP 探测 | BLOCKED | `127.0.0.1:3306=false`、`127.0.0.1:6379=false`；当前 MySQL/Redis 隧道均未连接，未执行任何远端写入 |
| 2026-09-04 11:06 CST | 重连后 localhost 探测与 `B7CouponRedisNonInterferenceIntegrationTest` 安全预检 | BLOCKED；1 test setup error；BUILD FAILURE | Redis `127.0.0.1:6379` 已可达，但真实版本仍不满足 7.0.x，测试在版本门禁处、任何测试写入之前停止；MySQL `127.0.0.1:3306` 仍不可达，未创建 schema、未执行迁移 |
| 2026-09-04 11:09 CST | 启动本机 Windows `MySQL` 服务并探测 | PASS | 管理员启动后服务为 `Running`，`127.0.0.1:3306=true`；未修改数据库配置或业务数据 |
| 2026-09-04 11:11 CST | `B7CouponReviewMysqlIntegrationTest,B7ReviewMigrationMysqlIntegrationTest` | RED；22 tests，1 failure，迁移 4/4 PASS | 真实 MySQL 暴露测试夹具自相矛盾：预置一张 `status=3` 券，却错误断言全表该状态数量为 0；产品状态保持行为本身正确 |
| 2026-09-04 11:13 CST | `B7CouponReviewMysqlIntegrationTest#holderAndFixedWindowStateMatrixFailsClosed` | GREEN；1/1；BUILD SUCCESS | 最小修订为逐张断言 7 张券状态保持 `{0,1,2,3,0,0,0}`，未修改产品代码 |
| 2026-09-04 11:13 CST | `B7CouponReviewMysqlIntegrationTest,B7ReviewMigrationMysqlIntegrationTest` | GREEN；22/22；BUILD SUCCESS | 真实 MySQL 8：事务/锁/DB 时间/状态/评价 18/18，迁移 clean/repeat/错误定义/脏数据 4/4；隔离 schema 已清理 |
| 2026-09-04 11:14 CST | 测试断言修订增量独立只读 Review | PASS；P0=0、P1=0、P2=0、P3=0 | 确认逐主键状态保持断言比原全表计数更精确，未削弱失败关闭证明；无产品代码变化 |
| 2026-09-04 11:50 CST | Redis 依赖与忽略配置安全预检 | PASS | `127.0.0.1:36379` PONG；Redis 7.0.15；DB15 `DBSIZE=0`；本地 `application-test.yml` 被 Git 忽略且未进入 status |
| 2026-09-04 11:51 CST | `B7CouponRedisNonInterferenceIntegrationTest` | 2/2 PASS；BUILD SUCCESS | 真实 MySQL 隔离 schema + Redis 7.0.15：成功提交和触发器回滚均满足库存/订单/明细及秒杀哨兵断言；精确测试键与 schema 已清理 |
| 2026-09-04 11:52 CST | B7 最终聚焦测试 | 52/52 PASS；BUILD SUCCESS | 优惠、订单、评价、路由、安全异常和迁移静态合约新鲜通过 |
| 2026-09-04 11:52 CST | `cd frontend/fashion-client; npm run build` | BUILD PASS；1728 modules | Vite 6.4.2 生产构建通过；仅有既有大 chunk 提示 |
| 2026-09-04 11:53 CST | `cd backend; mvn test` | 453 tests；0 failures；0 errors；117 skipped；BUILD SUCCESS | 四模块完整后端新鲜回归；条件真实依赖测试已另以显式命令通过，不重复计入默认套件 |
| 2026-09-04 11:54 CST | 最终 Git/安全/清理检查 | PASS | `git diff --check` 无 whitespace error；暂存区为空；无冲突标记、私钥、Access Key 或本轮测试凭据命中；Redis DB15 清理后 `DBSIZE=0`；主工作区用户修改保持原样 |
| 2026-09-04 12:03 CST | `cd frontend/fashion-client; npm run build` | BUILD PASS；1728 modules | B7 远程交付前新鲜生产构建；仅有既有大 chunk 提示 |
| 2026-09-04 12:05 CST | `mvn -pl fashion-server -am -Db7.integration=true ... test` | 24/24 PASS；BUILD SUCCESS | 真实 MySQL 事务/评价 18、迁移 4、真实 MySQL+Redis 7.0.15 联合测试 2；使用 loopback、隔离 schema 与专用 DB15，测试状态已清理 |
| 2026-09-04 12:06 CST | `cd backend; mvn test` | 453 tests；0 failures；0 errors；117 skipped；BUILD SUCCESS | B7 远程交付前四模块完整后端新鲜回归；条件真实依赖测试已由上一行显式执行 |
| 2026-09-04 12:07 CST | 远端基线与提交前 Git/安全检查 | PASS | `Final-StandMarket/master` 与 HEAD 均为 `657286e6`；0 ahead/0 behind；`git diff --check` 通过；暂存区为空；B7 测试凭据、私钥和 Access Key 无 diff 命中；忽略配置未进入 Git |

## Residual gates

- B7 本地验收无剩余阻塞；第一、二轮 findings 已修订，第三轮及最新增量独立 Review 均为 P0/P1/P2/P3=0。
- 聊天中出现过测试 Redis/RabbitMQ 凭据；它们未进入 Git，但测试容器在再次使用前应销毁或轮换随机凭据。
- B0-AC6、B8-B11、生产维护窗口、版本切换、迁移和部署仍是阶段/发布门禁，不属于 B7 本地完成声明。

## Local delivery summary

B7 已本地验证：独立 Review P0/P1/P2/P3=0，真实 MySQL 22/22、真实 MySQL+Redis 2/2、聚焦 52/52、完整后端 453/453（117 条条件测试跳过）和用户端生产构建均通过；Git/敏感信息/清理检查通过。未 commit、push、创建 PR、迁移生产库或部署；不得据此宣称可生产发布。
