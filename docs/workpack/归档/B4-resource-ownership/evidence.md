# B4-resource-ownership · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B4-AC1 | `WebconfigPublicRouteContractTest` 4/4：真实注册、短信、登录、商品、分类匿名通过；旧短信路径、私有用户路径和匿名上传被拒绝；反射绑定真实 Controller 映射 | PASS |
| B4-AC2 | `AddressBookServiceImplTest` 6/6：无用户 `1` 回退，未登录不调用 Mapper，请求体 `userId` 被当前用户覆盖，写入检查影响行数 | PASS |
| B4-AC3 | 地址 Mapper 合约 3/3；显式 MySQL 门禁用生产 XML 验证 `id + user_id`；故障触发器让默认地址第二步失败后，真实 Spring 代理回滚第一步重置 | PASS |
| B4-AC4 | `OrderAddressOwnershipTest` 3/3；显式 MySQL 门禁证明越权地址在真实订单事务内先失败且订单零新增，合法地址快照及空地址语义由单测覆盖 | PASS |
| B4-AC5 | `UserResourceOwnershipContractTest` 6/6、Mapper 合约 4/4；MySQL 双用户订单和普通支付流水查询隔离；退款、AI 和用户 Controller 均改用当前用户专用入口 | PASS |
| B4-AC6 | 评价本人列表由 Service 读取 `BaseContext`，请求评价强制覆盖用户；订单评价检查 SQL 使用 `order_id + user_id`，单测及 MySQL 双用户验证通过 | PASS |
| B4-AC7 | 秒杀列表/券/详情/取消从服务端当前用户取 ID；生产 SQL 的跨用户取消零写入、本人首次取消一行、重复取消零行 | PASS |
| B4-AC8 | B4 聚焦回归 42/42；完整后端 196 tests、0 failures、0 errors；`git diff --check` 通过，限定源码和敏感字面量人工扫描无 B4 泄漏；独立实现二次复审 P0/P1/P2/P3 均为 0 | PASS |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-01 | 路由和地址 Mapper 首轮 RED | exit 1；5 tests，4 failures | 证明真实注册被拦截、错误旧路径被放行及 owner SQL 缺失 |
| 2026-09-01 | 路由和地址 Mapper GREEN | exit 0；5/5 | 精确公开路由及地址 owner SQL 生效 |
| 2026-09-01 | 地址 Service RED → GREEN | RED 6/6 failed；GREEN 6/6 passed | 证明并关闭默认用户、裸读写、零行未失败及默认更新顺序问题 |
| 2026-09-01 | 下单地址 RED → GREEN | RED 3 tests / 2 failures；GREEN 3/3 | 越权地址阻断、合法快照和空地址原语义 |
| 2026-09-01 | 交易资源合约 RED → GREEN | Mapper RED 3/3 failed；Service RED 6/6 failed；GREEN 9/9 | 普通订单、支付、退款、评价及秒杀 owner 边界 |
| 2026-09-01 | 裸地址/评价 Mapper 入口 RED → GREEN | RED 7 tests / 2 failures；GREEN 7/7 | 删除未使用且可绕过 owner 条件的裸 Mapper 方法和 XML statement |
| 2026-09-01 14:51 | `mvn -q -pl fashion-server -Db4.mysql.integration=true ... -Dtest=ResourceOwnershipSpringMysqlIntegrationTest test` | exit 0；4/4 | 本机 MySQL 随机临时 schema；真实 Spring 代理、生产 MyBatis XML、双用户隔离和事务回滚；测试后自动删库 |
| 2026-09-01 15:00 | 独立审查异常脱敏 RED | exit 1；7 tests，3 failures | 证明秒杀券、详情、取消公开响应拼接底层 SQL/驱动异常消息；真实 Controller 映射合约已通过 |
| 2026-09-01 15:01 | 独立审查修订 GREEN | exit 0；7/7 | 三个固定公开错误及真实用户/商品/分类 Controller 映射合约通过 |
| 2026-09-01 15:02 | B4 聚焦 Maven 回归 | exit 0；42/42 | 含 B4 新测试及 Payment/Refund/Order 相关回归 |
| 2026-09-01 15:02 | 显式 B4 MySQL 门禁复跑 | exit 0；4/4 | 审查修订后的新鲜生产 Mapper/事务证据 |
| 2026-09-01 15:03 | `mvn -q test`（`backend/`） | exit 0；196 tests，0 failures，0 errors，42 skipped | 跳过项为属性门控的显式集成测试；B4 MySQL 门禁已单独显式执行 |
| 2026-09-01 | `git diff --check` | exit 0 | 仅出现 Git 的 LF/CRLF 工作区提示，无 whitespace error |
| 2026-09-01 | 限定 owner/裸资源/默认用户源码扫描 | reviewed | 命中仅为管理端、支付通知和其他可信内部通用秒杀读取；用户入口未命中裸订单/支付/秒杀读取 |

## Not run or blocked

- 本地未安装 `gitleaks`；GitHub PR #13 首轮 Gitleaks 已通过。
- 产品提交：`da4eedc`（`fix(auth): 下沉用户资源归属校验`）。
- 尚未执行合并、生产迁移或部署。
- B0-AC6 与 B11 继续阻塞生产发布，不阻塞 B4 本地开发。

## Remote delivery

- 产品提交：`da4eedc6402cf83d9c3a9a1fcebff7052a028f74`（`fix(auth): 下沉用户资源归属校验`）。
- 本地证据提交：`cef491ae3dff85c0eaaa49009509cdb049dc56c7`（`docs(workpack): 记录 B4 本地验证证据`）。
- 功能分支：`codex/b4-resource-ownership`，非强推推送到 GitHub；远端提交与本地一致。
- Pull Request：[PR #13](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/13)，目标 `master`，关联并计划关闭 Issue #12。
- 首轮 GitHub checks（目标提交 `cef491a`）5/5 通过：Java backend、Frontend build (fashion-admin)、Frontend build (fashion-client)、Python agent、Gitleaks。
- 本次提交仅同步归档和远程证据；推送后必须等待最终文档提交的 GitHub checks 再次全绿，方可合并。
