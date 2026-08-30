# B3-refund-state · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B3-AC1 | `RefundStateSpringMysqlIntegrationTest#approvalHasNoOrderPaymentOrInventorySideEffects` 通过；真实 Spring 代理、生产 Mapper XML 和 MySQL 前后快照证明仅 `0 -> 1` | PASS |
| B3-AC2 | `concurrentApprovalsHaveOneWinner` 与 `approvalAndRejectionRaceHasOneLegalWinner` 通过；固定 `WHERE status=0` CAS 合约测试通过 | PASS |
| B3-AC3 | `rejectionCommitsTogetherAndRollsBackTogether` 通过；正常拒绝同时提交，订单恢复零行时退款 `0 -> 3` 回滚 | PASS |
| B3-AC4 | `RefundStateMapperContractTest` 证明通用退款 update、`refund_time` 更新和库存依赖已删除；限定源码扫描仅命中既有订单取消库存回补 | PASS |
| B3-AC5 | `refundApplicationAndConfirmationRaceHasOneWinner` 通过；退款申请和确认收货均经真实代理/生产 XML，最终仅一个状态迁移成功 | PASS |
| B3-AC6 | Controller 与两端 UI 合约测试通过；管理端/用户端生产构建通过，状态 1 精确显示“已同意，等待退款处理” | PASS |
| B3-AC7 | `RefundStateMigrationSqlTest` 与 5 个 MySQL 迁移门禁测试通过：首次/合法 `1/2` 重跑、clean/upgrade 等价、历史/坏时间/坏前态阻断、单/错/非 ENFORCED/碰撞 marker 拒绝 | PASS |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-08-30 | planning only | not run | Design/workpack confirmation gate; no product code changed |
| 2026-08-30 14:22 | `mvn -pl fashion-server -Dtest=RefundServiceImplTest,RefundControllerContractTest test` | RED：4 tests / 4 failures | 证明旧实现没有申请/同意/拒绝专用 CAS，且审核同意仍返回“退款成功” |
| 2026-08-30 14:23 | `mvn -pl fashion-server -Dtest=RefundStateMapperContractTest test` | RED：3 tests / 3 failures | 证明固定目标 CAS SQL、退款状态常量和退款服务依赖边界尚不存在 |
| 2026-08-30 14:24 | `mvn -pl fashion-server -Dtest=RefundServiceImplTest test` | RED：7 tests / 7 failures | 进一步证明 CAS 零行未失败、拒绝可恢复非法前态等事务边界缺口 |
| 2026-08-30 14:26 | Slice 1 聚焦测试 | GREEN：11 tests / 0 failures / 0 errors | Service、Controller、Mapper/依赖边界通过 |
| 2026-08-30 14:27 | `mvn -pl fashion-server -Dtest=RefundUiContractTest,RefundStateMigrationSqlTest test` | RED：4 tests / 4 failures | 两端四状态文案、干净库双约束和增量迁移脚本均缺失 |
| 2026-08-30 14:29 | Slice 2 静态契约测试 | GREEN：4 tests / 0 failures / 0 errors | 两端四状态文案和双 marker 迁移静态契约通过 |
| 2026-08-30 14:31 | `RefundStateMysqlIntegrationTest` | GREEN：5 tests / 0 failures / 0 errors | 随机隔离 schema 验证迁移、重跑、元数据等价、历史阻断、marker 校验与并发单赢家 |
| 2026-08-30 14:34 | `RefundStateSpringMysqlIntegrationTest` | GREEN：5 tests / 0 failures / 0 errors | 首轮业务断言均通过但测试辅助方法存在 Boolean/Number 类型兼容错误；仅修复辅助读取后重跑全绿 |
| 2026-08-30 14:37 | 聚焦契约/单元测试合并重跑 | GREEN：15 tests / 0 failures / 0 errors | `RefundServiceImplTest`、Controller/UI、Mapper 与迁移静态契约全量聚焦重跑 |
| 2026-08-30 14:37 | 两个 MySQL 集成类合并重跑 | GREEN：10 tests / 0 failures / 0 errors | 同一命令运行直连迁移与 Spring/MyBatis 事务/并发门禁；临时 `fsm_b3_it_*` schema 已清理 |
| 2026-08-30 14:38 | 管理端、用户端 `npm ci` | exit 0 / exit 0 | 独立 worktree 原无 `node_modules`；按各自 lockfile 安装，依赖目录被 Git 忽略 |
| 2026-08-30 14:38 | 管理端、用户端 `npm run build` | exit 0 / exit 0 | 两端 Vite 生产构建成功；仅有既有大 chunk 警告 |
| 2026-08-30 14:39 | `backend/mvn test` | BUILD SUCCESS：163 tests / 0 failures / 0 errors / 38 skipped | Maven 四模块 reactor 全绿；10 个需显式本地 MySQL 开关的测试按设计在默认套件中跳过并已单独通过 |
| 2026-08-30 14:40 | `git diff --check` | PASS | 25 个变更路径；无空白错误 |
| 2026-08-30 14:40 | 变更文件敏感信息扫描与本地配置 ignore 检查 | PASS：0 hits；ignored=true | 未发现硬编码私钥、Access Key 或密码；仅引用主工作区已忽略的 `application-dev.yml` 路径 |
| 2026-08-30 14:42 | 增强后的 MySQL 迁移门禁 | GREEN：5 tests / 0 failures / 0 errors | 增加合法新状态重跑、非 ENFORCED、坏时间及非法申请前状态场景；随后审查发现 marker 用例需消除 nullable 列假阳性 |
| 2026-08-30 14:44 | marker 碰撞与 Service 契约修复前测试 | RED：8 tests / 2 failures / 0 errors | 真实暴露 `IN (0,12,3)` 被错误接受，以及 `RefundService#approve` 仍保留错误库存/订单注释 |
| 2026-08-30 14:45 | marker/接口契约修复后测试 | GREEN：10 tests / 0 failures / 0 errors | 保留 CHECK 列表分隔符；碰撞 marker 与 NOT ENFORCED 均按精确消息失败；接口注释边界测试通过 |
| 2026-08-30 14:45 | 最终聚焦契约/单元测试 | GREEN：15 tests / 0 failures / 0 errors | 最终产品文件状态下重跑全部 B3 静态、Service、Controller 与 UI 合约 |
| 2026-08-30 14:46 | 最终显式 MySQL/Spring 门禁 | GREEN：10 tests / 0 failures / 0 errors | 最终迁移 SQL、Spring 事务、生产 Mapper 与并发竞态合并重跑 |
| 2026-08-30 14:47 | 两端最终 `npm run build` | exit 0 / exit 0 | 管理端 2290 modules、用户端 1728 modules；仅既有大 chunk 警告 |
| 2026-08-30 14:47 | 与前端并行的 `mvn test` | exit 1：35 `NoClassDefFoundError` / 0 assertion failures | 广泛类加载干扰，非 B3 断言失败；该次结果不作为通过证据，随后清理并串行重跑 |
| 2026-08-30 14:48 | 串行 `mvn clean test` | BUILD SUCCESS：163 tests / 0 failures / 0 errors / 38 skipped | 四模块从干净构建产物全量通过；显式 MySQL 10 tests 已由上一行单独通过 |
| 2026-08-30 14:48 | 独立审查者全量 B3 复跑 | PASS：25 tests / 0 failures / 0 errors / 0 skipped | 独立执行 7 个 B3 测试类，包含真实 MySQL/Spring/生产 XML |
| 2026-08-30 14:49 | 最终范围、空白、敏感信息与源码限定审计 | PASS | `git diff --check` 通过，未跟踪新文件无尾随空白，27 个路径敏感模式 0 命中；源码扫描仅命中既有退款回调模板文案及订单取消的合法库存回补，B3 审核链路无命中；workpack 已移入 `归档/` |

## Not run or blocked

- 未执行生产数据库迁移、支付网关调用、部署、commit、push 或 PR；这些均不属于当前本地实现授权。
- 本项目两个前端没有 test/lint/typecheck 脚本，因此只记录实际执行的生产构建，不声称这些检查通过。
- B0-AC6 与 B11 仍是阶段/生产发布门禁，不影响 B3 本地验收，但 B3 不代表可直接生产发布。

## Local delivery summary

- B3 产品代码、测试、迁移脚本和本地验证完成；独立实现审查 PASS（P0/P1/P2/P3 均为 0）。
- 交付状态仅为“本地已验证”：没有执行生产迁移、commit、push、PR、CI、merge 或部署。
