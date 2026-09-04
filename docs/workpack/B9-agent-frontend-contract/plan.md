# B9-agent-frontend-contract · Workpack plan

> Status: 本地已验证（2026-09-04）
> Requirement source: [阶段 B B9](../../plans/阶段B-P0P1交易链路修复.md#b9ai-服务与前端契约治理p1) / [GitHub Issue #21](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/21)
> Design: [B9 AI 服务与前端契约治理 · 已确认](../../design/agent/B9-agent-frontend-contract-design.md)
> Branch: `codex/b9-agent-frontend-contract`
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Updated: 2026-09-04

## 1. Delivery objective

按已确认 Design 收紧浏览器→Java→Python 的身份和会话边界，为 Redis、ES、LLM、Java 工具和 Python 服务故障提供稳定降级契约，并统一用户端 Axios、AgentChat、管理端秒杀成功码与支付回跳五态解释。所有行为按测试先行实现，最终以真实 Redis 7、完整后端/Python测试和双前端生产构建留证。

## 2. Preconditions and guardrails

- 计划确认前不修改 `backend/`、`agent-service/`、`frontend/`、`mysql/` 或运行会改变外部状态的命令。
- 实现开始前重新 fetch 最新远端 master，并检查 B8 是否已合并；只在 B9 worktree 集成远端提交，不复制 B8 worktree 的未提交文件。
- B8 当前独立 worktree 为 `b8-product-cache-consistency`，检查时仅有未跟踪 Design，尚未登记 workpack。后续若 B8 先登记或合并，B9 必须保留其 `application.yml`、`docs/workpack/README.md` 和索引修改。
- 主工作区的 `.agents/skills/fsm-development-workflow/SKILL.md`、`AGENTS.md`、`docs/开发规范.md`、`docs/prototypes/` 不修改、不暂存、不清理、不回退。
- 不提交 `.env`、`application-dev.yml`、`application-prod.yml`、真实内部 token、用户 JWT、Redis 密码或故障日志原文。
- 本 workpack 不执行生产凭据轮换、生产 Redis 操作、部署、commit、push、PR 或 merge。

## 3. Scope

### In scope

- Java 浏览器 DTO/内部 DTO 分离、Long 用户身份、服务端 session、专用 Agent 配置/HTTP 客户端、内部认证、安全 fallback 与日志。
- Python `/chat` 内部认证、schema、按用户隔离的 Redis history/slots、原子 TTL、五类降级原因、坏商品过滤和安全日志。
- 用户端全部 `src/api/*.js` 复用统一 request，保留 Token/401/`skipAuthRedirect`/上传能力；AgentChat 使用服务端会话。
- 管理端秒杀视图只认 `code === 1`；支付回跳复用 `0/1/2/3/-1` 只读映射并保留验签参数。
- Java、Python、两套前端的行为测试/构建，以及真实 Redis 7 和受控故障注入证据。
- 为严格执行前端 TDD，在两套前端添加仅开发期的 Vitest、Vue Test Utils、jsdom、测试脚本和最小测试配置。plan 确认即同时确认这项测试配置例外；不增加生产依赖或改变生产 bundle 契约。

### Out of scope

- 匿名聊天、Prompt/推荐质量、新工具、向量搜索和 Python 全异步改造。
- B8 的 ES 同步/缓存一致性实现；B9 只消费 ES 故障语义。
- mTLS、Gateway、生产网络策略、用户 JWT 体系重构。
- MySQL schema/数据迁移、支付/退款状态机、支付通知或同步回跳写入。
- 管理端非秒杀页面的 Axios/返回码重构。
- 生产 Redis key 扫描、旧 key 迁移/删除、生产凭据轮换和部署。

## 4. Acceptance mapping

| AC | Planned behavior | Primary verification |
|---|---|---|
| AC1 Agent 配置 | `base-url` 只来自配置，尾斜杠规范化；专用 connect/read timeout 有限可覆盖；非回环强制 HTTPS，无硬编码 localhost | Java 配置测试覆盖 0/1/多尾斜杠、非法 URL、HTTP/HTTPS/profile、超时边界；源码范围复核仅作辅助 |
| AC2 浏览器 DTO | 公开请求只有 message/可选 sessionId；伪造 userId/token 被忽略且不进入内部 DTO | MockMvc/Controller 行为测试捕获传给 Service 的新内部 DTO；非法输入 422 |
| AC3 Long 与用户委托 | `BaseContext<Long>` 不缩窄；Java 只用已认证 Authorization 构造内部 DTO且不记录 JWT | Long 大值测试、header 捕获和日志捕获测试 |
| AC4 内部认证 | Java 发送环境注入 `X-FSM-Agent-Token`；Python 缺失/错误 401、正确才执行；双 token 轮换、无秘密默认值 | Python API 测试证明错误凭据时 graph/Redis 零调用；Java header/配置 fail-closed 测试；轮换测试 |
| AC5 登录与 health | AI 仅登录用户可用；`/health` 无认证且不泄露配置 | Java 401 集成测试；Python `/health` 与 `/chat` 对照测试 |
| AC6 session | Java 生成 128-bit Base64URL；外部格式门禁；响应始终有效且等于请求；存量 16 字符会话自动恢复 | Java session/fallback/响应漂移测试；AgentChat 旧 localStorage 和一次重试测试 |
| AC7 Redis 隔离/TTL | history/slots key 绑定 `(userId,sessionId)`；history 原子 push/trim/expire，slots `SET EX`；旧 key 无回退 | 单元测试 + 真实 Redis 7 A/B 隔离、TTL、clear、重启和旧 key 哨兵验证 |
| AC8 稳定 schema | 正常/降级均 reply/session 非空、products/reasons 非 null；`degraded` 与有限原因集合一致 | Java/Python schema 参数化测试；401/422 不进入降级 schema |
| AC9 五类故障 | Redis 无记忆、ES 空商品、LLM 规则回复、Java 工具不可用、Python 服务不可用均在超时预算内收敛 | 独立及组合故障注入；Java 真实 HTTP 不可达/超时；记录耗时和原因 |
| AC10 坏商品 | 单条无效/缺字段商品安全丢弃，合法条目保留，整次请求不 500 | Python 参数化行为测试和 Java 坏 schema fallback 测试 |
| AC11 安全日志 | 仅依赖名、异常类、trace/session 等诊断字段；无正文/JWT/内部 token/完整响应 | Java/Python日志捕获测试 + 最终敏感扫描 |
| AC12 统一 Axios | 12 个用户端 API 文件复用 `src/utils/request.js`，无重复实例/拦截器；上传与匿名兼容 | Vitest 行为测试覆盖 Token、401、`skipAuthRedirect`、FormData/30秒；静态扫描辅助；生产 build |
| AC13 AgentChat | 不发送 userId/token；未登录引导；首轮无 session并保存服务端返回值；展示降级 | Vue Test Utils 组件测试覆盖请求 payload、401/422、旧 session、降级和商品；用户端 build |
| AC14 管理端 code | 秒杀查询、确认、取消、删除只认 code 1，失败不显示成功 | Vue Test Utils 组件测试至少覆盖删除 code 0/1，并覆盖共享成功判定；管理端 build |
| AC15 支付回跳 | 0/1 pending轮询、2成功、3失败、-1未完成；验签/查询共用映射；只排除 orderId且零写入 | Vitest 五态表驱动测试、重复 query key fail-closed、API spy 证明只调用 verify/status；B1 后端回归测试 |
| AC16 完整验证 | Java聚焦+完整 `mvn test`、Python完整 pytest、真实 Redis 7、双前端 build、范围/敏感检查 | `evidence.md` 保存新鲜命令、退出码、版本、依赖条件和阻塞项 |
| AC17 独立审查 | 独立只读实现 Review PASS，P0/P1/P2=0，AC 证据完整 | `review.md` 记录每轮 findings 与关闭证据；最终复审后新鲜重跑 |

## 5. Slices and TDD order

### Slice 1 — Java/Python 可信 Agent 契约与依赖降级

包含 AC1–AC11，是一个跨服务但不可拆开的身份/会话/降级契约。

#### RED

1. Java 先新增/改写聚焦测试，证明当前代码仍：接受公开 userId/token、Long→Integer、硬编码 URL、缺少内部 token、生成/返回错误 session schema、超时 fallback 为 null/空。
2. 每个测试单独运行，记录预期失败断言；编译错误只在“期望的新 API 尚不存在”时作为第一步 RED，随后尽快收敛为行为失败。
3. Python 先写认证、schema、Redis namespacing/TTL、降级和坏商品测试；逐个运行并确认因当前缺口失败，而非环境/导入错误。

#### GREEN

1. 最小拆分 Java DTO，增加 session generator、Agent properties/专用 client和稳定 response；只实现当前红测所需行为。
2. 最小实现 Python内部认证、namespaced Redis API、原子写与统一原因集合；逐个让红测转绿。
3. 再分别实现 Redis、ES、LLM、Java 工具、Python顶层故障收敛；不加入同步自动重试或新业务工具。

#### REFACTOR

- 仅在聚焦测试全绿后去重校验、原因排序和日志辅助代码；每次重构后重跑对应 Java/Python 聚焦测试。
- 使用真实 Redis 7 运行隔离/TTL集成测试；Mock 单元测试不得替代此步骤。

### Slice 2 — 用户端统一请求与 AgentChat 会话接管

包含 AC12–AC13，并承接 Slice 1 的公开 schema。

#### RED

1. 先添加用户端测试开发依赖/脚本/最小配置，再写统一 request 行为测试；确认重复 API 当前无法满足统一拦截器契约。
2. 写 AgentChat 组件测试，确认当前仍生成/发送 userId和16字符 session、忽略服务端 session、未按稳定 422 恢复。

#### GREEN

1. 将每个 `src/api/*.js` 逐个切换到统一 request，保持 URL、method、params/data、export 不变；上传使用单请求 timeout并由 FormData 自动设置 boundary。
2. 最小修改 AgentChat：登录前不请求、payload 无身份字段、清理旧 session、保存服务端 session、仅 `INVALID_SESSION_ID` 最多重试一次、显示降级 reply/products。

#### REFACTOR

- 统一公开/匿名 API 的单请求 config 传递，删除重复 interceptor/router/token 代码；重跑用户端单测与 build。

### Slice 3 — 管理端成功码、支付五态与整体验证

包含 AC14–AC17，是前端契约收口和交付门禁。

#### RED

1. 管理端组件测试证明 `delete code=0` 当前错误显示成功，`code=1` 当前不进入删除成功分支。
2. 用户端支付测试证明同步验签结果的 0/1 当前被展示为失败，3/-1 缺少明确映射；验证参数重复键当前会被改写/丢弃。

#### GREEN

1. 管理端所有秒杀成功分支统一 `code === 1`，失败不刷新为成功。
2. 提取/复用支付五态只读映射；验签和查询使用同一逻辑，仅排除本站 `orderId`，重复 query key fail closed；不增加任何写 API。

#### REFACTOR / DELIVERY

- 重跑双前端测试/构建、B1 支付后端回归、Java/Python全量、真实Redis和全部故障注入。
- 独立只读实现 Review；修复 P0/P1/P2 后再次复审并新鲜重跑全部相关验证。

## 6. Planned file-level change surface

### Java

- `backend/fashion-pojo/src/main/java/com/fashion/dto/AgentChatRequest.java`
- 新增内部请求 DTO、降级原因/响应字段所需 POJO（精确路径实现时按现有模块边界确定）
- `backend/fashion-server/src/main/java/com/fashion/controller/user/AgentController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/AgentService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/AgentServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/config/` 下 Agent 专用 properties/client/422 映射
- `backend/fashion-server/src/main/resources/application.yml` 的最小 `fashion.agent.*` 占位配置
- `backend/fashion-server/src/test/java/com/fashion/` 下 Controller/Service/Config/日志聚焦测试

### Python

- `agent-service/app/config.py`、`schemas.py`、`main.py`、`redis_memory.py`
- `agent-service/app/agent/graph.py`、`agent/nodes.py`
- `agent-service/app/tools/search_product.py`、`tools/recommend.py` 及 Java 工具调用路径
- `agent-service/tests/` 下 B9 API/Redis/降级/日志测试；真实 Redis 测试必须由显式环境变量启用且连接失败时报告 blocked，不静默 skip 为 PASS

### 用户端

- `frontend/fashion-client/src/utils/request.js`
- `frontend/fashion-client/src/api/*.js`
- `frontend/fashion-client/src/components/AgentChat.vue`
- `frontend/fashion-client/src/views/PayResult.vue` 及必要的纯映射 helper
- `frontend/fashion-client/package.json`、lockfile、测试配置和行为测试

### 管理端

- `frontend/fashion-admin/src/views/SeckillOrderList.vue`
- `frontend/fashion-admin/package.json`、lockfile、测试配置和行为测试

### 文档

- 本 workpack 的 `plan.md`、`review.md`、`evidence.md`
- `docs/workpack/README.md`
- 已确认 Design 仅更新确认状态；若实现发现新架构歧义，停止并回到 Design，而不是静默改写决策

## 7. Repeatable dependency setup

### 7.1 Toolchain preflight

记录但不猜测：

```powershell
java -version
mvn -version
python --version
node --version
npm --version
docker version
git status --short
```

Node/Python/Maven依赖下载需要网络时另行请求执行权限；下载失败如实记入 evidence。

### 7.2 Disposable real Redis 7

优先使用隔离容器，不复用生产/共享 Redis。执行前确认容器名和端口均未占用，不自动删除同名资源：

```powershell
$env:B9_REDIS_PASSWORD = '<local-only secret injected outside git>'
docker run -d --name fsm-b9-redis --restart=no `
  -p 127.0.0.1:36389:6379 `
  --tmpfs /data:rw,noexec,nosuid,size=128m `
  redis:7.0.15-alpine `
  redis-server --save "" --appendonly no --requirepass "$env:B9_REDIS_PASSWORD"
docker exec fsm-b9-redis redis-cli -a "$env:B9_REDIS_PASSWORD" INFO server
```

测试只连接该容器的 DB 0，并使用随机 session/user 测试值；容器无持久卷。若改用用户已批准的 36379 测试 Redis，必须先证明 `redis_version` 为 7.x、连接目标不是生产、使用隔离 DB/前缀且不会 `FLUSHALL/KEYS *`。真实密码只进环境变量，不写 evidence。

完成后先核对精确容器名/映射，再停止并删除 `fsm-b9-redis`；不删除其他 B6/B8 容器或卷。

### 7.3 Agent processes and test secrets

- 生成一次本地测试专用随机内部 token，仅放环境变量：Java `FASHION_AGENT_INTERNAL_TOKEN`，Python `AGENT_INTERNAL_TOKENS`。
- Python 正常端口建议 `127.0.0.1:38000`；Java测试将 `FASHION_AGENT_BASE_URL` 指向该显式回环地址并使用有限超时。
- 测试证据只记录变量名、长度门禁和已打码 hash 前缀，不记录 token/JWT/message正文。
- `/health`、正确/错误内部 token、Long userId和响应 schema 先用自动化测试验证，再进行端到端请求。

## 8. Fault injection matrix

| Fault | Injection step | Expected result/evidence |
|---|---|---|
| Redis 连接拒绝 | 将测试 Python `REDIS_URL` 指向预先确认未监听的 `127.0.0.1:36910` | HTTP 200；reply/session非空、products数组；`REDIS_UNAVAILABLE`；无未处理500，耗时受Redis超时约束 |
| Redis 读坏 JSON | 在隔离容器新 namespaced history key 写入一个坏 JSON 哨兵 | 坏条目丢弃、其余处理继续；日志无正文；原因按 Design 记录 |
| Redis 原子写失败 | 在单元层让 EVAL/SET EX 抛错；真实容器侧通过断连前后验证新 key不存在或成功 key `PTTL>0` | 不产生新的无 TTL key；响应保留，原因 `REDIS_UNAVAILABLE` |
| ES 不可达 | `ES_HOST=http://127.0.0.1:36911`（执行前确认未监听），分别触发搜索和搭配 | 空 products、稳定 reply、`ELASTICSEARCH_UNAVAILABLE`，两条路径均不500 |
| LLM 拒绝/超时/坏响应 | 测试 transport依次返回503、超过配置timeout、缺choices结构；分别触发意图和回复调用 | 关键词/规则回复非空，原因 `LLM_UNAVAILABLE`，不输出外部响应 |
| Java 工具不可达 | `BACKEND_BASE_URL=http://127.0.0.1:36912` 或确定性503/坏JSON stub，触发订单及物流 | 不猜测订单，reply说明暂不可用，原因 `JAVA_TOOL_UNAVAILABLE`，受5秒内预算约束 |
| Python 连接拒绝 | Java专用 base URL指向确认未监听的 `127.0.0.1:36913` | Java在connect timeout附近返回原session、空products、`PYTHON_AGENT_UNAVAILABLE` |
| Python 读取超时 | 本地stub接受连接但不在read timeout内响应 | Java在read timeout + 小容差内返回同一稳定fallback，无无限等待 |
| Python 401/5xx/坏schema | stub分别返回401、503、空body、未知原因、不同合法session | 全部fail closed为 `PYTHON_AGENT_UNAVAILABLE`；不重试无token请求；保留原session |
| 认证错误 | Python `/chat` 缺header、错token，body含恶意userId/token | 401且Redis/graph/ES/LLM/Java工具零调用；日志无凭据 |
| 输入错误 | Java公开API空/超长message、非法session；Python内部DTO非法 | 422，不进入降级、不触发依赖；仅非法session允许前端一次恢复 |
| 组合故障 | 同时注入Redis+ES+LLM，另测Redis+Java工具 | 原因集合去重、固定排序，schema invariant保持 |

端口只是隔离建议；执行前必须用只读检查确认未被其他工作树/用户进程使用。不得停止占用这些端口的未知进程。

## 9. Rollback and compatibility verification

- Python先部署兼容新认证/key的版本，Java后部署，前端最后部署；首次使用一个新token，轮换使用old/new先扩后缩。
- 回滚Java而保留新Python应得到401/fallback，不能自动关闭内部认证；回滚旧Python前必须关闭Agent流量，因为旧版本会恢复无认证和无用户前缀key。
- 新代码不扫描/迁移/删除旧 `agent:session:*`、`agent:slots:*`。真实Redis测试创建旧key哨兵，证明新请求不读、不改，其TTL自然减少。
- 新前端从16字符localStorage session开始验证自动清理和无session首发；不要求用户手工清缓存。
- Axios统一回滚不能改变API URL/export；上传必须在回滚验证中仍能构造正确multipart boundary。
- 支付前端回滚/修复均不得新增写请求；使用API spy和B1 Controller回归证明同步回跳仍零写入。
- B9无数据库迁移；若 diff 出现SQL、mapper schema或生产队列配置，视为范围漂移并停止。

## 10. B8/shared-file coordination

实现前、最终验证前各执行一次：

```powershell
git fetch <verified-remote> master
git worktree list --porcelain
git status --short
git diff -- backend/fashion-server/src/main/resources/application.yml docs/workpack/README.md docs/design docs/plans
```

- 若B8已合并，先安全整合最新master并人工解决共享文件；不stash/reset主工作区或B8 worktree。
- `application.yml` 只追加 `fashion.agent.*`，保留B8 ES/cache/sync项；README同时保留B8/B9行。
- 若B8尚未合并，B9保持自身最小diff，并在最终报告注明潜在冲突；不复制B8未提交Design/代码。
- 整合后重跑B8可能受共享配置影响的相关测试以及B9完整验证；不能用“无直接代码冲突”替代行为验证。

## 11. Verification commands

精确聚焦类名/文件在RED测试创建后记录到evidence；计划命令如下。

### Java

```powershell
cd backend
mvn -pl fashion-server -am -Dtest=AgentControllerB9ContractTest,AgentServiceImplTest,AgentHttpClientConfigTest test
mvn test
```

若 Maven 多模块 `-Dtest` 导致无对应测试模块失败，使用仓库既有的 `-Dsurefire.failIfNoSpecifiedTests=false` 或在 `fashion-server` 模块执行，并在 evidence 记录真实命令，不把命令配置错误算作RED。

### Python

```powershell
cd agent-service
python -m pytest -q tests/test_b9_api_contract.py tests/test_b9_redis_memory.py tests/test_b9_degradation.py
python -m pytest -q
```

真实 Redis 测试通过显式 `B9_REAL_REDIS_URL` 门禁单独执行并要求实际运行；若依赖不可用，AC7保持 blocked，不能用mock替代。

### User frontend

```powershell
cd frontend/fashion-client
npm test -- --run
npm run build
```

### Admin frontend

```powershell
cd frontend/fashion-admin
npm test -- --run
npm run build
```

本计划确认后会新增 `test` 脚本；当前基线没有 test/lint/typecheck，执行前不得声称这些检查存在。B9不计划新增lint/typecheck，因此最终仍明确记录“未提供”，不写PASS。

### Cross-cutting

```powershell
git diff --check
git status --short
git diff --stat
git diff -- backend agent-service frontend docs
```

- 敏感扫描覆盖已跟踪和未跟踪B9文件，查找真实token/JWT/private key/password/外部响应样本；占位符不算秘密，但必须人工复核。
- 复核无 `mysql/`、RabbitMQ、部署、生产配置和B8产品文件越界修改。

## 12. Evidence protocol and stop conditions

- 每个行为按 `RED命令/期望失败 → GREEN命令/通过 → REFACTOR复跑` 追加到 `evidence.md`，不事后补造RED。
- 配置和测试工具变更以本 plan 的用户确认为TDD配置例外授权；相关业务行为仍必须先有失败测试。
- Mock只证明单元分支；真实Redis和Java→Python真实HTTP超时单独留证。
- 任一真实依赖不可用、完整测试失败、双前端build失败、独立Review有P0/P1/P2或敏感信息命中时，不标记本地完成。
- 实现发现需要改变Design中的身份、session、降级枚举、支付零写入或回滚边界时立即停止，回到Design确认门。
- plan 确认前保持 `待确认`，不修改产品代码。
