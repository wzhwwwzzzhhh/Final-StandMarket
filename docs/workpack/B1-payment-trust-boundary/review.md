# B1-payment-trust-boundary · Independent review

> Verdict: PASS（P0 0 / P1 0 / P2 0 / P3 0）

## Scope and drift

审查范围限定为 Issue #6 / B1 产品代码、测试、迁移、Design 与 workpack。`.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md` 是范围外用户改动，未纳入 B1。

## Findings

- Round 1：FAIL（P0 0 / P1 2 / P2 2 / P3 0）。
  - P1：真实 MySQL 未触发 `DuplicateKeyException -> FOR UPDATE` 当前读；已补外部事务确定性竞态和并发直写测试。
  - P1：迁移仅模糊匹配生成列表达式；已改为精确数据类型、生成类型和规范化表达式校验，并补错误 schema 拒绝测试。
  - P2：latest payment 同时间排序不确定；已增加 `id DESC` 及真实 MySQL 测试。
  - P2：evidence 有过度/过期声明；已按实际运行修正。
- Round 2：FAIL（P0 0 / P1 1 / P2 0 / P3 0）。产品与测试问题已全部关闭，唯一 P1 为 evidence 仍保留修复前计数和待办文字；已修正，等待最终复核。
- Round 3：PASS（P0-P3 均无）。最终复核确认产品修复、MySQL 9 项门禁、全量 59 项结果、两端构建、diff/敏感信息检查、范围边界与 residual risks 均准确。

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| B1-AC1 | 通知验签、应用/状态/类型/金额/关联矩阵，管理端旁路契约 | pass |
| B1-AC2 | 精确重复/冲突回调单测，MySQL 双回调与事务回滚 | pass |
| B1-AC3 | 唯一约束迁移、八线程复用、外部赢家冲突当前读、并发直写 | pass |
| B1-AC4 | 不可信支付入口移除契约与两端生产构建 | pass |
| B1-AC5 | 本人订单和普通订单类型查询/创建约束 | pass |
| B1-AC6 | 同步回跳只读验签及完整支付宝参数转发 | pass |
| B1-AC7 | 回调、取消、超时 CAS 与真实竞态/回滚 | pass |
| B1-AC8 | 外部可覆盖且为正值的有限 HTTP 超时 | pass |

## Residual risks

- 当前 B1 分支基于较早本地基线；后续远程交付前必须与已合并 B0 的目标分支安全集成并重新执行全部门禁，不能把范围外本地改动混入。
- 增量迁移执行前需要停止支付写入；若发现历史活动流水冲突或部分/错误 schema，脚本会拒绝继续并要求人工核对，不会自动删数据。
- 两个 Vue 项目当前仅有生产构建脚本，没有 test/lint/typecheck 脚本，因此未声称这些检查通过。
