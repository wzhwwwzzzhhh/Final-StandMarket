# B9-agent-frontend-contract · Independent review

> Verdict: PASS
> Reviewer: 独立只读 `ai-module-security-review`（`b9_implementation_review`）
> Updated: 2026-09-04
> Severity: P0=0 / P1=0 / P2=0 / P3=0

## Scope and drift

- Requirement: Stage B B9 / Issue #21。
- Confirmed Design: `docs/design/agent/B9-agent-frontend-contract-design.md`。
- Reviewed slices: Java/Python 可信契约；用户端统一请求与 AgentChat；管理端成功码与支付回跳解释；测试和证据。
- Scope drift: PASS。未引入匿名聊天、推荐能力扩展、B8 实现、数据库迁移、支付写路径或部署能力。
- Shared-file coordination: 仅修改 B9 已确认的 `application.yml` 占位配置与 workpack 索引；未复制、覆盖或回退 B8 worktree 内容。

## Final findings

| Severity | Open | Conclusion |
|---|---:|---|
| P0 | 0 | 未发现阻断级问题 |
| P1 | 0 | 内部认证、身份、错误脱敏、依赖故障分类与 fail-closed 边界已闭合 |
| P2 | 0 | 会话存在性、坏 schema、坏商品逐条过滤、AgentChat 完整响应门禁已闭合 |
| P3 | 0 | 无剩余建议项 |

## Resolved during independent review

- Python graph 后处理坏结构可能 500；已收拢为稳定 `PYTHON_AGENT_UNAVAILABLE`。
- Redis 客户端缺少有限连接/读取超时；已增加受限配置并验证真实 TCP 连接拒绝。
- Java 收到非对象商品可能 `ClassCastException`；已防御并 fallback。
- ES/Java 工具/LLM 故障可能被后续 LLM 误报为真实空数据；已固定安全回复且禁止故障后再次猜测。
- FastAPI 默认 422 可能回显 JWT/消息输入；已使用稳定脱敏 422。
- ES 与推荐混合坏 hit 会丢弃整批；已逐条丢弃并保留合法商品。
- 显式 null/空白 sessionId 被当成缺失；已区分“省略”和“显式非法值”，同时保持浏览器 DTO 仅两个业务字段。
- AgentChat 仅校验 sessionId 即持久化；已校验 reply/products/degraded/reasons/商品字段和 invariant。
- Axios 强制 JSON 头、支付宝重复 `orderId` 参数顺序、内部 token 尾逗号等 RED 缺口均已按 TDD 关闭。

## Acceptance evidence review

| AC | Result | Evidence |
|---|---|---|
| AC1–AC11 | PASS | Java/Python 契约测试、HTTP/Redis 故障注入、真实 Redis 7、日志捕获 |
| AC12–AC13 | PASS | 用户端 25 项测试和生产构建 |
| AC14 | PASS | 管理端 2 项成功码测试和生产构建 |
| AC15 | PASS | 支付五态、验签参数与零写入测试；完整 Maven 回归 |
| AC16 | PASS | 审查后完整验证、diff/scope/sensitive 检查已归档 |
| AC17 | PASS | 本文件所记录的独立只读 Review，P0/P1/P2/P3 全为 0 |

## Residual risks and release boundary

- 两套前端仍无 lint/typecheck 脚本；本次只新增并执行 Vitest，未虚构 lint/typecheck 结果。
- 前端构建存在既有的大 chunk warning，不影响构建成功，后续性能优化不属于 B9。
- 生产内部凭据配置/轮换、网络策略、部署与生产验证均未执行。
- B0-AC6、B10、B11 以及 B9 仍是生产发布门禁；本地 PASS 不等于允许部署。
