# B2-order-inventory-state · Independent review

> Verdict: PASS（2026-08-30，P0/P1/P2 均为 0）

## Scope and drift

- Review scope: Issue #8 / Stage B B2 confirmed Design、产品代码、测试、SQL 与本 workpack。
- Out-of-scope local changes: 主工作树三项用户工作流修改、`stage-b-ac6-gate` worktree、B3/B5/B10 产品实现。
- 范围复核未发现 B3 退款产品代码、秒杀库存或本地敏感配置混入。

## Plan and Design review

- 原 Design 三轮复审后 PASS；原 plan 三轮复审后 PASS。
- 首轮实现审查发现历史重复回调和履约迁移矛盾，触发 Design/plan 修订门禁；修订经三轮复审后 PASS，并由用户回复“继续”确认。
- 最终决策：支付发起/复用/首次成功回调要求 `stock_deducted=1`；已成功且相同 `trade_no` 的精确重复通知零写幂等返回，冲突拒绝；迁移拒绝 `status IN (2,3) AND stock_deducted=0`，不盲目回填库存事实。

## Implementation review

- Round 1: CHANGES_REQUESTED（0 P0 / 4 P1 / 2 P2）。发现历史精确重复通知回归、历史履约卡死、手写 JDBC 未证明 Spring/MyBatis 事务、`REQUIRES_NEW` 未证明、`FORCE INDEX`/非实际 SQL、文档漂移。
- Round 2: CHANGES_REQUESTED（0 P0 / 1 P1 / 1 P2）。剩余问题为 `EXPLAIN SELECT id` 与生产 `SELECT *` 不一致，以及阶段 B 文件清单旧任务类名。
- Final: PASS（P0/P1/P2 均为 0）。确认首轮 4 P1/2 P2 和第二轮 1 P1/1 P2 全部关闭。

## Acceptance evidence review

| AC | Reviewed evidence | Result |
|---|---|---|
| B2-AC1 | MySQL 有限库存并发争抢，成功订单/明细/库存一致 | PASS |
| B2-AC2 | 真实 Spring AOP + MyBatis + MySQL 覆盖第二商品、锁券、订单插入、券绑定零行、明细失败整体回滚 | PASS |
| B2-AC3 | 服务端计价、忽略客户端秒杀字段、订单持久化字段合约 | PASS |
| B2-AC4 | 双取消单赢家；历史库存零标识取消不回补且拒绝支付 | PASS |
| B2-AC5 | 支付/取消竞态单赢家 | PASS |
| B2-AC6 | 首次支付库存门禁、历史精确重复三例、发货/收货专用 CAS | PASS |
| B2-AC7 | 无登录上下文立即拒绝且 Mapper 零调用 | PASS |
| B2-AC8 | 真实代理的有券/无券混合超时批次，中间失败独立回滚并下轮成功重试 | PASS |
| B2-AC9 | 绑定、释放、核销零行使订单/支付/库存/券事务整体回滚 | PASS |
| B2-AC10 | MySQL 迁移首次/重跑/错误/部分/历史履约阻断及实际 Mapper SQL `EXPLAIN` | PASS |

## Residual risks

- B3 未封死旧退款审批直接回补前，B2 只能标记“本地已验证/不可部署”。
- 纯 B1 不是有效回滚目标；部署和生产迁移仍属于后续 B3/B10/B11 门禁。
- 历史待支付流水以及 `status IN (2,3)` 历史履约订单必须在未来切换前按 Design 盘点和清零。
