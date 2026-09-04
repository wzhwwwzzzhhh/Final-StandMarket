# B9 AI 服务与前端契约治理 · Design

> Status: 已确认
> Requirement source: [阶段 B：B9 AI 服务与前端契约治理](../../plans/阶段B-P0P1交易链路修复.md#b9ai-服务与前端契约治理p1)
> Tracking: [GitHub Issue #21](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/21)；Stage B 总跟踪 [#3](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/3)
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Updated: 2026-09-04
> Confirmed: 2026-09-04（用户明确确认）

## 1. Goal and scope

在不新增 AI 业务能力、不改变支付状态机的前提下，收紧浏览器、Java 和 Python Agent 的身份、会话及错误边界：浏览器只能提交业务输入；Java 是用户身份和会话入口的可信边界；Python `/chat` 只接受经过应用层认证的 Java 调用；每个依赖故障都收敛为稳定、可诊断且不泄密的降级响应。同时统一用户端 Axios、管理端秒杀成功码及支付回跳的只读状态解释。

### In scope

- 拆分浏览器聊天 DTO 与 Java→Python 内部 DTO，以 `BaseContext` 中的 `Long userId` 为唯一用户身份来源。
- 为 Java→Python `/chat` 增加环境注入的应用层内部认证、Agent 专用 URL/超时配置和安全日志边界。
- 由 Java 生成/校验 sessionId；Python Redis history/slots 均按 `(userId, sessionId)` 隔离并设置 TTL。
- 定义正常与 Redis、ES、LLM、Java 工具、Python 服务故障时的统一响应 schema 和有限原因枚举。
- 用户端所有 `src/api/*.js` 复用 `src/utils/request.js`，兼容上传、Token 注入、401 和 `skipAuthRedirect`。
- `AgentChat` 只发送 `message/sessionId`，使用服务端返回的 sessionId，并展示登录/降级语义。
- 管理端秒杀查询、确认、取消、删除等成功分支只认 `response.data.code === 1`。
- 支付状态 `0/1/2/3/-1` 的只读前端映射；同步回跳继续零写入并完整保留支付宝验签参数，仅排除本站追加的 `orderId`。

### Out of scope

- 匿名 AI 会话、新 Agent 工具、Prompt/推荐质量优化、向量搜索或 Python HTTP 全异步重构。
- ES 商品同步正确性；它属于 B8，B9 只治理 ES 故障时的响应。
- mTLS、API Gateway、生产网络策略、生产凭据生成/轮换和部署。
- 改造 Java 用户 JWT 机制或把 Python→Java 工具调用改成新的用户委托协议。
- 支付/退款状态机、支付通知或同步回跳写状态；继续遵守已确认的 [B1 支付可信边界 Design](../payment/B1-payment-trust-boundary-design.md)。
- 全站管理端 Axios 重构；管理端只处理 Issue #21 明确的秒杀成功码。
- B0-AC6、B8、B10、B11 的实现与发布门禁。

## 2. Current behavior and constraints

### 2.1 Java 边界

- `AgentChatRequest` 同时承担浏览器与内部调用 DTO，暴露 `Integer userId`、`token`；Controller 将 `BaseContext.getUserId()` 从 `Long` 缩窄为 `Integer`，再修改并转发原请求对象。
- Controller 从浏览器 `Authorization` 提取 JWT 后放回请求体；虽然身份读取自 `BaseContext`，但公开 DTO 与敏感内部字段未隔离，后续维护容易重新信任浏览器字段。
- `AgentServiceImpl` 硬编码 `http://localhost:8000/chat`，复用通用 `RestTemplate`，没有 Agent 专用认证和超时契约。
- Python 超时、拒绝、非 2xx 或空响应时，Java 返回空 sessionId、`products=null`，正常/降级 schema 不一致；日志直接记录异常消息，可能携带 URL 或外部响应细节。
- `/user/**` 已由用户登录拦截器保护，未登录返回 HTTP 401；Agent Controller 内仍有返回 HTTP 200 + `Result.error` 的冗余登录分支。

### 2.2 Python Agent

- `/chat` 没有应用层内部认证，直接信任 JSON 中的 `userId/token`。
- sessionId 缺失时使用截断为 16 个十六进制字符的 UUID，仅约 64-bit；外部 sessionId 没有长度/字符集门禁。
- Redis key 为 `agent:session:{sessionId}` 和 `agent:slots:{sessionId}`，没有 userId；猜到相同 sessionId 的不同用户会共享历史和槽位。
- history/slots 写入有 7 天 TTL，但读取、解析和写入故障会向顶层冒泡；旧 key 可被直接读取。
- LLM 已有部分规则兜底，搜索也有部分空结果兜底，但没有统一传播“哪个依赖降级”；搭配搜索吞异常且不标记，顶层 graph/Redis/商品 schema 异常仍可产生 500。
- Python 调 Java 订单/物流工具时使用用户 JWT；该 JWT 是敏感委托凭据，必须继续限制在 Java→Python 内部 DTO 和 Python→Java Authorization header，不得进入日志或客户端响应。

### 2.3 前端

- 用户端 12 个 `src/api/*.js` 中，`address/agent/coupon/product/seckill/upload/user` 各自创建 Axios 实例并重复 Token/401 逻辑；其余文件已复用 `src/utils/request.js`。
- `upload.js` 依赖 30 秒超时和 multipart；统一实例时必须用单请求配置保留超时，并让浏览器/Axios生成 multipart boundary。
- `browse.js` 已使用 `skipAuthRedirect: true`，统一后不能把匿名浏览行为的 401 变成强制跳转。
- `AgentChat` 在浏览器生成约 64-bit sessionId、发送本地 `userId`，且不保存服务端返回的 sessionId。
- 管理端秒杀列表的查询、确认、取消使用 `code === 1`，删除仍错误使用 `code === 0`。
- B1 已保证同步回跳后端零写入且前端只排除本站 `orderId`；当前 UI 的查询路径能将 `0/1` 设为处理中，但验签成功路径只判断 `2`，会把 `0/1` 落入失败展示，也没有对 `3/-1` 作明确区分。

### 2.4 并行与发布约束

- B8 在独立 worktree 并行，可能同时修改 `application.yml`、文档索引或共享配置。B9 实现不得复制、覆盖或回退 B8 diff；共享文件只能在最新 master 上做最小增量整合。
- 当前双前端只有 `build` 脚本，没有 test/lint/typecheck 脚本；不得把不存在的命令写成通过证据。
- 本 Design 不执行生产凭据轮换、部署或 Redis 数据迁移。

## 3. Design decisions

### D1. 公开 DTO 与内部 DTO 物理分离

公开 `POST /user/agent/chat` 只绑定 `AgentBrowserChatRequest`：

```json
{
  "message": "帮我找一件连衣裙",
  "sessionId": "可选"
}
```

- `message` 去除首尾空白后长度为 `1..2000`；缺失、空白、类型错误或超长均返回 HTTP 422。
- `sessionId` 可缺失；若存在，必须满足 `[A-Za-z0-9_-]{22,64}`。不匹配返回 HTTP 422，不尝试修正、截断或哈希成另一个会话。
- DTO 不声明 `userId/token/userAuthorization`。为兼容尚未更新的旧前端，额外字段被忽略，但测试必须证明恶意 `userId/token` 既不参与身份决策，也不会原样进入内部 DTO。
- 用户身份唯一来自已通过登录拦截器建立的 `BaseContext.getUserId()`；内部类型始终为 `Long`，禁止 `intValue()`、`Integer` 或其他缩窄。
- Controller 只读取已经通过当前用户拦截器认证的原始 `Authorization: Bearer ...`，构造新的 `AgentInternalChatRequest`，绝不修改或转发浏览器 DTO 本身。

Java→Python 内部 DTO 是独立类型：

```json
{
  "userId": 9223372036854770000,
  "sessionId": "128-bit-or-stronger-server-session-id",
  "message": "帮我找一件连衣裙",
  "userAuthorization": "Bearer <authenticated-user-jwt>"
}
```

- `userId` 为 JSON int64/Java `Long`；Python `int` 接收但拒绝 `<= 0`。
- `userAuthorization` 只用于 Python 调用现有 Java 订单/物流工具，是已经认证过的用户委托凭据。它与 Java→Python 应用凭据是两个不同的安全域，不得复用。
- 内部 DTO 只在内存和 TLS/受控网络链路中存在，不落 Redis、不落数据库、不输出日志、不回传浏览器。
- 本阶段不新增匿名路径；未登录浏览器由 Java 登录拦截器返回 HTTP 401，Controller 不用 HTTP 200 的业务错误伪装认证失败。

### D2. Java→Python 使用独立、fail-closed 的应用层认证

- Java 每次调用 Python `/chat` 都发送 `X-FSM-Agent-Token: <secret>`。该 header 只证明调用方应用是 Java 服务，不能替代 `userId + userAuthorization` 的用户委托。
- Java 配置：
  - `fashion.agent.base-url` ← `FASHION_AGENT_BASE_URL`
  - `fashion.agent.connect-timeout-ms` ← 可覆盖、默认 3000 ms
  - `fashion.agent.read-timeout-ms` ← 可覆盖、默认 10000 ms
  - `fashion.agent.internal-token` ← `FASHION_AGENT_INTERNAL_TOKEN`，无仓库默认值
- Python 配置：`AGENT_INTERNAL_TOKENS`，接收一个或两个逗号分隔的活动凭据；无仓库默认值。
- Java token 缺失/空白、Python 活动 token 集合为空、token 长度低于 32 个字符或超时非正值时，应用配置校验失败并拒绝启用 `/chat` 调用，不能退回无认证模式。
- Python 以常量时间比较请求 token 与活动集合。缺失或错误 token 返回 HTTP 401 和通用错误，不进入 DTO 业务处理、不访问 Redis/ES/LLM/Java 工具；`/health` 不含敏感数据，保持无需内部认证。
- 网络隔离和 TLS 是纵深防御及生产部署要求，但不作为本 Issue“已认证”的替代证据；本地故障注入可使用回环地址。

凭据轮换顺序：

1. 生成新凭据（生产操作不在 B9 本地交付范围），先把 Python 配置为同时接受 `old,new` 并滚动重启。
2. 验证旧凭据仍成功、新凭据成功、错误凭据 401；再把 Java 发送凭据切为 `new` 并滚动重启。
3. 确认所有 Java 实例均使用 `new` 后，从 Python 活动集合删除 `old`。
4. 第 3 步前回滚时，Java 可切回 `old`；第 3 步后回滚必须先让 Python 重新接受 `old`，再回滚 Java。不得为了恢复服务关闭认证。

### D3. Agent URL 与超时使用专用客户端

- 新增 `AgentProperties` 和带限定符的 `agentRestTemplate`（或语义等价的专用客户端）；不修改 B1 的共享 `RestTemplate` 超时，避免改变其他 HTTP 调用。
- `base-url` 去除所有末尾 `/` 后只拼接一次 `/chat`。配置必须是绝对 `http/https` URL，包含 host，不允许 query、fragment、userinfo 或预拼接 `/chat`；空值、非法 URI 和非允许 scheme 启动失败。非回环地址必须使用 HTTPS；HTTP 只允许 `localhost/127.0.0.1/[::1]`，或在显式测试 profile 中由默认关闭的 `allow-insecure-http` 开关放行。生产 profile 禁止该开关，避免应用 token 和用户委托 JWT 明文跨主机传输。
- 仓库不保留 `localhost` 业务默认值。开发、测试和生产均显式注入 base URL。
- connect/read timeout 必须是可覆盖的有限正整数，并设置合理上限（设计上限 60 秒）；超时、连接拒绝、非 2xx、空 body、反序列化失败及非法响应均在 Java 侧收敛为 `PYTHON_AGENT_UNAVAILABLE` 降级，不能无限等待。
- Java 不记录请求/响应 body、用户 JWT、内部 token、完整下游响应或异常 message；只记录依赖名、异常类、HTTP 状态类别、traceId 和 sessionId。

### D4. Java 是 sessionId 的生成与外部校验边界

- 浏览器未提供 sessionId 时，Java 使用 `SecureRandom` 生成至少 16 个随机字节，并以无填充 Base64URL 编码；当前格式为 22 个字符、128-bit 熵。禁止使用截断 UUID、`Math.random()`、用户 ID、时间戳或可预测序列。
- 浏览器提供的 sessionId 先按 D1 规则校验；同一用户可以继续该 session，不同用户即使提交相同 sessionId 也进入不同 Redis 命名空间。
- Java 在调用 Python 之前确定有效 sessionId，因此即使 Python 不可达，Java 降级响应仍返回同一个非空 sessionId。
- Python 内部 schema 将 sessionId 设为必填并再次执行相同字符集/长度校验；Java/Python 契约错误返回 Python HTTP 422，不能由 Python 静默生成第二个 ID。
- Python 响应的 sessionId 必须与本次 Java 内部请求的 sessionId 完全相等；仅“格式合法”不够。Java 收到另一个合法但不同的 ID 时拒绝整份响应，使用原请求 sessionId 生成 `PYTHON_AGENT_UNAVAILABLE` fallback，禁止下游静默切换会话。
- `AgentChat` 只有在收到 `code === 1` 且 data schema 有效时，才以服务端 `sessionId` 覆盖并保存 `localStorage.agent_session`；浏览器不再生成 sessionId。非法/空 sessionId 不写本地存储。

### D5. Redis history/slots 以 `(userId, sessionId)` 隔离

新 key 固定为：

```text
agent:user:{userId}:session:{sessionId}:history
agent:user:{userId}:session:{sessionId}:slots
```

- history 的 get/save/clear 和 slots 的 get/save/clear API 都显式接收 `Long/int userId` 与 sessionId；不存在只传 sessionId 的重载。
- 两类 key 的 TTL 由 `AGENT_SESSION_TTL_SECONDS` 配置，默认 604800 秒（7 天），必须为有限正值。history 必须用一段 Redis Lua（或语义等价的单次原子事务）原子完成 `LPUSH + LTRIM + EXPIRE`；slots 必须使用单条 `SET key value EX ttl`。不能先写 value/list 再以独立命令补 TTL。任一原子写失败均视为整体失败并标记 `REDIS_UNAVAILABLE`，不得产生新的无 TTL key；读取不延长 TTL。
- history 仍限制最近 20 条结构化消息，读取恢复时间正序；JSON 损坏条目被丢弃并记录条目计数，不记录正文。
- 旧 `agent:session:{sessionId}` 与 `agent:slots:{sessionId}` 不迁移、不扫描、不删除、不回退读取，只按原 TTL 自然过期。该规则避免无法证明所有权的历史被绑定给新用户。
- A/B 使用相同 sessionId 时 key 必然不同。clear 也只能删除当前 `(userId, sessionId)`，不能通配删除。

### D6. 正常与降级响应使用稳定 schema

Python 内部响应及 Java 对外 `Result.data` 统一为：

```json
{
  "reply": "非空用户可读文本",
  "sessionId": "有效且非空",
  "products": [],
  "degraded": false,
  "degradationReasons": []
}
```

- `products` 永不为 null；`degradationReasons` 永不为 null，按下列枚举顺序去重。
- `degraded === (degradationReasons 非空)`，正常响应必须为 `false + []`。
- 有限原因枚举仅为：
  - `REDIS_UNAVAILABLE`
  - `ELASTICSEARCH_UNAVAILABLE`
  - `LLM_UNAVAILABLE`
  - `JAVA_TOOL_UNAVAILABLE`
  - `PYTHON_AGENT_UNAVAILABLE`
- 可同时返回多个原因；固定排序使测试、日志聚合和前端展示稳定。前端只展示 `reply` 和商品，不拼接内部原因；原因字段供诊断和可观测性使用。
- Python 返回的未知原因、空 reply、非法 sessionId、与请求不相等的 sessionId、null products 或无法安全反序列化的商品都视为下游契约无效。Java 不透传部分不可信响应，改为本地稳定 fallback，原因 `PYTHON_AGENT_UNAVAILABLE`，并保留原请求 sessionId。

HTTP 与外层 `Result` 语义：

| Boundary/case | HTTP | Body semantics |
|---|---:|---|
| Java 对浏览器：正常或依赖降级 | 200 | `Result.code=1`，data 使用稳定 schema |
| Java 对浏览器：未登录/失效登录 | 401 | 现有认证错误结构；无降级 data |
| Java 对浏览器：message/sessionId 无效 | 422 | `Result.code=0`，稳定 `msg=INVALID_MESSAGE/INVALID_SESSION_ID`，无降级 data |
| Python `/chat`：正确应用凭据、正常或依赖降级 | 200 | 内部稳定 schema |
| Python `/chat`：应用凭据缺失/错误 | 401 | 通用认证错误；业务逻辑零执行 |
| Python `/chat`：内部 DTO 无效 | 422 | Pydantic/稳定输入错误；不伪装降级 |
| Python `/health` | 200 | 仅存活信息，不读取或回显凭据 |

### D7. 各依赖故障独立收敛

| Failure | Required behavior | Reason |
|---|---|---|
| Redis 读取失败/超时/坏 JSON | 使用空 history/slots 继续；回复后尝试保存，写失败不覆盖已生成回复 | `REDIS_UNAVAILABLE` |
| ES 搜索/搭配失败、超时或坏响应 | 商品结果为空，回复明确商品检索暂不可用；搜索与搭配两条路径都传播状态 | `ELASTICSEARCH_UNAVAILABLE` |
| 任一 LLM 调用超时/拒绝/坏响应 | 使用关键词意图和规则回复；reply 必须非空 | `LLM_UNAVAILABLE` |
| Python→Java 订单/物流工具超时、拒绝、非 2xx 或坏响应 | 不猜测订单数据；回复订单服务暂不可用 | `JAVA_TOOL_UNAVAILABLE` |
| Java→Python 超时、拒绝、非 2xx、空/坏 schema | Java 在自身超时预算内返回本地 fallback、原 sessionId 和空商品 | `PYTHON_AGENT_UNAVAILABLE` |
| Python graph 顶层非认证/非校验异常 | 顶层业务边界捕获，返回原 sessionId、本地 fallback 和空商品 | `PYTHON_AGENT_UNAVAILABLE` |

- 认证依赖和请求 schema 的 401/422 在进入业务 handler 前完成，顶层业务 catch 不捕获并改写这些错误。
- 多依赖故障合并所有已观测原因，不用“最后一个异常”覆盖前一个原因。
- 商品映射逐条验证 `id/name/price/image` 的类型和必填性；无效、缺字段或无法构造 `ProductItem` 的条目单独丢弃，其他有效商品继续返回。日志只记丢弃数量、异常类型、trace/session 标识，不记完整商品或外部响应。
- 所有外呼必须有有限超时。不得无限重试；B9 不在同步聊天请求内增加自动重试，以免放大故障和延迟。

### D8. 敏感数据与诊断日志边界

- 可以记录：依赖名、操作名、异常类、HTTP 状态类别、降级原因枚举、服务端 traceId、sessionId、无效商品计数、耗时分桶。
- 禁止记录：用户消息正文、历史/slots 内容、用户 JWT、Java→Python 内部 token、请求/响应 header、完整外部响应、完整商品/订单对象、Python/OpenAI/ES/Redis 异常原文。
- 不将 JWT、内部 token 或消息正文写入 Redis key、MDC、指标 label、错误响应或 `toString()`；内部 DTO 禁止使用会自动输出全字段的日志。
- traceId 由服务端生成或只接受通过长度/字符集门禁的既有值；sessionId 是随机不透明诊断标识，但日志仍不得与消息正文组合记录。

### D9. 用户端统一 Axios，但保留调用级能力

- 所有 `frontend/fashion-client/src/api/*.js` 只允许 `import request from '@/utils/request'`（命名可为 `api`），不得直接 `import axios`、`axios.create`、注册拦截器、读取/删除 token 或自行导航登录页。
- `src/utils/request.js` 是唯一契约：默认 `/api`、10 秒、JSON；请求拦截器统一注入 `Authorization: Bearer <token>`；401 时清理 token/userInfo 并跳登录，除非该请求配置 `skipAuthRedirect: true`。
- `skipAuthRedirect` 只抑制 401 清理/跳转，不抑制已有 token 注入，不吞异常。`browse.record` 等匿名兼容调用继续以单请求配置传入。
- 登录、注册、验证码请求传 `skipAuthRedirect: true`，避免凭据错误或匿名接口异常触发循环跳转；受保护接口使用默认行为。
- 上传通过统一实例 `request.post('/upload/oss', formData, { timeout: 30000 })` 保留长超时。不得手写全局 `multipart/form-data`，由浏览器/Axios为该 FormData 请求生成带 boundary 的 Content-Type；签名查询继续使用默认配置。
- 现有 API 的 method、URL、params/data 和 export 形式保持兼容；B9 不借统一实例重命名业务 API。

### D10. AgentChat 只服从服务端身份与会话

- 未登录时不发送聊天请求，显示登录引导；登录判断只用于 UX，安全仍由 Java 401 保证。
- 请求体只有 `message` 和可选的现有 `sessionId`；不读/不发送 localStorage `userInfo.id`，不显式发送 token（统一 Axios header 自动注入）。
- 删除浏览器 `genId`。收到成功响应后使用并持久化服务端 sessionId；后续消息带回该值。
- 组件初始化时先用与服务端相同的 `[A-Za-z0-9_-]{22,64}` 门禁检查 `localStorage.agent_session`。当前版本遗留的 16 字符 sessionId 及任何非法值必须立即删除，首轮请求不带 sessionId，让 Java 生成新值；不迁移或填充旧值。
- Java 的 session 校验 422 使用稳定错误标识 `Result.code=0, msg=INVALID_SESSION_ID`。若一次请求因该标识失败，组件删除本地 sessionId，并可在同一用户操作内最多自动重试一次无 session 请求；其他 422（例如 `INVALID_MESSAGE`）不重试，也不得通过匹配本地化文案决定重试。一次重试仍失败则正常展示输入错误，禁止循环。
- 正常与降级都以 `code === 1` 处理；`degraded=true` 时展示后端已经安全生成的 reply，商品使用 `products || []`，不把原因枚举直接暴露为技术错误。
- HTTP 401 由统一拦截器清理登录态/跳转，同时组件可设置登录提示；HTTP 422 显示输入错误，不当成依赖降级；网络错误使用组件本地非敏感兜底。

### D11. 管理端秒杀成功码统一为 `code === 1`

- `SeckillOrderList.vue` 的列表查询、活动查询、统计、确认支付、取消和删除均只在 `response.data.code === 1` 时进入成功分支。
- 任意 `code !== 1` 均显示失败且不得刷新后展示成功；尤其删除不得再把 `code === 0` 当成功。
- 只修改 Stage B 指定的秒杀视图，不扩展为管理端全站 Axios/返回码重构。

### D12. 支付回跳继续只读并明确五态映射

支付 UI 使用同一个纯函数/等价单点映射解释状态：

| `payStatus` | UI state | Behavior |
|---:|---|---|
| `0` 待支付 | `pending` | 显示处理中，有限轮询 |
| `1` 支付中 | `pending` | 显示处理中，有限轮询 |
| `2` 成功 | `success` | 显示支付成功，不再轮询 |
| `3` 失败 | `failed` | 显示支付失败/可重试，不轮询 |
| `-1` 无支付记录 | `incomplete` | 显示支付未完成，不轮询 |

- 同步回跳验签成功和直接状态查询必须复用同一映射；不能只在查询分支识别 `0/1`。
- 支付宝回跳提交验签时不采用字段白名单；只排除本站追加的 `orderId`，其余签名字段和值保持原样，不 trim、不改名、不类型转换。若出现重复 query key，前端不得擅自选第一个并继续验签，应明确拒绝为无效回跳，避免改写签名语义。
- `orderId` 只用于本站状态查询/导航，绝不进入支付宝验签参数。
- 前端只调用已存在的只读 `POST /user/pay/alipay/verify` 与 `GET /user/pay/status/{orderId}`；不得调用状态更新接口。后端同步回跳继续只验签并查询当前用户支付记录，任何状态迁移仍只允许 B1 定义的可信异步通知路径。

## 4. Contracts and state transitions

### 4.1 调用与信任链

```text
Browser
  -- Authorization: user JWT; body: message/sessionId only -->
Java user auth interceptors
  -- establish BaseContext<Long> -->
AgentController
  -- generate/validate session; build NEW internal DTO -->
AgentService / dedicated client
  -- X-FSM-Agent-Token + internal DTO(userId Long, delegated userAuthorization) -->
Python /chat
  -- validate app token before body/business -->
Redis namespace(userId, sessionId) / ES / LLM / Java tools
  -- stable response or enumerated degradation -->
Java Result(code=1) --> Browser
```

浏览器字段不能跨越第一条身份边界；内部应用 token 不能承担用户身份；用户 JWT 不能承担 Java→Python 应用身份。三者职责不可互换。

### 4.2 会话生命周期

```text
missing sessionId
  -> Java SecureRandom 128-bit+ generation
  -> Python namespaced read/write
  -> response returns same sessionId
  -> browser persists returned value

valid existing sessionId
  -> Java preserves it
  -> Redis key additionally binds current BaseContext userId

invalid sessionId
  -> HTTP 422
  -> no Python call / no Redis access
```

服务重启不会改变已持久化会话的 key 规则；Redis TTL 到期后，同一个 sessionId 可继续作为标识但历史/slots 为空。B9 不提供跨用户转移、找回或迁移会话。

### 4.3 降级合并规则

每个 Python state 维护集合型 `degradation_reasons`。节点只能追加已定义枚举；响应边界去重并按 D6 固定顺序输出。Java 只接受白名单枚举。任何未知值说明内部契约漂移，整份响应按 `PYTHON_AGENT_UNAVAILABLE` fallback 处理，而不是把未知字符串交给浏览器。

## 5. File-level change surface

### Java

- `backend/fashion-pojo/.../dto/AgentChatRequest.java`：收敛为浏览器 DTO，或由新的 `AgentBrowserChatRequest` 替代。
- 新增 Java→Python 专用 `AgentInternalChatRequest`、稳定 `AgentChatResponse` 字段和降级原因枚举。
- `backend/fashion-server/.../controller/user/AgentController.java`
- `backend/fashion-server/.../service/AgentService.java`
- `backend/fashion-server/.../service/impl/AgentServiceImpl.java`
- `backend/fashion-server/.../config/`：AgentProperties、专用 HTTP client、Agent 输入 422 映射所需的最小配置。
- `backend/fashion-server/src/main/resources/application.yml`：仅添加无秘密的 Agent 占位符/超时配置；实现时先核对 B8 最新版本。
- Java Controller、配置、Service 契约和 fallback 聚焦测试。

### Python

- `agent-service/app/config.py`
- `agent-service/app/schemas.py`
- `agent-service/app/main.py`
- `agent-service/app/redis_memory.py`
- `agent-service/app/agent/graph.py`、`nodes.py`
- `agent-service/app/tools/search_product.py`、`recommend.py` 及 Java 工具调用路径
- Python 认证、schema、Redis 隔离/TTL、各依赖降级和商品丢弃测试。

### 用户端

- `frontend/fashion-client/src/utils/request.js`
- `frontend/fashion-client/src/api/*.js`（12 个现有 API 文件，保持 export/API URL 兼容）
- `frontend/fashion-client/src/components/AgentChat.vue`
- `frontend/fashion-client/src/views/PayResult.vue`

### 管理端

- `frontend/fashion-admin/src/views/SeckillOrderList.vue`

### 文档（Design 确认后）

- `docs/workpack/B9-agent-frontend-contract/{plan.md,review.md,evidence.md}`
- `docs/workpack/README.md` 的单行登记；必须与 B8 最新索引协调，不覆盖并行条目。

## 6. Failure handling, idempotency, and compensation

- 聊天请求不写 MySQL，不存在跨存储事务；Redis memory 是可丢失的辅助状态，失败时以无记忆模式继续，不补写用户正文到其他存储。
- Java 在下游调用前生成 sessionId，使超时 fallback 幂等地返回相同会话标识；Java 同一请求不自动重试 Python，避免重复保存同一轮 history。前端仅可按 D10 对明确的 session 校验 422 做一次无 session 重试，该失败发生在 Python 调用前，不会重复保存 history。
- Python history 的 user/assistant 两次业务追加允许分别执行，但每次追加自身必须按 D5 以 Lua/原子事务同时完成 push、裁剪和 TTL；slots 用 `SET EX`。任一步失败都只标记 Redis 降级，不撤回已生成回复；后续请求可能缺一条历史，但不能留下无 TTL key、串用户或返回 500。
- Redis 重连、Python/Java 重启后仍用确定性 namespaced key；重复使用相同 sessionId 不会复制或跨用户合并历史。
- ES/LLM/Java 工具失败没有副作用，不做同步重试。规则 fallback 和空 products 是唯一补偿。
- Java 收到 Python 401 表示内部配置/轮换错误：对浏览器仍 fail closed 为 `PYTHON_AGENT_UNAVAILABLE` 降级，日志只记下游状态类别并触发运维告警，绝不回退无 token 调用。
- 输入/认证错误发生在业务前，严格 401/422；只有已经通过认证和输入校验后的依赖故障才返回 HTTP 200 的业务降级。

## 7. Migration, compatibility, and rollback

### 7.1 发布顺序

1. 在测试环境显式注入 Agent base URL、超时和新内部 token；先部署可同时接受 `old,new`（首次部署只有 `new`）且使用新 namespaced key 的 Python。
2. 验证 `/health`、内部 token 401/成功、Redis 隔离/TTL和五类故障注入，再部署使用专用配置、内部 DTO和稳定 fallback 的 Java。
3. 部署用户端 Axios/AgentChat/支付映射和管理端成功码修复。
4. 若是轮换，确认 Java 全实例使用新 token 后再删除 Python 的 old token。

Python 必须先于 Java，因为新 Java 会发送新 schema/认证；新 Python 与旧 Java 不兼容时应 401/422 fail closed，不能接受无认证旧请求。

### 7.2 Redis key 迁移

- 无数据迁移、无扫描、无删除。新代码只读写新 key；旧 key 依 TTL 自然过期。
- 上线前记录旧 TTL 上界；观察期至少覆盖该上界，期间不得加入回退读取。
- 回滚到旧 Python 会重新启用无 userId key 和无内部认证，是不安全回退，不允许在可接收流量时执行。若必须回滚 Python，先关闭 Java Agent 入口或撤离流量，再恢复一个仍保留认证和 namespaced key 的修复版本。

### 7.3 应用与前端回滚

- Java 回滚而 Python 保持新版本时，旧 Java 因缺内部 token 被 Python 401 拒绝，聊天不可用但不会绕过认证；这是可接受的 fail-closed 回滚状态。
- 前端可独立回滚，但旧 AgentChat 发送的 `userId` 会被新 Java 忽略，session 不合规时得到 422；为避免体验回退，优先修复前滚。
- 新版前端首次加载必须清除存量 16 字符 sessionId；该动作只丢弃旧的无所有权保证会话标识，与“不读取旧 Redis key”一致。发布验证需从带旧 localStorage 的浏览器状态开始，证明无需人工清缓存即可恢复聊天。
- Axios 统一只改变实例来源，不改变业务 URL/export。若某 API 回归，可回滚该前端构建；不得以恢复重复鉴权逻辑作为长期兼容方案。
- 支付映射回滚只影响展示，不得伴随后端状态写路径；任何回滚版本仍必须保留 B1 同步回跳零写入。

### 7.4 B8 协调

- 开始实现前重新获取最新 master，比较 B8 对 `application.yml`、文档索引和共享配置的已合并变更。
- B9 只追加 `fashion.agent.*`，不改写 B8 的 ES 配置、同步任务或测试证据；冲突逐行人工解决并重新运行双方相关验证。
- B8 未合并时，B9 worktree 不拷贝其未提交/未合并文件；最终集成由后续 rebase/merge 基于远端提交完成。

## 8. Verification gates

Design 确认后的 workpack 必须把每条 Issue #21 AC 映射到可执行测试。最低门禁如下：

### 8.1 Java TDD

- Controller：公开 DTO 仅 message/sessionId；伪造 userId/token 被忽略且不进入内部 DTO；未登录 HTTP 401；空白/超长 message 和非法 session HTTP 422；Long 最大边界不缩窄。
- session：缺失时生成 Base64URL 22 字符且使用 `SecureRandom` 可替换源验证 128-bit 输入；已有合法值保持；fallback 仍返回有效值。
- 配置：无硬编码 localhost；base URL 尾斜杠 0/1/多条均得到唯一 `/chat`；非法 URL、非回环 HTTP、生产 profile 的 insecure 开关、空 token、非正/超上限超时 fail closed；回环/显式测试 profile 与 HTTPS 成功；专用 client 不改变共享 client。
- 内部认证：正确 header/独立内部 DTO；浏览器字段不转发；日志捕获测试确认 JWT、内部 token、message、完整响应不出现。
- Service：Python 正常、空 body、超时、拒绝、非 2xx、坏 schema、未知降级枚举、重复原因，以及“格式合法但与请求不同”的 sessionId；响应 invariant 始终成立且 fallback 保留原 sessionId。
- 执行 Java 聚焦测试，并在 `backend/` 新鲜执行完整 `mvn test`。

### 8.2 Python TDD

- `/health` 无认证可用；`/chat` 缺失/错误 token 为 401 且 Redis/graph 零调用；正确 old/new token 成功；非法 DTO 为 422。
- session/user schema：int64 userId、session 正则和必填；不接受浏览器式 token 字段冒充应用认证。
- Redis mock 单元测试和真实 Redis 7 集成测试：A/B 同 session 完全隔离，history/slots/clear 均带 userId；history 的 push/trim/expire 原子执行，slots 使用 `SET EX`；TTL 接近配置值并在写后刷新，读取不刷新；注入原子写失败后不得出现新的无 TTL key；不存在旧 key fallback。
- 故障注入：Redis read/write、ES search/recommend、LLM intent/reply、Java order/tracking、graph 顶层异常分别得到稳定 schema和正确原因；组合故障返回排序去重原因。
- 商品坏数据：非 dict、缺 id/name/price/image、错误类型逐条丢弃，合法条目保留，整次请求不 500。
- 安全日志捕获：消息、JWT、内部 token、完整外部响应均不存在；只出现依赖、异常类和 trace/session 标识。
- 在 `agent-service/` 新鲜执行 `python -m pytest -q`。

### 8.3 前端与契约检查

- 静态边界检查只作为辅助：`src/api/*.js` 不再导入 axios/创建实例/注册拦截器/读写 token；不能代替构建和行为测试。
- 对统一实例进行可运行的最小行为测试（若 workpack 增加测试工具）：Token 注入、401、`skipAuthRedirect`、FormData boundary/30 秒覆盖；若不引入前端测试框架，必须用构建加人工/浏览器故障步骤如实记录，不能宣称单测通过。
- AgentChat 行为：未登录不请求；请求不含 userId/token；首轮无 session；存量 16 字符/非法 localStorage session 自动清除；保存服务端 session；仅 `INVALID_SESSION_ID` 422 清理并最多重试一次，其他 422 不重试；降级 reply/products 展示；401/422 分流。
- 管理端秒杀查询/确认/取消/删除均只认 code 1；code 0 不显示成功。
- 支付映射覆盖 0/1/2/3/-1；验签和查询共用映射；只排除 orderId，重复 query key fail closed；确认没有支付写 API。
- 分别执行用户端和管理端 `npm run build`。当前没有 test/lint/typecheck 脚本，evidence 必须明确写“未提供”，不能写 PASS。

### 8.4 真实依赖与故障注入

- 真实 Redis 7 使用隔离的测试实例/DB，记录镜像版本、端口、非生产 key 前缀和清理边界；证明 A/B 会话隔离、TTL、重启后读取及旧 key 不回退。
- ES、LLM、Java 工具和 Python 服务分别通过不可达端口、确定性 stub 或受控超时注入；记录期望最大收敛时间，不能用 sleep 模糊证明。
- Python 服务不可达验证必须经过真实 Java HTTP client 超时路径；Mock 只作为单元证据，不能冒充真实故障注入。
- 真实凭据只从环境注入，证据中打码；测试完成后不提交 `.env`、profile 私密配置或日志原文。

### 8.5 完成前证据

- 独立只读实现 Review，最终 P0/P1/P2 均为 0；P3 记录但不阻断。
- 新鲜运行 `git diff --check`、限定 B9 范围 diff、未跟踪文件检查和敏感信息扫描。
- `evidence.md` 逐条关联 AC、红—绿—重构记录、命令、退出码、真实依赖条件、故障注入结果和阻塞项。
- 只有上述证据齐全才可标记“本地已验证”；B0-AC6、B10、B11 及 B9 本身仍是生产发布门禁，不宣称可部署。

## 9. Decisions requiring user confirmation

1. 确认 Java 是外部 sessionId 的唯一生成/校验边界：128-bit `SecureRandom` Base64URL（22 字符），Python 内部 DTO 要求必填；会话 TTL 默认 7 天。
2. 确认 Java→Python 使用独立 `X-FSM-Agent-Token`，Python 支持最多两个活动 token 完成先扩后缩轮换；用户 JWT 仅作为内部 DTO 的受保护委托字段供现有 Java 工具使用。
3. 确认降级响应采用 `degraded + degradationReasons[]`，允许多原因并限定为五个依赖枚举；认证/输入错误保持 401/422，不进入降级 schema。
4. 确认旧无 userId Redis key 不迁移、不回退读取；回滚旧 Python 前必须先撤离/关闭 Agent 流量，不能以恢复服务为由重新开放不安全 key 或无认证 `/chat`。
5. 确认支付状态只做前端五态解释和有限轮询；继续保持 B1 的同步回跳零写入，且支付宝验签参数只排除本站 `orderId`。

除以上架构选择外，没有尚待新增的业务范围决定；匿名会话、mTLS、新工具及生产轮换继续留在 B9 范围外。

## 10. Independent review

- Reviewer: 独立只读 reviewer `b9_design_review`
- Round 1 verdict: FAIL（2026-09-04）
- Round 1 findings: P0=0，P1=2，P2=2，P3=0。
  - P1：Redis value/list 写入与 TTL 非原子，可能留下永久会话数据。已在 D5、失败处理和验证门禁中改为 history 原子 Lua/事务、slots `SET EX`，并增加“失败不产生无 TTL key”证据。
  - P1：未治理浏览器存量 16 字符 sessionId，升级后会反复 422。已在 D10、兼容/回滚和前端验证中增加本地预清理及仅针对稳定错误码的一次无 session 重试。
  - P2：允许非回环 HTTP 与 TLS 声明矛盾。已要求非回环 HTTPS，insecure HTTP 仅限显式测试 profile且生产禁止。
  - P2：未明确 Python 响应 sessionId 必须等于请求值。已加入相等 invariant、fallback 规则和测试。
- Round 2 verdict: PASS（2026-09-04）
- Round 2 findings: P0=0，P1=0，P2=0，P3=0；首轮四项均关闭，未发现新增问题。
- Design gate: P0/P1 必须为 0 后才可交用户确认；实现最终门禁另要求 P2 为 0。
