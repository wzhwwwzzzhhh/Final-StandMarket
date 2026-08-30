# B2-order-inventory-state · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B2-AC1 | `OrderInventoryMysqlIntegrationTest` 以 10 线程争抢库存 5：恰好 5 个订单和 5 组明细成功，最终库存 0 且无负库存 | PASS |
| B2-AC2 | `OrderInventorySpringMysqlIntegrationTest` 使用真实 Spring 事务代理、生产 MyBatis XML 和 MySQL，分别在第二商品、锁券、订单插入、券绑定零行、明细插入注入失败；库存、订单、明细和券均回滚 | PASS |
| B2-AC3 | `OrderCreationInventoryTest` 与持久化合约证明金额按商品服务端价格计算，客户端 amount/activityId/couponId 不进入普通订单，秒杀字段为空/0，`original_price` 和 `stock_deducted=1` 完整 | PASS |
| B2-AC4 | MySQL 双取消仅一个成功并只回补一次；真实 Spring/MyBatis 测试证明历史 `stock_deducted=0` 订单可取消但库存不变，且不能发起支付 | PASS |
| B2-AC5 | MySQL 并发支付/取消只产生已支付或已取消一个合法终态，无双写 | PASS |
| B2-AC6 | 单元/Mapper 测试覆盖支付发起、复用、首次成功回调库存门禁及 `2→3`、本人 `3→4` CAS；历史 stock0 已支付订单同 `trade_no` 零写幂等成功、冲突拒绝、stock0 首次成功拒绝 | PASS |
| B2-AC7 | `OrderAuthorizationTest` 验证列表、取消、确认收货无登录上下文立即拒绝且 Mapper 零调用 | PASS |
| B2-AC8 | 真实 Spring/MyBatis/MySQL 混合有券/无券超时批次：前后订单通过 `REQUIRES_NEW` 独立提交，中间券释放失败订单保持待支付，补齐券事实后下一轮重试成功 | PASS |
| B2-AC9 | 真实事务测试证明券绑定、取消释放和支付核销零行分别回滚订单创建/取消/支付状态、库存与券写入 | PASS |
| B2-AC10 | MySQL 测试覆盖迁移首次、重跑、兼容部分 schema、错误列/CHECK/索引、历史履约阻断及处置后重跑；`final07` 的 orders 建表和 4 条种子实际执行；实际 Mapper `SELECT *` SQL 的 `EXPLAIN` 使用目标索引 | PASS |

## TDD observations

- 历史已支付 `stock_deducted=0` 且相同 `trade_no` 的重复通知用例先失败：`OrderServiceImplPaymentTest` 14 项中 1 failure；将库存门禁移动到已成功精确重复分支之后，14/14 GREEN。
- 历史履约迁移阻断静态合约先失败：`OrderInventoryMigrationSqlTest` 2 项中 1 failure；加入 `status IN (2,3) AND stock_deducted=0` 的 `SIGNAL SQLSTATE '45000'` 后 GREEN。
- 更早的 Slice 1/2 RED/GREEN 包括条件扣库存、重复购物车、异常数量、订单持久化字段、未登录回退、取消 CAS、超时无券查询、支付库存门禁、优惠券严格影响行数和状态专用 CAS；对应最终聚焦套件均保持 GREEN。

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-08-30 12:54 | `mvn ... -Dtest=OrderServiceImplPaymentTest ... test` | RED：14 tests / 1 failure | 精确证明历史 stock0 已支付重复通知被库存门禁提前拒绝 |
| 2026-08-30 12:56 | `mvn ... -Dtest=OrderInventoryMigrationSqlTest,OrderServiceImplPaymentTest ... test` | migration RED；payment 14/14 GREEN | 迁移阻断缺口可观察，历史回调修复生效 |
| 2026-08-30 13:02 | B2 聚焦 Service/Mapper/SQL 合约命令 | 46 tests / 0 failures / 0 errors / 0 skipped | 覆盖 15 个 B2 聚焦测试类 |
| 2026-08-30 13:02 | 显式 `OrderInventoryMysqlIntegrationTest` | 10 tests / 0 failures / 0 errors / 0 skipped | 临时 schema 首尾精确创建/删除；迁移、并发、回滚与竞态通过 |
| 2026-08-30 13:02 | 显式 `OrderInventorySpringMysqlIntegrationTest` | 9 tests / 0 failures / 0 errors / 0 skipped | AOP 代理、生产 Mapper XML、真实事务回滚及 `REQUIRES_NEW` 通过 |
| 2026-08-30 13:06 | 实际超时 Mapper SQL `EXPLAIN` | `key=idx_orders_timeout`, `rows=2`, `Extra=Using index condition; Using where; Using filesort` | `SELECT *`、相同谓词、`ORDER BY id ASC LIMIT 100`；未使用 `FORCE INDEX` |
| 2026-08-30 13:07 | `backend/mvn -q test` | exit 0；138 tests / 0 failures / 0 errors / 28 skipped | 显式环境集成测试默认受属性门禁跳过；19 个 B2 MySQL 用例已在前两行单独全绿运行 |
| 2026-08-30 | 独立实现审查 final | PASS：P0/P1/P2 均为 0 | 首轮 4 P1/2 P2、第二轮 1 P1/1 P2 全部关闭 |
| 2026-08-30 | `git diff --check` | exit 0 | 仅 Git LF→CRLF 提示，无空白错误 |
| 2026-08-30 | 限定范围和敏感信息扫描 | 0 forbidden config / 0 potential secret assignments | 未纳入 ignored datasource 配置或凭据值 |

## MySQL safety

- 测试只读取主工作树现有且受 Git ignore 保护的 `backend/fashion-server/src/main/resources/application-dev.yml`；未复制、输出或提交 datasource 值。
- 两个集成测试类仅创建符合 `^fsm_b2_it_[0-9a-f]{32}$` 的随机临时 schema，删除前重复校验并按保存的精确名称清理；所有运行均正常完成清理。
- 未执行生产 schema 迁移、外部支付网关调用或其他业务数据写入。

## Remote delivery status

- 未授权、未执行 commit、push、PR、merge 或远程设置修改。

## Local delivery summary

- B2 已在 `codex/b2-order-inventory-state` 独立 worktree 完成本地实现、真实 MySQL 验证和独立 Review PASS。
- 主工作树三项用户未提交修改和 `stage-b-ac6-gate` worktree 保持原状，未 reset、stash、暂存或混入 B2。
- B3 未完成前 B2 不可部署；生产迁移与发布仍受 B0/B3/B10/B11 门禁约束。
