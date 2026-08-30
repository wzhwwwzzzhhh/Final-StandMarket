# 阶段 B：P0/P1 交易链路与安全基线修复

> 来源：2026-08-27 全项目只读技术尽调
>
> 状态：实施中
>
> 当前分支：`master`
>
> 阶段定位：从“功能可演示”进入“交易正确、权限可信、可重复验证”的上线前加固阶段
>
> 本阶段事实来源：本文；其他进度或路线图与本文冲突时，以本文为准
> 本地执行规范：[开发规范.md](../开发规范.md)；本文是 B0-B11 的已确认需求源，不再为各项重复创建 PRD

阶段 B 按小工作包执行。每个工作包在 `docs/workpack/<phase>-<slice>/` 保存计划、独立审查和验证证据；只有出现本文未解决的高风险新决策时才新增 Design。

## 一、当前基线

### 1. 已具备能力

- 后端 Maven 多模块结构完整，管理端、用户端和 Python Agent 已形成端到端功能。
- 后端测试、用户端生产构建、管理端生产构建可以执行。
- 支付宝异步通知已具备签名、金额和 `app_id` 校验。
- 普通订单金额由服务端按购物车与商品数据重算。
- 通用优惠券具备归属校验、条件锁券、核销和释放操作。
- 秒杀入口使用 Redis Lua 原子校验活动时间、库存和重复购买，数据库有订单号及用户券唯一索引兜底。

### 2. 当前验证结果

| 项目 | 当前结果 | 说明 |
|---|---|---|
| 后端 `mvn test` | 通过 | 仅 6 个有效 Java 测试，全部集中在 AgentService |
| 用户端 `npm run build` | 通过 | 存在主包和图片资源偏大警告 |
| 管理端 `npm run build` | 通过 | 存在大体积 chunk 警告 |
| Python 全量测试 | 未通过环境收集 | 当前 Python 环境缺少 Redis/Elasticsearch 依赖 |
| Python 尺码测试 | 12 个通过 | 不能替代 Agent 全量验证 |
| 生产部署 | 未开始 | 无 CI、生产 Compose、Nginx 和迁移工具 |

### 3. 当前未提交修改

当前工作树已经开始 B1 支付修复，涉及支付查询、回跳只读、支付记录复用和回调条件更新；这些改动尚未形成完整交易闭环，也尚未提交。

该 B1 修改作为存量工作保留，但从本文修订生效后暂停扩大范围；B0 的产品代码安全基线（B0-AC1 至 B0-AC5）通过测试、独立审查和 CI 后，才可继续收口和提交 B1。B0-AC6 的外部凭据处置证据不阻塞 B1-B10 的本地开发，但继续阻塞 Issue #4 关闭、B11 完成和任何生产发布。

## 二、阶段目标与发布门禁

本阶段只在以下条件全部满足后才能标记完成：

1. 密码、验证码、Token 和密钥不出现在接口响应或业务日志中。
2. 匿名注册、验证码、公开商品和分类接口路由正确，其余用户接口必须登录。
3. 所有用户资源按当前登录用户校验归属，重点覆盖地址、订单、退款、评价和 AI 会话。
4. 普通订单完成“条件扣库存 → 支付 → 取消/退款一次性回补”的数据库事务闭环。
5. 订单状态只能通过显式 CAS 流转，重复请求和并发请求不会重复扣减或回补。
6. 用户端不能通过模拟接口或客户端参数直接把订单标记为已支付、已退款或获得秒杀优惠。
7. 秒杀完成 Redis 预扣、MQ 投递、MySQL 落单、超时/主动取消补偿和用户占用释放闭环。
8. 数据库结构能够通过明确、可审查、可重复执行的迁移脚本部署。
9. 新增交易与安全测试通过，两端前端生产构建通过。
10. 各工作包完成独立代码审查和证据归档，并把阶段级结论和遗留问题汇总回本文。

## 三、执行顺序

依赖关系如下：

