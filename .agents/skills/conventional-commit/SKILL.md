---
name: conventional-commit
description: Use when writing a git commit message or asking Codex to commit - produces Conventional Commits style messages matching this repo's history
---

# Conventional Commit

## Overview

Write commit messages in Conventional Commits format, matching this repo's existing history.

This repo's commit history uses this style:

```
feat: Phase 6 ES 搜索增强
feat: Phase 5 售后退款完整接入
feat: Phase 4 物流/发货功能完整接入
feat: Phase 3 支付宝沙箱支付完整接入
chore: add __pycache__ and .Codex to gitignore
```

## Format

```
<type>(<scope>): <subject>
```

## Types

- `feat` — 新功能 / 新阶段
- `fix` — bug 修复
- `refactor` — 重构,不改变行为
- `docs` — 文档
- `chore` — 构建/配置/杂项
- `test` — 测试
- `perf` — 性能优化

## Rules

1. **Subject 用中文**,一行写完,不超过 50 字
2. 大型功能阶段提交用 `feat: Phase N 描述`,与仓库历史保持一致
3. **Why 优先于 What** — 描述动机和影响,不是罗列改了哪些文件
4. 提交信息聚焦于"为什么"和"带来的改变"
5. 需要关联功能阶段时用 scope,例如 `feat(agent): 新增意图识别`
6. 不要提交敏感配置文件(application-dev.yml 等已被 gitignore)

## Body (可选)

多行改动时,空一行后写 body,用中文列出关键改动点。Body 不需要列举每个文件——那些在 diff 里能看到,Body 只写"为什么这么改"和"关键决策"。
