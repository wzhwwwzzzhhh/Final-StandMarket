# Workpack 索引

> Workpack 是本地开发执行产物，记录计划、独立审查和验收证据。需求事实保留在阶段 B 或 PRD 中。

## 目录约定

```text
docs/workpack/
  <phase>-<slice>/
    plan.md
    review.md
    evidence.md
  归档/
    <phase>-<slice>/
```

每个工作包只包含一至三个紧密相关、可以独立验收的切片。

## 活跃工作包

| 阶段 | 切片 | 状态 | 需求源 | Plan | Review | Evidence |
|---|---|---|---|---|---|---|
| B0 | credential-log-safety | CI 验证中（[PR #5](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/5)；AC6 外部证据阻塞） | [阶段 B B0](../plans/阶段B-P0P1交易链路修复.md#b0密码token-与日志安全p0) / [Issue #4](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/4) | [plan](B0-credential-log-safety/plan.md) | [PASS](B0-credential-log-safety/review.md) | [evidence](B0-credential-log-safety/evidence.md) |


## 已归档

| 阶段 | 切片 | 本地交付日期 | Review | Evidence |
|---|---|---|---|---|
| OPS | github-ci-collaboration | 2026-08-28 | [PASS](归档/OPS-github-ci-collaboration/review.md) | [evidence](归档/OPS-github-ci-collaboration/evidence.md) |

## 状态

`待确认 → 进行中 → 待审查 → 本地已验证 → CI 验证中 → 已归档`

## 规则

- `plan.md` 未经用户确认，不修改产品代码。
- `review.md` 非 PASS 或 `evidence.md` 不完整，不得标记本地完成。
- 归档使用移动而不是复制，保留全部计划、审查和证据。
- 只有 GitHub 对已推送提交返回真实结果后，才能登记 PR/CI 状态；本地等价命令不得冒充远程检查。
