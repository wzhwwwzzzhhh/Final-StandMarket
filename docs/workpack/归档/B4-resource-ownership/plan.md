# B4-resource-ownership · Workpack plan

> Status: 已归档（2026-09-01；PR #13 首轮 checks 全绿，等待归档证据提交复检后合并）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B4 / GitHub Issue #12
> Design: 无新增 Design；阶段 B B4 已确认“精确公开路由 + 服务端登录态 + 资源归属条件下沉”的安全边界，本 workpack 不引入新的状态机、迁移或跨服务契约决策
> Baseline: `master` @ `7819d5460d76f0d4be1a8241687704ff66a09a57`
> Plan review: PASS（第二轮独立复审，P0/P1/P2/P3 均为 0；用户已确认）

## Scope

### In scope

- 修正用户登录拦截器的精确公开路由：实际注册、短信验证码、登录、用户端商品与分类查询；上传继续由 `AdminLoginInterceptor` 保护并维持既有 token 兼容行为。
- 地址新增、列表、详情、更新、删除、默认地址与下单引用全部绑定当前登录用户，彻底删除默认用户 `1` 回退。
- 将普通订单、支付状态/回跳、退款申请、评价本人查询、秒杀订单与 AI 订单查询的归属条件下沉到 Service/Mapper，而不是先无条件读取再由 Controller 事后比较。
- 对目标资源不存在和不属于当前用户使用一致、非泄露式失败；修改类操作严格检查影响行数。
- 用测试先行、真实 Spring/MyBatis/MySQL 归属隔离测试、完整后端测试和独立审查完成验收。

### Out of scope

- B5 的秒杀支付、取消/超时取消状态机、MySQL/Redis 库存补偿、Lua 和 30 分钟延迟闭环。
- B7 的“已完成且商品属于订单”评价资格、订单商品级唯一约束、公开评价投影与完整评价防重。
- 支付/退款状态机、真实退款完成、普通订单库存逻辑及 B6/B8-B11。
- 新增数据库表、索引、约束或生产迁移；生产部署和生产数据变更。
- 扩大公开面：评价列表/统计、秒杀活动等接口不因 B4 自动放行。

## Current findings

- `LoginInterceptor` 放行了不存在的 `/user/send-sms-code`，实际 `/user/register` 与 `/user/sms-code` 被匿名拦截。
- `AddressBookServiceImpl` 在六条路径回退到用户 `1`；详情、更新、删除和设默认使用无 `user_id` 条件 SQL。
- 普通订单创建按裸地址 ID 读取，可能把其他用户地址快照写入订单。
- 普通订单详情/物流、支付状态/同步回跳、退款申请和 AI 物流先读取任意订单，再在 Java 中比较归属。
- 评价 `/check/{orderId}` 按裸订单 ID 返回是否已评价；`/my` 接口仍由 Controller 向 Service 传用户 ID。
- 秒杀订单详情/取消先读取任意订单；取消更新只按订单号和状态，归属校验未进入同一条 SQL。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B4-AC1 | 匿名可访问真实 `/user/register`、`/user/sms-code`、`/user/login`、`/user/product/**` 和 `/user/category/**`；其他 `/user/**` 仍返回 401，`/upload/**` 继续由 `AdminLoginInterceptor` 拒绝匿名访问并维持既有 token 兼容行为 | Web 配置/MockMvc 合约测试，覆盖真实映射、旧错误路径、私有用户路径和上传保护 |
| B4-AC2 | 地址服务无默认用户回退；未登录时不调用 Mapper，登录用户 ID 覆盖任何请求体归属字段 | Service 单测、源码边界扫描 |
| B4-AC3 | 地址详情、更新、删除、设默认均在 SQL 中同时匹配 `id + user_id`；默认重置与目标写入同事务，目标越权/不存在时全部回滚 | Mapper XML 合约、Service 测试、真实 Spring/MyBatis/MySQL 双用户测试 |
| B4-AC4 | `addressId` 非空时仅允许复制当前用户地址；越权/不存在地址不创建订单且不产生购物车、优惠券或库存副作用，空值保持现有语义 | Order Service 单测、真实事务回滚与数据快照测试 |
| B4-AC5 | 普通订单详情/物流、支付状态/同步回跳、退款申请和 AI 物流使用用户归属查询；同步回跳的支付流水也按当前用户订单做 SQL 归属；用户 A 猜测用户 B 的订单 ID/支付流水得不到订单、支付、退款或物流数据 | Controller/Service 测试、Payment/Order Mapper 合约、生产 Mapper XML + MySQL 双用户测试 |
| B4-AC6 | 评价本人列表及订单已评价检查只使用服务端当前用户；用户 A 不能通过用户 B 的订单号判断其评价事实 | Review Controller/Service/Mapper 测试 |
| B4-AC7 | 秒杀订单列表、券列表、详情和用户取消从服务端当前用户取 ID；详情查询和取消写入包含 `user_id` 条件 | Seckill Controller/Service/Mapper 测试、MySQL 双用户读写隔离测试 |
| B4-AC8 | 管理端/支付通知等可信内部查询保持可用；用户端无客户端 `userId` 信任、裸资源读取或默认用户回退 | 回归测试、限定源码扫描、完整 `mvn test`、范围/敏感信息审查 |