```text
B0 密码与身份安全
  ↓
B1 支付可信边界 ──→ B2 普通订单库存闭环 ──→ B3 退款状态
  ↓                         ↓
B4 地址与业务归属      B5 秒杀状态与库存
                            ↓
                       B6 MQ 可靠性
  ↓                         ↓
B7 优惠与评价完整性 ──→ B8 缓存一致性
  ↓
B9 AI/前端契约 ──→ B10 数据库发布基线 ──→ B11 测试、审查与交付
```

上图 `B0 → B1` 的前置条件特指 B0-AC1 至 B0-AC5 的产品代码安全基线已经通过测试、独立审查和 CI。B0-AC6 涉及外部平台凭据轮换、旧凭据失效或“从未共享”的可核验证据；该运维门禁可以在 B1-B10 本地开发期间保持未完成，但必须保持显式阻塞，不得关闭 Issue #4，也不得完成 B11、生产部署或发布就绪声明。

阶段 B 只有在本文列出的 P0、P1 和 B11 交付门禁全部完成后才能关闭。若确需移出某项 P1，必须由项目维护者批准、先修改本文范围，并创建有负责人和验收时间的后续阶段文档；不能仅在检查表中注明“延期”后关闭阶段 B。

本地部署准备不受此限制；任何生产发布必须等待阶段 B 完成、独立审查通过并形成回滚记录。

## 四、详细实施任务

### B0：密码、Token 与日志安全（P0）

#### 业务要求

- 用户和员工实体不得直接作为包含敏感字段的接口响应。
- 登录 Redis 会话只保存 `id/name/phone/avatar` 等最小身份信息。
- 禁止记录验证码、Token、密码哈希、完整登录用户和支付回调全量参数。
- 修改或设置密码只能走专用接口，统一 BCrypt；资料更新接口忽略密码字段。
- 如果本地开发配置中的 OSS、支付或其他密钥曾被共享，执行轮换。
- 密钥轮换需要外部平台权限时，在 B0 workpack 中登记负责人、目标系统和阻塞状态；没有实际轮换、旧凭据失效或“从未共享”的可核验证据时不得勾选 B0-AC6。该阻塞不阻止 B1-B10 本地开发，但阻止 Issue #4 关闭、B11 完成和任何生产发布。

#### 后端文件

- `backend/fashion-pojo/src/main/java/com/fashion/entity/User.java`
- `backend/fashion-pojo/src/main/java/com/fashion/entity/Employee.java`
- 新增用户/员工安全响应 VO
- `backend/fashion-server/src/main/java/com/fashion/service/impl/UserServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/EmployeeServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/admin/UserAdminController.java`
- `backend/fashion-server/src/main/java/com/fashion/aspect/OperationLogAspect.java`
- `backend/fashion-server/src/main/resources/mapper/UserMapper.xml`
- `backend/fashion-server/src/main/resources/mapper/EmployeeMapper.xml`

#### 前端文件

- `frontend/fashion-client/src/views/Settings.vue`
- `frontend/fashion-client/src/api/user.js`
- 用户信息展示、资料修改相关组件

#### 验收

- 用户信息、管理端用户详情、员工列表和 Redis 登录 Hash 均不含密码。
- 首次设置密码和修改密码最终均为 BCrypt。
- 日志检索不到验证码、Token、密码哈希和私钥内容。
- 外部凭据逐项具备轮换与旧凭据失效证据，或具备“从未配置/从未共享”的可核验证据；证据未齐时保持 B0-AC6 阻塞并纳入 B11 门禁。

### B1：支付入口、通知校验与幂等（P0）

#### 业务要求

- 移除普通订单和秒杀订单的随机模拟支付入口。
- 支付状态只能查询本人订单及正确的 `order_type`。
- 同步回跳只展示状态，不修改订单。
- 普通订单支付状态只接受支付宝异步通知或明确审计的人工补单流程更新。
- 异步通知校验签名、`app_id`、金额、支付状态、支付记录类型和订单状态。
- 支付记录创建具备数据库级并发幂等，不能只依赖“先查再插”。
- 删除管理端直接修改普通订单为已支付的人工确认入口；未来如确有人工补单需求，必须单独立项并设计双人复核、凭证和审计字段。
- HTTP 客户端必须配置连接与读取超时。

