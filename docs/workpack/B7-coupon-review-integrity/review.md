# B7-coupon-review-integrity · Independent review

> Verdict: PASS（最新增量独立只读 Review：P0=0、P1=0、P2=0、P3=0）

## Scope and drift

第一轮独立只读实现审查为 FAIL（P0=0、P1=2、P2=4、P3=2）。第二轮在主体修订后仍发现测试夹具与并发栅栏问题（P0=0、P1=1、P2=2、P3=0）。第三轮逐项复核修订，确认 P0/P1/P2/P3 全部清零；真实 MySQL 暴露一处自相矛盾的测试断言后，第四轮增量只读 Review 再次确认 P0/P1/P2/P3=0。最终真实依赖和本地回归证据均已补齐。

## Findings

| Priority | Round 1 finding | Resolution state |
|---|---|---|
| P1 | 通用 `BaseException` / `IllegalArgumentException` 可原样回显 | 封闭 `PublicBusinessException.Code` 目录；Controller 仅回显该类型；恶意通用异常测试与复审通过 |
| P1 | 缺少模板 X 锁跨时间边界、管理更新等待、JVM 偏移与真实状态矩阵 | 真实 MySQL 栅栏/矩阵已纳入 18/18 事务测试并通过 |
| P2 | Redis 用例使用 Mock MySQL 且无回滚路径 | 隔离 schema 的真实 MySQL + Redis 7.0.15 成功/触发器回滚联合测试 2/2 通过 |
| P2 | 隐藏评价不在同商品且未调用统计 | 同商品 status 0/1 的真实列表与统计断言通过 |
| P2 | 迁移不校验 BTREE 且 ALTER 后不复核、未比较两个基线 | ALTER 后完整签名和两个基线比较已由真实 MySQL 迁移 4/4 证明 |
| P2 | 可领取模板资格与领取契约不一致 | `valid_days > 0`、`NOW(3)`、半开结束区间及真实 Mapper 用例通过 |
| P3 | 保留未使用绕过型 Mapper | 已删除无生产调用的直接持有券 insert/lock 和旧评价写入/公开查询方法 |
| P3 | MySQL README 未登记 B7 唯一升级入口 | 已登记 `add_review_integrity.sql`，明确 `review_table.sql` 仅为初始化基线 |

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| B7-AC1 至 B7-AC10 | 最新独立 Review P0/P1/P2/P3=0；真实 MySQL 22/22、MySQL+Redis 2/2、聚焦 52/52、完整后端和用户端构建均通过 | PASS |

## Follow-up review rounds

| Round | Verdict | Findings and resolution |
|---|---|---|
| 2 | FAIL；P0=0、P1=1、P2=2、P3=0 | 隐藏评价夹具违反唯一键；时间边界未显式证明锁前/释放时 DB 时间；管理更新可能因未调度产生假阳性 |
| 3 | PASS；P0=0、P1=0、P2=0、P3=0 | 隐藏评价改为同商品不同已完成订单；持锁连接断言 `lockedAt < boundaryAt <= releaseAt` 且释放前仍阻塞；`executeUpdate` 前 latch 被主线程确认；未发现新问题 |
| 4（增量） | PASS；P0=0、P1=0、P2=0、P3=0 | 真实 MySQL 暴露夹具预置 `status=3` 与“全表数量为 0”断言矛盾；改为逐主键断言 `{0,1,2,3,0,0,0}` 完整保持，证据更精确且无产品代码变化 |

## Residual risks

- B0-AC6、B8-B11 仍是阶段完成和生产发布门禁。
- 生产迁移、维护窗口、部署和版本切换不属于 B7 本地交付。
