# B8 商品缓存版本化与 ES 可恢复同步 · Implementation Review

> Status: PASS（P0=0、P1=0、P2=0）
> Scope: Issue #20、confirmed Design、B8 implementation diff、tests and evidence
> Reviewer: Turing（独立只读 agent）

## Review gate

- 每轮记录 P0/P1/P2/P3、具体 finding、关闭证据和 reviewer 文件修改数。
- P0/P1/P2 全为 0 前不得标记“本地已验证”，不得进入远程交付。
- Review 结论不替代最终 fresh verification。

## Review rounds

### Round 1 — 2026-09-04

结论：FAIL。P0=0、P1=5、P2=5、P3=1；reviewer 文件修改数 0。

| 等级 | Finding | 处理状态与证据 |
| --- | --- | --- |
| P1 | 缓存锁释放异常可能越过读接口，异步 executor 拒绝时可能遗留锁 | 已关闭：`safeRelease` 吞并计数释放故障；提交拒绝同步释放 token；新增两条行为测试，6/6 通过 |
| P1 | ES 外呼缺少显式超时；外呼前没有再次确认 task/revision lease 当前所有权；对账批次可能超过 lease | 已关闭：配置显式 connect/socket/request timeout；repository owner/current-revision fence 在 ES 调用前二次校验；启动校验批次预算严格小于 lease |
| P1 | 迁移对列、索引、CHECK、generated expression、engine/collation 的 exact signature 与中断边界验证不足 | 已关闭：四表完整元数据签名、正向空前缀/逆序/非空前缀、错误定义及脏数据门禁；真实 MySQL 11/11 |
| P1 | 完整 schema 缺少 singleton 行时可能被静默补种 | 已关闭：完整 schema 强制 singleton 数量恰为 1；非空 revision 场景真实测试验证拒绝 |
| P1 | 对账 status 只能查看 active run 且暴露持久实体内部字段 | 已关闭：active-or-latest 查询；返回专用脱敏 DTO；序列化测试证明不含 cursor/lockedBy |
| P2 | status 端点仍可能暴露 cursor/lease owner | 已与上一项共同关闭：HTTP DTO 物理上无这些字段，序列化回归测试覆盖 |
| P2 | 缺少关键降级、重建、重试/终态、漂移指标 | 已关闭：新增进程内只读 counters/snapshot 与管理查询；分支行为测试覆盖 |
| P2 | ES 故障分类、端到端恢复/对账和迁移矩阵不足 | 已关闭：受控 HTTP transport 测试 timeout/429/503/400/401/403；真实 ES 乱序/重上架；真实 MySQL→task→ES→漂移→对账→修复链路 |
| P2 | evidence 仍为计划阶段内容 | 已更新为实际 TDD、真实依赖和完整回归结果；最终 fresh gates 待复审后补齐 |
| P2 | 远程 master 已合入 B9，共享 `application.yml` 与 workpack 索引尚未验证兼容 | 已关闭：保留 B9 agent 配置与归档行；detached `0512000` + B8 临时副本完整回归 572/0/0/147，真实依赖 33/33 |
| P3 | executor rejection 的锁泄漏路径 | 已与 P1 第一项共同关闭 |

### Round 2 — 2026-09-04

结论：FAIL。P0=0、P1=2、P2=3、P3=0；reviewer 文件修改数 0。

| 等级 | Finding | 处理状态与证据 |
| --- | --- | --- |
| P1 | ES 初始 ownership fence 同时要求 current revision，旧任务在进入 supersede 分支前返回并持有租约，阻塞当前任务 | 已修：拆分 `ownsDeliveryLease` 与 `ownsCurrentDelivery`；旧任务先确认 owner，再比较权威 revision 并 `SUPERSEDED`；真实 MySQL 验证当前版本可立即 claim |
| P1 | 迁移 exact signature 使用 token `LIKE`，可接受 `OR 1=1`、额外枚举值、错误 default/generated expression | 已修：CHECK/generated expression 改为 MySQL 规范化后的 canonical equality；列 default/extra 逐列精确校验；真实 MySQL 14/14 覆盖弱化、扩域和错误定义 |
| P2 | 缺少 task 按 target 的 success/retry/terminal 和 reconcile success/retry/terminal 指标 | 已修：仅 owner CAS 成功后计数；stale owner 不计数；repository 行为测试覆盖 |
| P2 | 缺少真实双 worker、lease expiry、旧 owner 与 current supersede 的集成证据 | 已修：真实 Spring proxy/MyBatis/MySQL 双线程只产生一个 owner；过期接管 token/attempt/claim 可观测；旧 owner completion 无效；旧版本立即让位 |
| P2 | 缺少事务各写入阶段故障注入及真实 MySQL 8.0.16 以下版本门禁证据，且 evidence 有过度结论 | 部分关闭：version/revision/Redis task/ES task 四阶段真实事务回滚全绿；evidence 已纠正。当前主机无 Docker CLI，无法启动真实 8.0.15，按流程记录 `tooling_blocked`，未用 Mock 冒充证据 |

### Round 3 — 2026-09-04

结论：FAIL。P0=0、P1=0、P2=1、P3=0；reviewer 文件修改数 0。

| 等级 | Finding | 处理状态与证据 |
| --- | --- | --- |
| P2 | “真实双 worker”仅竞争同一 task row，未证明同商品不同版本 task 经 `SKIP LOCKED` 后竞争同一 revision lease | 已修：保存后再更新制造 101/102 两条 ES task；双线程并发 claim 后精确断言 `PROCESSING:1/PENDING:1` 与 `attempt 1:1/0:1`，未获 lease 的 task 不增加 attempt；owner 完成/让位后另一 task 可立即 claim |

### Round 4 — 2026-09-04

结论：PASS。P0=0、P1=0、P2=0、P3=1；reviewer 文件修改数 0。

- Round 3 唯一 P2 已关闭：同商品 101/102 两条 ES task、双 Spring 事务代理、`REQUIRES_NEW`、revision lease 单 owner、loser attempt 不增加以及释放后立即 claim 均有真实 MySQL 证据。
- P3：`evidence.md` 的 review 状态与最终矩阵数量过时；已在最终归档中更正为 Round 4 PASS 和 33/33。

## Final verdict

PASS：独立实现 Review 最终 P0=0、P1=0、P2=0；P3 文档数量已更正。最终 fresh verification 全绿；MySQL 8.0.15 实例验证作为已披露 `tooling_blocked` 保留，不宣称通过。