#### 后端文件

- `PaymentController.java`
- `PayNotifyController.java`
- `UserOrderController.java`
- `UserSeckillOrderController.java`
- `admin/OrderController.java`
- `PaymentService.java` / `PaymentServiceImpl.java`
- `PaymentMapper.java` / `PaymentMapper.xml`
- `OrderServiceImpl.java`
- `OrderMapper.java` / `OrderMapper.xml`
- `RestConfig.java`
- `mysql/payment_table.sql` 或对应版本化迁移

#### 前端文件

- `frontend/fashion-client/src/views/Order.vue`
- `frontend/fashion-client/src/views/CreateOrder.vue`
- `frontend/fashion-client/src/views/PayResult.vue`
- `frontend/fashion-client/src/api/payment.js`

#### 验收

- 无有效异步通知不能更新支付和订单。
- 重复通知只成功迁移一次。
- 并发创建支付记录不会生成多个活动支付流水。
- 所有模拟支付 URL 不再可用。

### B2：普通订单库存与状态闭环（P0）

#### 业务要求

- 下单在同一数据库事务内按商品执行 `stock >= quantity` 条件扣减。
- 任一商品扣减失败时，订单、明细、优惠券锁定和所有库存扣减全部回滚。
- 客户端传入的金额、秒杀活动 ID 和秒杀券 ID 不作为普通订单优惠依据。
- 用户取消与超时取消仅允许待支付订单通过 CAS 迁移成功一次。
- 只有成功扣减过的订单才能回补库存，重复取消不能重复回补。
- 确认收货、发货和支付均限制合法前置状态。
- 移除订单服务中未登录回退到用户 `1` 的逻辑。

#### 后端文件

- `OrderService.java` / `OrderServiceImpl.java`
- `OrderMapper.java` / `OrderMapper.xml`
- `OrderDetailMapper.java` / `OrderDetailMapper.xml`
- `ProductMapper.java` / `ProductMapper.xml`
- `ShoppingCartMapper.java` / `ShoppingCartMapper.xml`
- `CouponServiceImpl.java`
- `OrderTimeoutTask.java`
- `OrderCreateDTO.java`
- `Orders.java`

#### 数据库

- 明确订单库存状态或等价的状态机约束。
- 补齐订单插入字段：`original_price`、秒杀标记、活动/券字段等；普通订单必须写空秒杀字段。
- 审查订单状态、支付状态和超时查询索引。

#### 验收

- 并发下单不会超卖。
- 中途失败不留下半订单、半扣库存或永久锁券。
- 重复取消和支付/取消竞态最多只有一个状态迁移成功。

### B3：退款真实状态与一次性回补（P0）

#### 业务要求

- 阶段 B 不接入支付宝真实退款 API；目标是先修正现有虚假状态。
- 退款状态固定为：`0 待审核`、`1 已同意/待外部退款`、`2 退款完成（阶段 B 不写入）`、`3 已拒绝`。
- 审核通过只执行 `0 → 1` CAS，不修改支付状态、不恢复库存，也不填写退款完成时间。
- 审核拒绝只执行 `0 → 3` CAS，并恢复申请前订单状态。
- 后续真实退款阶段新增可信退款通知后，才允许执行 `1 → 2`、支付状态更新和一次性库存回补。
- 前后端文案必须显示“已同意，等待退款处理”，不得显示“退款成功”。

#### 后端文件

- `RefundService.java` / `RefundServiceImpl.java`
- `RefundController.java`（user/admin）
- `RefundMapper.java` / `RefundMapper.xml`
- `PaymentServiceImpl.java`
- `OrderServiceImpl.java`
- `ProductMapper.java`
- `Refund.java` / `Orders.java` / `Payment.java`
- `mysql/refund_table.sql` 或对应版本化迁移

#### 验收

- 审核通过后退款状态为 1，订单支付状态和商品库存不变。
- 重复或并发审批最多一次成功。
- 代码中不存在阶段 B 直接写入退款完成状态或恢复退款库存的路径。

