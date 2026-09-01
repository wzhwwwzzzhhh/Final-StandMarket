# B5-seckill-state-inventory · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B5-AC1 | 专用支付 CAS、纯 `pay_time` 更新、任意状态写口移除；聚焦单测与真实支付/取消竞态 | PASS |
| B5-AC2 | `REQUIRES_NEW` 取消事务、CAS 赢家一次回补；真实 MySQL 重复取消与回滚 | PASS |
| B5-AC3 | Lua 写前校验、成功/重放/并发/缺 key/双 key wrong-type/非法数值与上溢真实 Redis 测试 | PASS |
| B5-AC4 | 内层独立提交、外层回滚不反转取消、Redis 失败映射 `REDIS_RECONCILIATION_PENDING` | PASS |
| B5-AC5 | B5 迁移首次/重跑/部分与错误定义/脏状态、clean DDL+dump、重新参与和元数据等价 | PASS |
| B5-AC6 | 用户端真实取消、管理端待支付边界、无随机支付契约；双前端生产构建 | PASS |
| B5-AC7 | 命名常量 30 分钟、学习文档同步、B11 旧队列切换门禁 | PASS |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-01 | planning only | not run | Design/workpack drafting; no product code changed |
| 2026-09-01 | user confirmation | confirmed | B5 Design and workpack plan confirmed; implementation gate opened |
| 2026-09-01 16:05 | `mvn -pl fashion-server -Dtest=SeckillOrderStateContractTest test` | RED: 3 tests / 3 failures / 0 errors | 证明支付时间仍递增状态、缺少可信取消 CAS、缺少独立 `REQUIRES_NEW` 取消事务 Bean |
| 2026-09-01 16:07 | `mvn -pl fashion-server -Dtest=SeckillOrderStateContractTest test` | GREEN: 3 tests / 0 failures / 0 errors | 专用支付/取消 CAS、纯时间更新与独立事务结构合约通过 |
| 2026-09-01 16:08 | `mvn -pl fashion-server -Dtest=SeckillOrderServiceImplB5PaymentTest test` | RED: 2 tests / 2 failures / 0 errors | 证明确认支付仍先读订单且未调用/检查单条 `markPaid` CAS |
| 2026-09-01 16:09 | `mvn -pl fashion-server '-Dtest=SeckillOrderServiceImplB5PaymentTest,SeckillOrderStateContractTest' test` | GREEN: 5 tests / 0 failures / 0 errors | 支付单条 CAS、零行竞态和 Mapper 状态合约通过 |
| 2026-09-01 16:10 | `mvn -pl fashion-server -Dtest=SeckillCancellationApiContractTest test` | RED: 3 tests / 3 failures / 0 errors | 证明缺少稳定取消 DTO、提交后 Redis 编排和用户/管理/超时统一入口 |
| 2026-09-01 16:15 | Slice 1 focused tests | GREEN: 11 tests / 0 failures / 0 errors | 取消 DTO/编排、用户异常脱敏、支付 CAS 与 Mapper 合约合并通过；此前一次 server-only 构建因旧本地 pojo 缺 DTO 而失败，`-am -DskipTests install` 更新模块后重跑 |
| 2026-09-01 16:18 | `mvn -pl fashion-server '-Dtest=SeckillRollbackLuaContractTest,SeckillB5CrossLayerContractTest' test` | RED: 6 tests / 6 failures / 0 errors | 证明 Lua、30 分钟 TTL、真实用户取消、管理端待支付边界和 B5 schema 迁移均缺失 |
| 2026-09-01 16:24 | 同上 | GREEN: 6 tests / 0 failures / 0 errors | Lua、TTL、双前端与 schema 静态契约通过 |
| 2026-09-01 16:26 | `mvn -pl fashion-server '-Dtest=SeckillCancellationTransactionTest,SeckillOrderCancellationOrchestrationTest' test` | GREEN: 7 tests / 0 failures / 0 errors | 数据库 CAS/回补与 Redis 成功、未应用、异常、冲突编排通过 |
| 2026-09-01 16:31 | 首轮显式 B5 MySQL/Redis 集成门禁 | MySQL 9 tests PASS；Redis 环境连接失败 | 随机 MySQL schema 已自动清理；本机 Redis 当时未启动，不把环境失败冒充测试 PASS |
| 2026-09-01 16:33 | `SeckillRollbackRedisIntegrationTest`（临时本机 Redis） | GREEN: 5 tests / 0 failures / 0 errors | 临时 Redis 认证与已忽略本地配置对齐，专用 key 自动清理，进程随后停止；未输出或提交凭据 |
| 2026-09-01 16:35 | `mvn -pl fashion-server '-Dtest=SeckillOrderStateContractTest' test` | RED: 3 tests / 1 failure / 0 errors | 发现仍存在可传任意目标状态的通用秒杀写口 |
| 2026-09-01 16:37 | `mvn -pl fashion-server '-Dtest=SeckillOrderStateContractTest,SeckillOrderServiceImplB5PaymentTest' test` | GREEN: 5 tests / 0 failures / 0 errors | 通用状态写口移除，支付/取消只保留专用迁移 |
| 2026-09-01 16:39 | 全部 B5 聚焦单元/合约测试 | GREEN: 24 tests / 0 failures / 0 errors | 状态、事务、API、Lua、前端与迁移聚焦回归通过 |
| 2026-09-01 16:39 | `mvn test` | 231 tests / 1 error | 产品断言无失败；旧 B4 测试桩未注入新增事务 Bean，随后适配测试 |
| 2026-09-01 16:40 | `UserResourceOwnershipContractTest` | GREEN: 6 tests / 0 failures / 0 errors | 资源归属回归改为验证当前用户 ID 传入统一取消事务 |
| 2026-09-01 16:41 | `mvn test` | GREEN: 231 tests / 0 failures / 0 errors / 56 skipped | 全模块后端回归通过；显式集成类在普通命令中按条件跳过，另有独立真实门禁 |
| 2026-09-01 16:44 | 用户端 `npm ci` + `npm run build` | PASS | 1728 modules；只有既有 chunk-size warning；项目无 test/lint/typecheck 脚本，不声称通过 |
| 2026-09-01 16:44 | 管理端 `npm ci` + `npm run build` | PASS | 2290 modules；只有既有 chunk-size warning；项目无 test/lint/typecheck 脚本，不声称通过 |
| 2026-09-01 16:45 | 显式 B5 MySQL/Redis 集成门禁最终复跑 | GREEN: 15 tests / 0 failures / 0 errors | Spring/MyBatis/MySQL 5、迁移 5、真实 Redis 5；隔离 schema/key 自动清理，临时 Redis 随后停止 |
| 2026-09-01 16:46 | `git diff --check` 与限定范围/残留扫描 | PASS | 无 whitespace error；无秒杀状态递增、900000 TTL、随机支付或学习文档 15 分钟残留；命中的其他 `updateStatus` 属于普通订单/评价既有代码 |
| 2026-09-01 16:52 | `SeckillOrderStateContractTest` 复审修订 RED | 3 tests / 1 failure / 0 errors | 证明允许多条取消历史后，失效的 `selectByUserIdAndCouponId` 单行查询仍存在 |
| 2026-09-01 16:53 | `SeckillStateMigrationMysqlIntegrationTest` 复审修订 RED | 6 tests / 1 failure / 0 errors | 真实 MySQL 证明原迁移会接受 VIRTUAL `active_marker` |
| 2026-09-01 16:55 | Mapper 契约与真实 MySQL 迁移 GREEN | Mapper 3/3；迁移 6/6 | 移除失效单行 Mapper；普通列、VIRTUAL、非 STORED/错误形状和 `NOT ENFORCED` 均由严格元数据门禁拒绝 |
| 2026-09-01 16:56 | `SeckillStateSpringMysqlIntegrationTest` | GREEN: 6 tests / 0 failures / 0 errors | Redis 回调当下无线程活动事务；独立 JDBC 已看到 `status=3` 与库存回补提交事实 |
| 2026-09-01 16:57 | `mvn test` | GREEN: 234 tests / 0 failures / 0 errors / 59 skipped | 全模块后端最终回归通过；条件跳过的真实集成类已由显式门禁执行 |
| 2026-09-01 16:58 | 独立实现复审第二轮 | PASS；P0/P1/P2/P3 均为 0 | 首轮 P1=1、P2=2 全部关闭；复审只读，未修改文件 |
| 2026-09-01 16:58 | `git diff --check`、残留与高置信敏感信息扫描 | PASS | 仅 Git LF→CRLF 提示；无 whitespace error、产品残留或高置信凭据命中 |

## Not run or blocked

- 生产迁移、RabbitMQ 队列操作、commit、push、PR、CI、merge 与部署均未执行。
- B6, B0-AC6, B10 and B11 remain production release gates.
