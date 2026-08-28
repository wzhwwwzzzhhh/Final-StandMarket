# B0-credential-log-safety · Workpack plan

> Status: 外部证据阻塞（代码审查 PASS）
> Requirement source: [阶段 B：B0 密码、Token 与日志安全](../../plans/阶段B-P0P1交易链路修复.md#b0密码token-与日志安全p0)
> Execution tracker: [GitHub Issue #4](https://github.com/wzhwwwzzzhhh/Final-StandMarket/issues/4)
> Design: 无；阶段 B 已确认本项安全边界，当前调查未发现需要新增高风险设计选择

## Execution isolation and preconditions

- 当前 `master` 工作区已有 10 个 B1 支付文件的未提交修改。本计划阶段只新增/修改 workpack 文档，不修改产品代码。
- B0 的支付回调日志验收会触及当前 B1 已修改的 `PayNotifyController.java`。计划确认后，从干净 `HEAD` 创建独立 `codex/b0-credential-log-safety` 分支与 worktree，在其中复制已确认 workpack 并执行 B0；当前工作区及其 B1 diff 保持原样。
- B0 worktree 建立后，先保存并比对当前 B1 的 `git diff --name-only` 和 `git diff` 摘要；B0 交付 diff 不得包含下列 B1 业务改动文件（B0 独立 worktree 中对 `PayNotifyController.java` 的改动仅限敏感日志删除，必须单独复核）：
  - `backend/fashion-server/src/main/java/com/fashion/controller/admin/OrderController.java`
  - `backend/fashion-server/src/main/java/com/fashion/controller/notify/PayNotifyController.java`
  - `backend/fashion-server/src/main/java/com/fashion/controller/user/PaymentController.java`
  - `backend/fashion-server/src/main/java/com/fashion/mapper/OrderMapper.java`
  - `backend/fashion-server/src/main/java/com/fashion/mapper/PaymentMapper.java`
  - `backend/fashion-server/src/main/java/com/fashion/service/PaymentService.java`
  - `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderServiceImpl.java`
  - `backend/fashion-server/src/main/java/com/fashion/service/impl/PaymentServiceImpl.java`
  - `backend/fashion-server/src/main/resources/mapper/OrderMapper.xml`
  - `backend/fashion-server/src/main/resources/mapper/PaymentMapper.xml`
- 未经另行明确授权，不执行 commit、push、创建 PR、合并或修改远程仓库设置。

## Scope

### In scope

- 用户信息、管理端用户详情、员工分页/详情统一返回不含密码字段的安全 VO；实体上的密码字段增加序列化防御，避免未来误返回时泄漏。
- 用户和管理员登录写入 Redis 的 Hash 改为显式白名单字段：用户只保留 `id/name/phone/avatar`，管理员只保留鉴权和展示必需的 `id/name/username/phone`；不再从完整实体自动转 Map。
- 用户资料更新改用白名单 DTO/Mapper，明确忽略请求中的 `password`；设置密码和修改密码都走专用请求体与专用 Mapper 更新，并统一生成 BCrypt 哈希。
- 管理端新增用户/员工时的初始密码统一 BCrypt；普通用户/员工资料更新路径不得写密码。修正员工新增 SQL，确保用户名、BCrypt 密码及实体中实际存在的字段正确入库。
- 删除用户登录、验证码、Token、完整用户对象/Map、登录返回对象中的敏感日志；操作日志切面对嵌套对象、Map、集合和序列化失败路径安全脱敏。
- 支付宝异步通知日志不记录完整回调参数，仅记录必要的非敏感关联标识和失败类别；该项在独立 B0 worktree 中实现，避免吸收当前 B1 diff。
- 用户设置页使用服务端 `hasPassword` 布尔值判断“设置/修改密码”，两种情况统一调用专用密码接口；密码改为 JSON 请求体，不进入 URL 查询参数。
- 将仓库跟踪配置中的固定 JWT 默认密钥改为外部配置占位符；检查仓库历史与当前跟踪文件中的凭据风险。
- 将 `mysql/final07.sql` 中用户和员工演示账号的明文密码替换为 BCrypt 哈希，避免新初始化环境继续导入明文密码。B10 的 Flyway 基线和既有数据库密码迁移不在本包内。
- 按下表登记 OSS、支付及其他可能共享凭据的负责人、目标系统与阻塞状态；只有拿到实际平台轮换证据才可标记相应项完成。

### External credential rotation register

| Target system / credential | Owner | Current status | Blocker / required evidence |
|---|---|---|---|
| 后端 JWT 管理端/用户端签名密钥 | 项目维护者 | 待处理 | 需确认各运行环境的密钥注入方式、生成新随机值并提供部署/失效旧会话证据 |
| 阿里云 OSS AccessKey | 项目维护者 | 外部核查阻塞 | 当前无阿里云控制台权限；需确认是否曾共享并提供 AccessKey 轮换/禁用旧 Key 证据 |
| 支付宝应用私钥及关联配置 | 项目维护者 | 外部核查阻塞 | 当前无支付宝开放平台权限；需提供证书/密钥轮换及回调验证证据 |
| 微信 App/商户密钥与 API v3 Key | 项目维护者 | 外部核查阻塞 | 当前无微信平台权限；需提供轮换及旧凭据失效证据 |
| AI Provider API Key | 项目维护者 | 外部核查阻塞 | 当前无提供商控制台权限；需确认本地配置是否曾共享并提供轮换证据 |
| MySQL、Redis、RabbitMQ 运行凭据 | 项目维护者 | 待核查 | 需确认被共享范围及运行环境；若已共享，提供轮换和服务重连证据 |

### Out of scope

- B1-B11 的支付状态机、订单库存、退款、资源归属、秒杀、MQ、缓存、AI 会话、Flyway 和阶段级交付实现。
- 支付通知的签名、金额、`app_id`、订单类型或状态迁移逻辑；B0 只改日志内容。
- 新增密码找回、短信重置、管理员代重置、强制改密、密码复杂度策略或历史密码迁移 API。
- 对既有生产/共享数据库执行批量密码迁移；本包只阻止新写入明文，并修正初始化 SQL。既有数据迁移应在 B10 版本化迁移或单独确认的运维方案中处理。
- 未获平台权限时替用户执行 OSS、支付宝、微信、AI、数据库、Redis 或 RabbitMQ 凭据轮换。
- 前端 Axios 全局实例统一（属于 B9）；本包只调整 `user.js` 的密码请求契约。
- commit、push、PR、CI、merge 和生产发布。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B0-AC1 | `/user/me`、管理端用户分页/详情、员工分页/详情均使用安全 VO；JSON 中不存在 `password`，同时保留页面需要的非敏感字段和 `hasPassword` 布尔值 | Controller/序列化聚焦测试断言响应字段；Mapper/Service 测试断言 VO 映射；前后端构建 |
| B0-AC2 | 用户与管理员登录 Redis Hash 只写显式最小字段，不含密码、身份证号、openid、Token 或完整实体 | Mockito 捕获 `putAll` 参数并按精确 key set 断言；登录返回对象序列化断言不含密码 |
| B0-AC3 | 注册、管理端新增、首次设置和修改密码均写入 BCrypt；旧密码校验只接受 BCrypt；资料更新即使收到伪造 `password` 也不写密码 | 先写失败测试，覆盖注册/新增、首次设置、正常修改、错误旧密码、资料更新注入密码、Mapper 专用更新；SQL 静态检查不再含明文演示密码 |
| B0-AC4 | 应用和操作日志不记录验证码、Token、密码哈希、私钥、完整用户对象或完整支付回调；序列化失败也不会退回 `toString()` 泄漏 | 日志捕获/脱敏单元测试覆盖嵌套 DTO、Map、集合及失败回退；支付回调日志测试；源码敏感日志模式复核 |
| B0-AC5 | 设置页只依赖 `hasPassword`，设置与修改都使用 JSON 专用密码接口，资料更新不再承载密码 | 后端请求契约测试；`Settings.vue` 行为复核；用户端 `npm run build` |
| B0-AC6 | 跟踪配置不含固定 JWT 默认密钥；仓库秘密扫描无新增泄漏；外部凭据逐项有真实轮换证据或明确保持阻塞 | `gitleaks git . --redact --no-banner`（CLI 可用时）/GitHub Secret Scan（仅远程交付获授权后）；配置检查；轮换登记与平台证据复核 |

## Slices

### Slice 1 — Safe response and minimal session identity

1. 先新增失败测试，证明当前用户详情、管理员用户详情、员工列表/详情和 Redis Hash 会暴露密码或过量字段。
2. 新增安全响应 VO，并把 Controller/Service 返回类型和映射收口到 VO；实体密码字段加序列化防御。
3. 登录会话从完整实体自动映射改为显式最小字段 Map；保持现有拦截器只依赖 `id` 的行为。

### Slice 2 — BCrypt-only password lifecycle and profile boundary

1. 先新增失败测试覆盖首次设置、修改、注册/管理端新增、伪造资料更新密码及员工新增 SQL。
2. 引入专用密码请求 DTO 和 Mapper `updatePassword`，资料 Mapper 只允许白名单字段；移除明文密码兼容登录。
3. 修正员工新增 SQL 和初始化 SQL 的明文演示密码；更新设置页与 API 契约。

### Slice 3 — Sensitive logging and credential evidence

1. 先新增失败测试，捕获当前验证码、Token、完整对象、操作日志嵌套敏感值和完整支付回调泄漏。
2. 收敛业务日志并实现结构化递归脱敏与安全失败回退；在独立 B0 worktree 中只修改支付回调日志，不改 B1 状态逻辑。
3. 外部化固定 JWT 默认密钥，运行秘密扫描；逐项更新轮换登记。无平台证据的项目保持 `外部核查阻塞`，不得算作完成。

## File-level change surface

以下是计划变更面；实现中若需新增超出 B0 的产品文件，先更新本计划并重新确认。

| Area | Planned files | Purpose |
|---|---|---|
| POJO / contracts | `User.java`, `Employee.java`, `UserInfo.java`, `UserUpdateDTO.java`; 新增 `UserSafeVO.java`, `EmployeeSafeVO.java`, `PasswordChangeDTO.java`（最终命名以测试驱动的最小实现为准） | 密码序列化防御、安全响应、`hasPassword`、白名单资料和专用密码请求 |
| User backend | `UserService.java`, `UserServiceImpl.java`, `UserController.java`, `UserAdminController.java`, `UserMapper.java`, `UserMapper.xml` | 安全返回、最小 Redis Hash、BCrypt-only、资料/密码写路径分离 |
| Employee backend | `EmployeeService.java`, `EmployeeServiceImpl.java`, `EmployeeController.java`, `EmployeeMapper.java`, `EmployeeMapper.xml` | 安全列表/详情、最小 Redis Hash、BCrypt 初始密码、员工 SQL 字段修正 |
| Logging / config | `OperationLogAspect.java`, `PayNotifyController.java`, `application.yml` | 递归脱敏、安全失败回退、移除完整回调日志、外部化 JWT 密钥 |
| User frontend | `frontend/fashion-client/src/views/Settings.vue`, `frontend/fashion-client/src/api/user.js` | 使用 `hasPassword`；设置/修改统一走 JSON 密码接口 |
| Seed data | `mysql/final07.sql` | 演示用户/员工密码改为 BCrypt，避免新环境导入明文 |
| Tests | 新增或更新 `UserServiceImplTest.java`, `EmployeeServiceImplTest.java`, `UserControllerTest.java`, `UserAdminControllerTest.java`, `EmployeeControllerTest.java`, `OperationLogAspectTest.java`, `PayNotifyControllerTest.java` | TDD 覆盖 AC1-AC5；不复用现有 B1 未提交实现作为 B0 证据 |
| Workpack | 本目录 `plan.md`, `review.md`, `evidence.md`; `docs/workpack/README.md` | 计划、独立审查、证据与状态同步 |

## Risks and rollback

- **B1 污染风险**：`PayNotifyController.java` 与 B1 重叠。通过独立 B0 worktree、逐文件 diff 和仅日志改动复核隔离；不在当前脏工作区实现产品代码。
- **会话兼容风险**：Redis Hash 字段收窄后，旧会话仍可能含历史字段。部署/验证时应使旧登录会话失效或清理精确的登录 Key 前缀，并要求重新登录；不得对整个 Redis 执行广泛清空。
- **API 兼容风险**：响应从实体变为 VO，设置页不再读取 `password`。保留页面当前需要的字段，并通过两端构建和 Controller 契约测试验证。
- **密码兼容风险**：移除明文兼容后，既有明文账号将无法密码登录。初始化 SQL 改为 BCrypt；既有数据库批量迁移不在本包内，交付时必须记录残余风险，不能宣称存量已迁移。
- **SQL 风险**：`final07.sql` 是初始化快照，替换演示密码只影响新导入环境，不对现有数据库执行 DDL/DML。回滚可恢复该文件，但会重新引入明文风险，因此仅用于诊断，不作为可接受的长期回滚。
- **密钥轮换风险**：先部署可读取新密钥的外部配置，再轮换/禁用旧凭据；缺少平台权限或证据时保持阻塞，不修改虚构状态。
- **日志可观测性风险**：脱敏后仍保留请求路径、操作类型、主体 ID/订单关联标识和异常类别，避免把排障信息全部删除。
- 所有本地代码回滚只针对 B0 独立分支/worktree 的文件；不得 reset、stash、覆盖或还原当前 B1 工作区。

## Verification commands

### RED/GREEN focused backend tests

在 `backend/` 执行（测试类按最终新增文件调整）：

```powershell
mvn -pl fashion-server -am -Dtest=UserServiceImplTest,EmployeeServiceImplTest,UserControllerTest,UserAdminControllerTest,EmployeeControllerTest,OperationLogAspectTest,PayNotifyControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

每个切片先记录对应测试失败原因（RED），再执行最小实现并记录 GREEN；不得以编译失败冒充有效 RED。

### Full local verification

```powershell
cd backend
mvn test

cd ../frontend/fashion-client
npm run build

cd ../fashion-admin
npm run build

cd ../..
git diff --check
git status --short
git diff --name-only
git diff -- backend/fashion-server/src/main/java/com/fashion/controller/notify/PayNotifyController.java
```

### Sensitive-data verification

```powershell
gitleaks git . --redact --no-banner
rg -n --glob '!**/target/**' --glob '!**/node_modules/**' '发送验证码：|生成的token|Redis中的验证码|输入的验证码|用户信息Map|查询到的用户|回调验签失败.*params' backend
rg -n "'123456'|password.*123456|123456.*password" mysql backend --glob '*.sql' --glob '*.xml' --glob '*.java'
```

- 当前主机尚未安装 `gitleaks` CLI；执行阶段可安装/使用可信的等价运行方式，若仍不可用则在 `evidence.md` 记录精确阻塞。只有获授权推送后，GitHub Secret Scan 的真实结果才能作为远程证据。
- 日志验收以自动化日志捕获测试和源码复核共同证明；`rg` 只用于发现已知危险模式，不能单独证明“所有日志安全”。
- 最终复核 B0 worktree 的 diff 仅包含本计划文件面；当前原工作区的 B1 diff 必须与计划确认时基线一致。