## Slices

### Slice 1 — 精确公开路由与地址归属

1. RED：增加路由合约测试，证明真实注册/验证码当前被拦截且旧错误路径被放行。
2. GREEN：将公开路由改为与 Controller 映射一致的精确白名单，不扩大评价、订单、秒杀和 AI 接口公开面；断言 `/upload/**` 仍由管理员拦截器保护。
3. RED：增加地址 Service/Mapper 测试，证明用户 `1` 回退、裸 ID 读取/写入和默认地址部分更新风险。
4. GREEN：统一要求当前登录用户；增加 `id + user_id` 查询/写入，严格检查零行结果；新增、更新和设默认的多语句操作使用真实 Spring 事务。
5. GREEN：普通订单创建在 `addressId` 非空时使用当前用户地址查询；越权/不存在时明确失败并整体回滚，`addressId` 为空时保持现有行为，不新增必填契约。
6. 运行 Slice 1 聚焦测试，记录真实 RED/GREEN 输出。

### Slice 2 — 交易与查询资源归属

1. RED：为普通订单、支付、退款、AI、评价和秒杀订单增加双用户越权用例，证明现有裸资源查询或事后校验边界。
2. GREEN：普通订单增加当前用户专用读取能力，Mapper 查询包含 `id + user_id`；用户订单、支付和 AI 路径复用该能力，管理端及支付通知继续使用可信通用读取。
3. GREEN：同步支付回跳通过 Payment Service 当前用户专用方法读取支付流水，生产 SQL 将 `payment` 与普通订单归属关联；通用按支付流水查询仅保留给可信通知/内部路径。订单支付状态接口先完成归属订单查询再读取对应支付记录。
4. GREEN：退款申请的订单查询使用 `id + user_id`；退款列表继续只从 `BaseContext` 取当前用户。
5. GREEN：评价本人列表和订单已评价检查由 Service 获取当前用户并执行归属 SQL；评价提交仍强制覆盖 `userId`，完整订单/商品资格留给 B7。
6. GREEN：秒杀用户详情、列表、券列表和取消使用无外部 `userId` 参数的当前用户方法；查询/取消 SQL 同时包含订单号和 `user_id`。只补归属条件，不实现 B5 库存/状态闭环。
7. 运行 Slice 2 聚焦测试，记录真实 RED/GREEN 输出。

### Slice 3 — 集成验证与交付证据

1. 在隔离 MySQL 8 临时 schema 中建立两个用户及交叉资源，使用生产 MyBatis XML 验证地址、普通订单、退款/评价查询和秒杀订单读写隔离。
2. 通过真实 Spring 事务代理验证默认地址及下单越权失败的回滚，不使用只验证 Mockito 调用的结果冒充事务证据。
3. 运行后端聚焦测试、显式 MySQL 门禁和完整 `mvn test`；本 workpack 不改前端，因此不把前端构建列为本地完成门禁。
4. 运行 `git diff --check`、变更范围、敏感信息和限定源码扫描，确认无默认用户、客户端用户 ID 信任或用户端裸资源查询。
5. 完成独立实现 Review；P0/P1 清零后补齐 `review.md` 与 `evidence.md`。

## File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/config/Webconfig.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserAddressController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/AddressBookService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/AddressBookServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/AddressBookMapper.java`
- `backend/fashion-server/src/main/resources/mapper/AddressBookMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/service/OrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/OrderServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/OrderMapper.java`
- `backend/fashion-server/src/main/resources/mapper/OrderMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/PaymentController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/PaymentService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/PaymentServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/PaymentMapper.java`
- `backend/fashion-server/src/main/resources/mapper/PaymentMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/RefundServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/AgentController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/ReviewController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/ReviewService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/ReviewServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/ReviewMapper.java`
- `backend/fashion-server/src/main/resources/mapper/ReviewMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserSeckillOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/SeckillOrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/SeckillOrderMapper.java`
- `backend/fashion-server/src/main/resources/mapper/SeckillOrderMapper.xml`