### B4：地址、公开路由与资源归属（P0）

#### 业务要求

- 放行真实 `/user/register`、`/user/sms-code`、登录、公开商品和分类接口。
- 地址查、改、删、设默认和下单引用全部按当前用户过滤。
- 地址服务移除默认用户 `1`。
- 订单、支付、退款、评价、秒杀订单和 AI 订单查询均以服务端登录态为准。

#### 后端文件

- `Webconfig.java`
- `AddressBookServiceImpl.java`
- `AddressBookMapper.java` / `AddressBookMapper.xml`
- `UserAddressController.java`
- `OrderServiceImpl.java`
- `UserOrderController.java`
- `PaymentController.java`
- `RefundController.java`
- `ReviewController.java`
- `UserSeckillOrderController.java`
- `AgentController.java`

#### 验收

- 匿名用户可以注册和获取验证码。
- 用户 A 无法通过猜测 ID 读取或修改用户 B 的地址及交易数据。

### B5：秒杀支付、取消与库存闭环（P0）

#### 业务要求

- `updatePayTime` 只更新时间，不隐式递增状态。
- 支付、主动取消和超时取消统一使用 `status=1` 条件 CAS。
- 取消成功后，MySQL 使用事务和状态 CAS 恢复数据库库存；Redis 使用 Lua 原子恢复库存并移除已购用户。
- MySQL 与 Redis 不能由同一个 Lua 脚本形成跨存储原子事务，跨存储失败由 B6 的消息确认、补偿记录和定时对账收敛。
- 禁用秒杀随机模拟支付。
- 延迟关闭时间统一为 30 分钟，代码、配置和文档保持一致。

#### 后端文件

- `SeckillOrderService.java` / `SeckillOrderServiceImpl.java`
- `SeckillOrderMapper.java` / `SeckillOrderMapper.xml`
- `SeckillCouponServiceImpl.java`
- `SeckillCouponMapper.java` / `SeckillCouponMapper.xml`
- `UserSeckillOrderController.java`
- `admin/SeckillOrderController.java`
- `DirectExchangeConfig.java`
- 新增 `resources/lua/seckill_rollback.lua`

#### 前端文件

- `frontend/fashion-client/src/views/SeckillOrder.vue`
- `frontend/fashion-admin/src/views/SeckillOrderList.vue`

#### 验收

- 支付后状态稳定为已支付，不会变成已取消。
- 主动取消和超时取消并发时只补偿一次。
- 取消成功后，在业务允许的情况下用户可以重新参与。

### B6：RabbitMQ 可靠投递与消费失败治理（P1）

#### 业务要求

- Lua 预扣成功但同步投递异常时立即原子回滚 Redis 库存和用户占用。
- 启用 publisher confirm/return，记录不可路由和未确认消息。
- 消费失败采用有限重试，超过阈值进入业务死信队列并告警，禁止无限 requeue。
- 消费端以订单号和数据库唯一约束保证幂等。
- 明确“数据库事务提交后发送延迟消息”的时序，避免回滚订单留下孤立延迟消息。
- 新增秒杀补偿记录和定时对账任务，核对 Redis 预扣、有效秒杀订单和取消补偿；发现悬空预扣时自动修复并记录。
- 本阶段不强制采用完整 outbox，但必须交付 publisher confirm、有限重试、业务死信和定时对账，形成可验证的最终一致性闭环。

#### 后端文件

- `SeckillCouponServiceImpl.java`
- `DirectExchangeConfig.java`
- `application.yml`
- 新增 `SeckillMqConfirmConfig.java`
- 新增 `SeckillReconciliationTask.java`
- 新增 `SeckillMessageLogMapper.java` 及 XML
- 新增秒杀消息/补偿记录数据库迁移脚本

#### 验收

