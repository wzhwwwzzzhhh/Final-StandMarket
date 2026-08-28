## 变更目标

<!-- 说明为什么要改、解决什么问题。 -->

## 需求与工作包

- 需求源：<!-- 阶段 B 编号或 PRD 路径 -->
- Design：<!-- 已确认 Design 路径；不适用写“无” -->
- Workpack：<!-- docs/workpack/<phase>-<slice>/ -->

## 范围

### 本次包含

-

### 明确不包含

-

## 验收与证据

| AC | 代码/测试证据 | 结果 |
|---|---|---|
| AC1 |  |  |

## Review

- 独立审查结论：<!-- PASS / FAIL / tooling_blocked -->
- `review.md`：<!-- 路径 -->
- 遗留风险：

## 检查清单

- [ ] 修改未超出已确认需求和 Design。
- [ ] 已运行相关聚焦测试及模块完整验证。
- [ ] `git diff --check` 通过。
- [ ] 没有提交密码、Token、密钥、生产配置或未脱敏数据。
- [ ] Java、Python、前端检查结果与实际能力一致，没有伪造 test/lint/typecheck。
- [ ] 数据库、缓存、MQ 或外部契约变化已说明兼容、失败处理和回滚。
- [ ] Workpack 的 `review.md` 与 `evidence.md` 已更新。
