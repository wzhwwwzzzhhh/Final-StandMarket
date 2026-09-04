# B9-agent-frontend-contract · Evidence

> Status: 已归档（2026-09-04）；[PR #22](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/22) @ `4408b02` 首轮 5 项 checks 全绿，等待归档提交复验后合并
> Updated: 2026-09-04
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Branch: `codex/b9-agent-frontend-contract`

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| AC1 Agent 配置/URL/超时 | `AgentPropertiesB9Test`、`AgentHttpClientFailureB9IntegrationTest`；base URL 规范化、HTTPS/loopback、连接/读取超时 | PASS |
| AC2 浏览器 DTO 边界 | `AgentChatRequestB9ContractTest`、`AgentControllerB9ContractTest`；仅 message/sessionId，非字符串与非法输入 422 | PASS |
| AC3 Long 身份/用户委托 | Controller 从 `BaseContext<Long>` 构造内部 DTO；Long 大值与原始 Bearer 测试 | PASS |
| AC4 Java→Python 内部认证 | 专用 Header、1–2 个轮换 token、尾逗号/空白/重复配置 fail-closed、认证先于校验 | PASS |
| AC5 登录与 health | Java 未登录 401；Python `/health` 无认证且不泄露配置；`/chat` 缺/错内部凭据 401/503 | PASS |
| AC6 session 生成/校验/兼容 | 128-bit Base64URL、22–64 格式、显式 null/空白/旧 16 位拒绝、fallback 保留原 session | PASS |
| AC7 Redis 隔离/TTL/旧 key | 真实 Redis 7：用户隔离、history/slots TTL、原子写、精确 clear、旧 key sentinel 未读取/修改 | PASS |
| AC8 稳定响应 schema | Python/Java 正常与降级 invariant、坏 graph/下游 schema 统一 fallback | PASS |
| AC9 五类依赖故障 | Redis、ES、LLM、Java 工具、Python 服务分别注入；固定有限原因与安全回复 | PASS |
| AC10 坏商品过滤 | search/recommend 混合坏 hit 与响应商品逐条丢弃，合法商品保留；Java 非对象商品 fallback | PASS |
| AC11 安全日志 | Python caplog 与 Java ListAppender 验证：仅异常类型，不含消息、JWT、内部凭据或外部正文 | PASS |
| AC12 用户端统一 Axios | 全部 `src/api/*.js` 统一 request；Token/401/`skipAuthRedirect`/FormData/上传超时测试 | PASS |
| AC13 AgentChat | 不发送 userId/token；服务端 session；422 仅重试一次；完整成功 schema 门禁；degraded+product 展示 | PASS |
| AC14 管理端成功码 | 秒杀删除只认 `code === 1`，`code === 0` 不显示成功 | PASS |
| AC15 支付五态/零写入 | 0/1/2/3/-1 映射；支付宝参数保留且重复参数 fail-closed；回跳组件无写接口 | PASS |
| AC16 完整本地验证 | Maven、pytest+真实 Redis、双前端测试/构建、diff/scope/sensitive 检查 | PASS |
| AC17 独立实现 Review | `review.md`：P0=0、P1=0、P2=0、P3=0 | PASS |

## TDD red-green-refactor log

| Slice/behavior | RED evidence | GREEN evidence | Status |
|---|---|---|---|
| 前端请求/支付参数 | 聚焦 Vitest：2 failures（强制 JSON header、重复 orderId 未拒绝） | 13/13 passed | PASS |
| Python token 配置 | 聚焦 pytest：尾逗号配置错误返回 200 | 1/1 passed，配置 fail-closed | PASS |
| Java 严格浏览器 DTO | 数字 message 被 Jackson 缩放为字符串并返回 200 | Controller 合同 5/5，最终相关集 7/7 | PASS |
| Review 降级边界 | Python 8 failures；Java 非对象商品 `ClassCastException`；显式 null session 返回 200 | Python 聚焦 31 passed；Java Controller/Service 11 passed | PASS |
| ES/Java 工具坏结构 | 坏 ES schema 抛 `KeyError`；混合 hit 丢合法项；坏订单元素被接受 | 坏顶层归 ES 降级，坏单项被逐条丢弃，合法项保留 | PASS |
| AgentChat 完整 schema | 5 failures：4 类坏 data 被持久化，degraded 商品展示用例未满足 | AgentChat 9/9 passed | PASS |
| 完整 Java DTO 形状 | 首次全量：1 failure，内部 presence 字段暴露为第三字段 | 内部状态改为同字段哨兵；最终 Maven 474 项全绿 | PASS |

## Verification runs

| Command | Exit/result | Notes |
|---|---|---|
| `cd backend && mvn test` | PASS：474 tests，0 failures，0 errors，117 skipped | skipped 均为仓库显式真实依赖门禁；完整 reactor BUILD SUCCESS |
| `cd agent-service && python -m pytest -q`（显式 `B9_REAL_REDIS_URL`） | PASS：70 passed | 真实 Redis server major=7；无 skip |
| 用户端 `npm test` | PASS：5 files / 25 tests | 当前无 lint/typecheck 脚本 |
| 管理端 `npm test` | PASS：1 file / 2 tests | 当前无 lint/typecheck 脚本 |
| 用户端 `npm run build` | PASS：1729 modules | 仅 chunk size warning |
| 管理端 `npm run build` | PASS：2290 modules | 仅 chunk size warning |
| `git diff --check` | PASS | 仅 Windows LF→CRLF 提示，无 whitespace error |
| 独立只读实现 Review | PASS：P0/P1/P2/P3 = 0 | 最终快照复审 |
| PR #22 @ `4408b02` GitHub checks | PASS：5/5 | Java backend、Python agent、双前端 build、Gitleaks 全部成功；PR 为 `CLEAN / MERGEABLE` |
| 用户合并授权 | confirmed | 进入合并前需求状态同步与 workpack 归档；归档提交仍须再次通过 GitHub checks |

## Real dependency and fault-injection conditions

| Dependency/fault | Condition | Observed result |
|---|---|---|
| Redis 7 | 隔离测试容器经回环转发 `127.0.0.1:36379`，DB 15；测试断言 server major=7 | 用户/会话隔离、TTL、旧 key sentinel、精确清理全部 PASS |
| Redis connection refusal | 动态占用后释放的回环端口；真实 async Redis client，connect/read timeout 0.2s | HTTP 200；保留业务 reply；唯一原因 `REDIS_UNAVAILABLE`；小于 3 秒 |
| Redis + graph 组合故障 | Redis history 失败 + graph 异常 | 原因稳定为 `REDIS_UNAVAILABLE, PYTHON_AGENT_UNAVAILABLE` |
| ES | 超时/异常 stub、顶层坏 schema、混合坏 hit | 空/部分合法商品按契约返回；`ELASTICSEARCH_UNAVAILABLE`；不猜测 |
| LLM | 第一次调用 TimeoutError，第二次模拟可成功 | 不进行第二次猜测；规则回复；`LLM_UNAVAILABLE` |
| Java 工具 | 非成功码、坏 data、订单/物流异常 | 固定“订单服务暂不可用”；`JAVA_TOOL_UNAVAILABLE` |
| Python service | 本地真实 HTTP 连接拒绝与延迟读超时 server | Java 在预算内返回原 session、空商品、`PYTHON_AGENT_UNAVAILABLE` |

## Scope and sensitive-information review

- 限定范围仅含 B9 Design/workpack、Java/Python Agent 契约、两套前端相关文件及测试配置；无 MySQL/迁移、B8 实现、部署或生产配置文件。
- 配置只提交环境变量占位符；未写入真实内部 token、JWT、Redis 密码、LLM key、私钥或完整外部响应。
- 测试使用明显的占位 token/marker，并通过日志捕获断言这些 marker 不出现在日志/错误响应中。
- 临时 `agent-service/.venv311` 与 `.pytest_cache` 已在归档前按已验证精确路径删除。
- 主工作区用户未提交的 B1 文件与 `docs/prototypes/` 未修改、未暂存、未清理；B8 worktree 未修改。

## Not run / intentionally out of scope

- 两套前端没有 lint/typecheck 脚本，因此未声称通过这些检查。
- 未连接真实 ES 或真实 LLM；故障分类使用确定性 stub，真实 Redis 7 与真实 HTTP/TCP 失败另有运行证据。
- B9 不含 MySQL schema/数据迁移，未运行数据库迁移。
- 已完成 commit、push 和 PR；尚未执行 merge、生产凭据轮换、生产 Redis 操作或部署。

## Local delivery summary

B9 已本地验证并归档：AC1–AC17 均有可运行证据，独立实现 Review P0/P1/P2/P3 全为 0，审查后全量验证与 PR #22 首轮 5 项 GitHub checks 均通过。当前等待归档提交复验后合并；B0-AC6、B10、B11 和生产发布门禁仍未解除，也未获得部署授权。