- B6 workpack 的 `plan.md` 必须写明可重复的 MySQL、Redis、RabbitMQ 启动条件，以及 MQ 不可达、消费者失败、进入业务死信和对账修复的故障注入步骤；环境依赖不可用时在 `evidence.md` 记录阻塞。
- 模拟 MQ 不可达后 Redis 库存和用户占用恢复。
- 毒消息不会无限占用消费者。
- 重复投递只生成一个秒杀订单。
- 人工制造悬空预扣后，对账任务能够发现、修复并留下记录。

### B7：优惠与评价业务完整性（P1）

#### 业务要求

- 普通订单拒绝客户端传入秒杀活动/秒杀券直接抵扣。
- 通用券必须属于本人、有效、满足门槛和商品范围，并且只能被一个订单锁定。
- 评价前验证订单属于本人、订单已完成、商品属于订单明细。
- 数据库增加订单评价唯一约束，防止并发重复评价。
- 评价查询不得泄露其他订单的非公开信息。

#### 后端文件

- `OrderServiceImpl.java`
- `CouponServiceImpl.java`
- `UserCouponMapper.xml`
- `ReviewController.java`
- `ReviewServiceImpl.java`
- `ReviewMapper.java` / `ReviewMapper.xml`
- `mysql/review_table.sql` 或对应版本化迁移

#### 验收

- 伪造他人订单、未完成订单或不属于订单的商品均不能评价。
- 同一订单并发提交评价最多成功一次。
- 普通订单传入秒杀活动或秒杀券参数不会获得优惠。

### B8：商品缓存一致性（P1）

#### 业务要求

- 禁止使用 `DEL productPage:*` 伪通配符删除。
- 列表缓存确定采用版本号方案：缓存 Key 包含商品列表版本，商品写操作在数据库成功后递增版本；旧版本自然过期。
- 修复互斥锁释放：只有持有唯一值的线程才能通过 Lua 删除锁。
- 缓存空值和实际值使用正确 TTL 单位，并增加合理随机抖动。
- 商品数据库、详情缓存、列表缓存和 ES 更新失败时有可观察的补偿路径。

#### 后端文件

- `CacheClient.java`
- `admin/ProductController.java`
- `user/UserProductController.java`
- `ProductServiceImpl.java`
- `ProductIndexServiceImpl.java`
- `ProductSyncTask.java`

#### 验收

- 新增、修改、上下架或删除商品后，新请求不会读取旧版本列表。
- 非锁持有者不能删除其他线程的缓存重建锁。
- ES 同步失败可被定时任务发现并重新同步。

### B9：AI 服务与前端契约治理（P1）

#### AI/后端要求

- `AgentServiceImpl` 从配置读取 Python 地址，配置连接和读取超时。
- Python `/chat` 不能直接信任外部 `userId`；内部服务调用增加共享认证或网络隔离。
- Agent 会话键绑定用户 ID，客户端不能通过猜测 `sessionId` 串用他人上下文。
- Redis/ES/LLM 不可用时返回一致的降级响应并记录可诊断日志。

#### 前端要求

- 用户端 API 统一复用一个 Axios 实例。
- 管理端秒杀操作统一以 `code === 1` 为成功。
- 支付回跳参数和只读状态查询契约保持一致。
- Token 继续由统一拦截器注入，避免每个 API 文件重复实现。

#### 文件

- `AgentServiceImpl.java`
- `RestConfig.java`
- `AgentController.java`
- `agent-service/app/main.py`
- `agent-service/app/redis_memory.py`
- `agent-service/app/config.py`
- `frontend/fashion-client/src/utils/request.js`
- `frontend/fashion-client/src/api/*.js`
- `frontend/fashion-admin/src/views/SeckillOrderList.vue`

#### 验收

- 配置不同 Agent 地址后无需修改 Java 代码即可切换。
- Java 调用 Python 超时后在限定时间内返回降级响应。
- 用户 A 提交用户 B 的会话 ID 不能读取或影响用户 B 的历史。
- 用户端 API 仅保留一个统一鉴权和 401 处理实现。
- 管理端秒杀成功操作统一判断 `code === 1`。

### B10：数据库发布基线（P1）

#### 业务要求