### Expected tests

- `WebconfigPublicRouteContractTest`：精确公开路由和私有路由 401。
- `AddressBookServiceImplTest` / `AddressBookOwnershipMapperContractTest`：当前用户、归属 SQL、零行失败和事务边界。
- `OrderAddressOwnershipTest`：下单地址归属与越权回滚。
- `UserResourceOwnershipContractTest`：普通订单、支付、退款、AI 与评价入口不读取他人资源；同步回跳不先裸查任意支付流水。
- `SeckillOrderOwnershipTest` / Mapper 合约测试：用户查询与取消的 `user_id` 边界。
- `ResourceOwnershipSpringMysqlIntegrationTest`：真实 Spring/MyBatis/MySQL 双用户隔离与回滚。

测试类名可按现有包结构微调，但不得删除相应验收维度。若实现必须引入 schema 迁移、前端协议变化或 B5/B7 行为，先停止并修订计划，不顺手扩大范围。

## Branch and dirty-worktree handling

- B4 在 `D:\market-handsome\Final-StandMarket-worktrees\b4-resource-ownership`、分支 `codex/b4-resource-ownership` 开发，基线为 `master@7819d546`。
- 主工作区仍在 `codex/b1-payment-trust-boundary`，三项未提交工作流文档保持原状，不 reset、stash、暂存或混入 B4。
- B2/B3 worktree 和分支保持原状；清理不属于 B4 计划阶段。
- 本 workpack 只暂存 B4 文档、必要产品代码和测试；不得纳入本地敏感配置。

## Risks and rollback

- **白名单扩大**：只修正阶段 B 明确列出的实际路径；错误旧路径不保留兼容放行，上传继续由 `AdminLoginInterceptor` 拒绝匿名访问并维持既有 token 兼容行为。
- **Controller 事后鉴权**：用户查询必须在 SQL 前获得服务端用户 ID，并将归属放入同一查询/更新条件；Controller 比较只能作为防御补充。
- **默认地址半更新**：目标越权或不存在必须抛错，使默认重置和目标写入由真实事务一起回滚。
- **下单越权副作用**：非空地址引用的归属校验位于订单、库存、购物车和优惠券写入之前；异常仍依赖现有订单创建事务整体回滚。空地址引用保持原语义。
- **支付流水先查后鉴权**：用户同步回跳使用与普通订单归属关联的专用支付查询；通用支付流水查询不暴露给用户入口。
- **管理端回归**：保留通用可信读取供管理端和支付通知使用，新增用户专用方法，不改变管理端语义。
- **B5/B7 交叉污染**：秒杀仅增加所有权谓词；评价仅关闭本人查询泄露。状态机、库存和完整评价资格在后续阶段单独处理。
- **错误回滚**：B4 无数据库迁移；回滚功能提交即可恢复旧版本。生产发布仍受 B0-AC6 与 B11 门禁约束。

## Verification commands

实现阶段先对每个切片执行精确 RED/GREEN。最终至少执行：

```powershell
$B4_MYSQL_CONFIG = 'D:\market-handsome\Final-StandMarket\backend\fashion-server\src\main\resources\application-dev.yml'
if (-not (Test-Path -LiteralPath $B4_MYSQL_CONFIG)) { throw 'B4 MySQL config is missing' }
git -C 'D:\market-handsome\Final-StandMarket' check-ignore --quiet -- 'backend/fashion-server/src/main/resources/application-dev.yml'
if ($LASTEXITCODE -ne 0) { throw 'B4 MySQL config is not ignored by Git' }

Set-Location backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=WebconfigPublicRouteContractTest,AddressBookServiceImplTest,AddressBookOwnershipMapperContractTest,OrderAddressOwnershipTest,UserResourceOwnershipContractTest,SeckillOrderOwnershipTest' test
mvn -pl fashion-server '-Db4.mysql.integration=true' "-Db4.mysql.config=$B4_MYSQL_CONFIG" '-Dtest=ResourceOwnershipSpringMysqlIntegrationTest' test
mvn test

Set-Location ..
git diff --check
git diff --stat
git diff --name-only
rg -n 'BaseContext\.getUserId\(\)\s*!=\s*null\s*\?|:\s*1L|send-sms-code|request.*userId|getById\(orderId\)|selectByOrderNumber\(orderNumber\)' backend/fashion-server/src/main
```

限定扫描必须人工区分管理端、支付通知和其他可信内部通用读取；目标是用户入口不再依赖裸资源查询，而不是全局删除合法管理能力。显式 MySQL 测试只创建并清理固定随机前缀的临时 schema，不执行生产迁移，不输出或复制凭据。
