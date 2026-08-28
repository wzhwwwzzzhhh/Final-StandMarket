# B1-payment-trust-boundary · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B1-AC1 | 有效签名/应用/金额/类型/关联/状态矩阵单测；管理端支付旁路测试 | pass |
| B1-AC2 | 一致/冲突重复单测；MySQL 8 双回调仅一次订单、支付、券迁移 | pass |
| B1-AC3 | 订单行锁创建单测；MySQL 8 八线程 Service 创建复用；初读为空后外部事务提交触发唯一冲突并由锁定当前读收敛；并发直写一胜一败；生成列唯一索引 | pass |
| B1-AC4 | `PaymentEndpointContractTest` 证明普通、用户秒杀、管理端普通确认映射及随机 service 已移除 | pass |
| B1-AC5 | `PaymentControllerTest` 证明本人范围和 `order_type=0` 查询；创建 Service 校验当前用户 | pass |
| B1-AC6 | 同步回跳 Controller 测试验证只读；前端保留完整支付宝签名参数且不混入本站 `orderId` | pass |
| B1-AC7 | MySQL 8 双回调、事务回滚、回调/取消竞争单赢家无死锁；取消 CAS 单测 | pass |
| B1-AC8 | `RestConfigTest` 验证 3000/30000 ms 默认值、正值与外部覆盖 | pass |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-08-28 | `gh issue view 6 --repo wzhwwwzzzhhh/Final-StandMarket --json ...` | Issue OPEN, `BLOCKED BY #4` | 确认唯一需求源、范围和执行禁令 |
| 2026-08-28 | `gh issue view 4 --repo wzhwwwzzzhhh/Final-StandMarket --json ...` | Issue OPEN | B1 产品实现前置依赖未满足 |
| 2026-08-28 | `git status --short` | 10 个 B1 产品文件及 3 个范围外工作流文件已修改 | 未 reset/stash/改写任何存量修改 |
| 2026-08-28 | `git diff --stat -- <10 B1 files>` | `103 insertions(+), 59 deletions(-)` | 现有 B1 存量修改基线 |
| 2026-08-28 | `rg` 检索支付模拟、确认接口、超时和测试 | 找到普通/秒杀随机模拟入口、管理端确认入口、无超时及无支付测试 | 用于计划缺口盘点，不表示验收通过 |
| 2026-08-28 | `git diff --check` | exit 0，仅工作流文件换行提示 | 仅为计划前工作树检查，不表示产品验证通过 |
| 2026-08-28 | 独立只读 Design/plan Review（round 1） | FAIL：5 P1、2 P2 | 已修订管理旁路、取消竞态、支付宝响应矩阵、唯一冲突/DDL 语义、真实数据库测试和精确重复一致性；等待复审 |
| 2026-08-28 | 独立只读 Design/plan Review（round 2） | FAIL：1 P1、2 P2 | 已补订单后续发货/完成/退款的一致重复语义、部分 schema 拒绝测试和未支付订单管理端推进限制；等待复审 |
| 2026-08-28 | 独立只读 Design/plan Review（round 3） | PASS：P0-P3 均无 | Design/plan 可提交用户确认；不代表产品实现 Review PASS |
| 2026-08-28 | 用户确认 B1 Design 与 workpack plan | confirmed | Design 标记 `已确认`；Issue #4 仍为 OPEN，产品实现继续阻塞 |
| 2026-08-28 | 项目维护者确认 B1 本地开发阻塞解除 | confirmed | Issue #6 已写明 B0-AC6 不再阻塞 B1-B10；创建 `codex/b1-payment-trust-boundary` 开始执行 |
| 2026-08-28 | `mvn -pl fashion-server -am '-Dtest=PaymentEndpointContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`（RED） | 4 tests / 4 failures | 证明普通/秒杀模拟入口、管理端人工确认及完整 Orders 状态载荷仍存在 |
| 2026-08-28 | 同一聚焦命令（GREEN） | 4 tests / 0 failures，BUILD SUCCESS | Slice 1 后端不可信入口契约收口 |
| 2026-08-28 | `frontend/fashion-client: npm run build` | exit 0 | 用户端生产构建通过；保留大资源/chunk 警告 |
| 2026-08-28 | `frontend/fashion-admin: npm run build` | exit 0 | 管理端生产构建通过；保留大 chunk 警告 |
| 2026-08-28 | `mvn ... -Dtest=PayNotifyControllerTest,OrderServiceImplPaymentTest ... test`（RED） | 6 tests / 4 failures / 0 errors | 证明 `TRADE_FINISHED`、订单持久化金额、异常响应和冲突 `trade_no` 缺口 |
| 2026-08-28 | 同一聚焦命令（GREEN） | 6 tests / 0 failures，BUILD SUCCESS | 通知响应矩阵和锁内精确重复收口 |
| 2026-08-28 | `mvn ... -Dtest=OrderCancellationCasTest ... test`（RED） | 3 tests / 2 failures / 0 errors | 证明用户/超时取消仍会在 CAS 失败后释放优惠券 |
| 2026-08-28 | 同一聚焦命令（GREEN） | 3 tests / 0 failures，BUILD SUCCESS | 取消仅在待支付 CAS 成功后释放优惠券 |
| 2026-08-28 | `mvn ... -Dtest=PaymentCreationContractTest,RestConfigTest ... test`（RED） | 3 tests / 1 failure / 2 errors | 证明通用金额创建契约和有限 HTTP 超时尚未收口 |
| 2026-08-28 | 同一聚焦命令（GREEN） | 3 tests / 0 failures，BUILD SUCCESS | 订单范围支付宝创建契约和可覆盖超时配置完成 |
| 2026-08-28 | `mvn ... -Dtest=PaymentServiceImplCreationTest,PaymentActiveUniqueSqlTest ... test`（RED） | 5 tests / 1 missing-script error | Service 行为测试已绿，证明已有库增量 SQL 尚缺失 |
| 2026-08-28 | 同一聚焦命令（GREEN） | 5 tests / 0 failures，BUILD SUCCESS | 活动流水创建行为和 SQL 安全守卫通过 |
| 2026-08-28 | 独立实现审查（round 1） | FAIL：2 P1、2 P2 | 要求真实触发唯一冲突当前读、精确校验迁移表达式、稳定 latest 排序并修正文档；均已修复并补 RED/GREEN |
| 2026-08-28 | `mvn ... -Db1.mysql.integration=true -Dtest=PaymentCreationContractTest,PaymentMysqlIntegrationTest ... test`（RED） | 11 tests / 3 failures / 0 errors | 新增断言分别证明初读/冲突读未分离、错误生成列表达式被误判为已迁移、同秒排序不稳定 |
| 2026-08-28 | `mvn ... -Db1.mysql.integration=true -Dtest=PaymentCreationContractTest,PaymentServiceImplCreationTest,PaymentMysqlIntegrationTest,PaymentActiveUniqueSqlTest ... test`（GREEN） | 18 tests / 0 failures / 0 errors / 0 skipped | 使用本机 MySQL 8 隔离临时 schema；9 个数据库测试覆盖迁移冲突/重试/no-op/部分及错误 schema、稳定排序、八线程创建、外部赢家冲突当前读、并发直写、双回调、回滚和回调取消竞争；schema 已删除 |
| 2026-08-28 | B1 非 MySQL Java 测试集合（由最终全量报告汇总） | 44 tests / 0 failures / 0 errors | 通知 9、Controller/管理边界 10、回调状态 11、取消 3、创建 8、SQL 1、超时 2；MySQL 9 另有显式门禁 |
| 2026-08-28 | `backend: mvn test`（最终新鲜运行） | 59 tests / 0 failures / 0 errors / 9 skipped，BUILD SUCCESS | 默认全量包含既有 Agent 6 项，并跳过需显式属性的 9 个 MySQL 隔离库测试；同一代码已在上一行独立启用通过。首次与独立审查并发写同一 Maven `target` 时发生一次瞬态 testCompile 缺类，审查任务结束后同命令独立重跑通过 |
| 2026-08-28 | `frontend/fashion-client: npm run build`（最终新鲜运行） | exit 0 | 支付结果页完整转发支付宝验签参数后的生产构建通过；保留既有大资源/chunk 警告 |
| 2026-08-28 | `frontend/fashion-admin: npm run build`（最终新鲜运行） | exit 0 | 生产构建通过；保留既有大 chunk 警告 |
| 2026-08-28 | `git diff --check` | exit 0 | 无空白错误；仅 Git 的 LF→CRLF 工作树提示 |
| 2026-08-28 | 移除路径 `rg` + B1 新文件敏感字面量扫描 | pass | 普通订单、用户秒杀模拟支付及管理端普通确认入口由契约测试证明已移除；命中的管理端秒杀确认属于保留范围；新增测试、SQL 未发现硬编码 password/secret/private key/access key |
| 2026-08-28 | 独立实现审查（round 2） | FAIL：1 P1（仅证据门禁） | 代码问题全部关闭；要求把修复后的 9 项 MySQL、59 项全量测试和已完成验证准确回填本文档 |
| 2026-08-28 | 独立实现审查（round 3） | PASS：P0-P3 均无 | 独立复核确认代码问题、证据计数、范围边界与 residual risks 均准确，可本地交付 |
| 2026-08-28 | `gh api repos/wzhwwwzzzhhh/Final-StandMarket/commits/master --jq .sha` | `94627c5...` | 在两次 Git HTTPS fetch 超时后，通过 GitHub API 新鲜确认远端 `master` 与本地远端引用一致 |
| 2026-08-28 | 在隔离 worktree 合并 `Final-StandMarket/master` | 2 个冲突已解决 | `PayNotifyController` 保留 B0 更严格的无业务标识验签失败日志与 B1 校验逻辑；workpack 索引同时保留 B0/B1；当前 checkout 的 3 个范围外修改未移动、未暂存 |
| 2026-08-28 | B0+B1 集成后 `backend: mvn test` | 90 tests / 0 failures / 0 errors / 9 skipped，BUILD SUCCESS | 合并基线上全量测试通过；9 个真实 MySQL 门禁按属性默认跳过 |
| 2026-08-28 | B0+B1 集成后显式 `PaymentMysqlIntegrationTest` | 9 tests / 0 failures / 0 errors / 0 skipped | 独立 worktree 首次因忽略配置不存在而 1 error；临时复制当前项目既有 `application-dev.yml` 后重跑通过，临时配置和隔离 schema 均已删除，未输出或暂存配置内容 |
| 2026-08-28 | B0+B1 集成后两端 `npm ci && npm run build` | client/admin 均 exit 0 | 新 worktree 首次因无 `node_modules` 无法找到 Vite；按锁文件安装依赖后生产构建通过，保留既有大资源/chunk 警告 |
| 2026-08-28 | B0+B1 集成独立只读复核 | PASS：P0-P3 均无 | 确认父提交、两处冲突解决、相对远端 `master` 的 45 个 B1 路径集合、敏感配置清理和验证证据均准确 |
| 2026-08-28 | [PR #7](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/7) 首轮 GitHub checks（head `ba1559d`） | 5/5 pass | Java backend、Python agent、fashion-client build、fashion-admin build、Gitleaks 全绿；未用本地结果冒充远程 CI |

## Remote delivery status

- 用户已授权 push、PR、merge；B1 本地提交 `9f2d137`、B0 集成提交 `ba1559d` 已推送并创建 PR #7，首轮 5 项 GitHub checks 全绿。workpack 已归档，归档提交仍需再次通过 GitHub checks 后才允许合并。

## Local delivery summary

三个 Slice 已实现；本地原始基线和 B0 集成基线均完成后端全量、两端生产构建与真实 MySQL 8 门禁，集成复核 PASS，PR #7 首轮 CI 全绿。workpack 已归档；当前等待归档提交的第二轮 GitHub checks 后合并。