- 不再依赖人工猜测多个 SQL 文件的执行顺序。
- 确定使用 Flyway，以现有库建立 baseline。
- 将支付、退款、评价、收藏、优惠券、审计和索引变更纳入版本化迁移。
- 已发布迁移不可修改；新增结构只能追加新版本。
- 补充迁移前备份、失败回滚和测试库演练记录。

#### 文件

- `backend/fashion-server/pom.xml`
- `backend/fashion-server/src/main/resources/application.yml`
- 新增 `backend/fashion-server/src/main/resources/db/migration/Vxxx__*.sql`
- `mysql/README.md`
- `README.md`

#### 验收

- 空测试库可以按文档初始化到当前结构。
- 已有结构的测试库可以建立 baseline 后执行后续迁移。
- 重复启动不会重复执行或修改已成功迁移。
- 支付、退款、评价、收藏、优惠券和审计表均在迁移历史中可追踪。

### B11：测试、审查和交付（P0 门禁）

#### Java 测试

至少覆盖：

1. 用户信息不返回密码，资料更新不能修改密码。
2. 注册和验证码匿名可访问，其余用户接口返回 401。
3. 地址所有 IDOR 场景。
4. 普通订单扣库存成功、库存不足整体回滚、重复取消不重复回补。
5. 支付通知签名、金额、应用 ID、订单类型、归属和重复通知。
6. 退款审批与完成状态、并发重复回补。
7. 秒杀支付/取消 CAS、Redis 补偿和 MQ 投递失败。
8. 普通订单不能使用客户端秒杀券参数。
9. 评价归属、订单状态、商品归属和并发重复评价。
10. 缓存失效和锁所有权。

建议新增或恢复以下测试文件：

- `UserServiceImplTest.java`
- `WebconfigTest.java`
- `AddressBookServiceImplTest.java`
- `OrderServiceImplTest.java`
- `PaymentControllerTest.java`
- `PayNotifyControllerTest.java`
- `RefundServiceImplTest.java`
- `SeckillOrderServiceImplTest.java`
- `SeckillCouponServiceImplTest.java`
- `ReviewControllerTest.java`
- `CacheClientTest.java`

#### Python 测试

- 先创建项目虚拟环境并执行 `python -m pip install -r requirements.txt`，确保全量测试可收集。
- `/health`、`/chat` 正常与降级路径。
- 会话按用户隔离。
- Redis、ES、LLM 和 Java 后端不可用时的响应。
- 订单工具不信任伪造用户 ID。

#### 前端验证

- 用户端和管理端生产构建。
- 注册、登录、验证码、设置密码、下单、支付回跳、取消、退款、秒杀和评价关键流程。
- 管理端确认支付、取消和删除操作的成功/失败提示与后端一致。

#### 阶段与发布门禁

- B0-AC6 的外部凭据登记逐项完成：已共享或无法排除泄漏的凭据必须轮换并使旧凭据失效；从未配置或从未共享的项目必须有可核验证据。
- B0-AC6 未完成时，B1-B10 可以继续本地开发和验证，但 Issue #4、B11、阶段 B、生产部署和发布就绪状态均不得标记完成。

#### 最终命令

```bash
mvn test
cd frontend/fashion-client && npm run build
cd frontend/fashion-admin && npm run build
cd agent-service && python -m pytest -q
```

## 五、责任与范围变更

| 角色 | 责任 |
|---|---|
| 项目维护者 | 批准业务边界、密钥轮换、P1 范围变更和生产发布 |
| 实现者 | 按本文文件范围开发，补测试和验证证据，不顺带扩大需求 |
| 独立只读审查者 | 在工作包完成标记前审查安全、状态机、异常、接口一致性和 AC 证据；不可用时记录 `tooling_blocked` |

范围变化必须先修改本文，再修改代码。任何移出阶段 B 的事项都必须写明后续文档、负责人、目标时间和当前残余风险。

## 六、提交拆分建议

避免把所有风险混在一个提交中，建议按以下顺序：

1. `fix(security): protect credentials and user identity boundaries`
2. `fix(payment): enforce trusted payment state transitions`
3. `fix(order): close inventory and cancellation transaction loop`
4. `fix(refund): separate approval from completed refund`
5. `fix(seckill): make payment cancellation and compensation idempotent`
6. `fix(mq): add delivery confirmation retry and compensation`
7. `fix(review): validate completed order ownership`
8. `fix(cache): repair product cache invalidation and lock ownership`
9. `refactor(agent): externalize config and isolate sessions`
10. `build(db): introduce versioned database migrations`
11. `test(transaction): cover critical state machines`

每个本地交付单元都必须能独立验证，并在对应 workpack 的 `evidence.md` 中记录结果。只有用户明确要求时才创建提交。

## 七、明确不伪装完成的事项

- 没有真实支付宝退款结果时，不宣称“退款完成”。
- 只有同步发送异常补偿而没有 confirm、有限重试、业务死信和对账时，不宣称“MQ 最终一致性完成”。
- 没有真实压测数据时，不宣称达到某个 QPS。
- 只有本地构建而没有生产部署、监控和回滚演练时，不宣称“可生产上线”。
- 当前项目是 Maven 多模块单体；在没有服务拆分、独立部署和治理能力前，不宣称微服务架构。

## 八、完成检查表

### P0

- [ ] 密码、验证码、Token 不泄露，密码统一 BCrypt。
- [ ] 注册和短信验证码匿名可访问。
- [ ] 地址和所有用户资源完成归属校验。
- [ ] 普通订单库存扣减、取消和退款回补闭环。
- [ ] 模拟支付入口全部禁用。
- [ ] 支付通知与状态迁移幂等。
- [ ] 秒杀支付、取消、超时补偿正确。
- [ ] P0 自动化测试全部通过。

### P1

- [ ] MQ confirm、有限重试、业务死信与即时补偿完成。
- [ ] 商品缓存失效和锁所有权修复。
- [ ] 评价与优惠业务完整性修复。
- [ ] AI 会话隔离、配置和超时完成。
- [ ] 前端 API 和返回码契约统一。
- [ ] 数据库版本化迁移完成。

### 交付

- [ ] 后端测试通过。
- [ ] Python 全量测试通过。
- [ ] 用户端生产构建通过。
- [ ] 管理端生产构建通过。
- [ ] 各 workpack 独立审查完成且 `review.md` 为 PASS。
- [ ] 各 workpack 验证证据完整，阶段级审查摘要已写入本文。
- [ ] B0-AC6 外部凭据轮换、旧凭据失效或“从未配置/从未共享”证据逐项完整。
- [ ] 项目进度跟踪已同步。

## 九、审查记录

### 2026-08-27 计划修订

- 根据全项目只读尽调扩大阶段范围。
- 新增密码与日志安全、注册路由、评价完整性、缓存锁、AI 会话隔离、前端契约和数据库迁移任务。
- 将“能构建”与“交易正确、可上线”明确区分。
- 当前仅为计划修订，代码实现和代码审查尚未完成。

### 2026-08-28 B0-AC6 门禁调整

- 项目维护者确认：B0-AC1 至 B0-AC5 的产品代码安全基线通过测试、独立审查和 CI 后，可继续 B1-B10 本地开发。
- B0-AC6 不降级、不视为完成，也不移出阶段 B；其外部凭据证据改为阻塞 Issue #4、B11、阶段完成和任何生产发布，而不再阻塞 B1-B10 本地开发。

### 实现后审查

已归档工作包：

- B3（2026-08-30）：本地已验证，独立实现审查 PASS（P0/P1/P2/P3 均为 0）；后端干净全量 163 tests、显式 B3 MySQL/Spring 10 tests、两端生产构建通过。详见 [B3 review](../workpack/归档/B3-refund-state/review.md) 与 [evidence](../workpack/归档/B3-refund-state/evidence.md)。真实 `1 -> 2`、生产历史对账、B0-AC6/B11 发布门禁仍未完成。

其余工作包实现、测试、独立审查和证据归档后补充：

- 审查范围：
- 发现问题：
- 修复结果：
- 遗留风险：
- 验证证据：
