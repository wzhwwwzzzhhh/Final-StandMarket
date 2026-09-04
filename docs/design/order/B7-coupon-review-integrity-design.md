# B7 优惠与评价业务完整性 · Design

> Status: 已确认（2026-09-03）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B7 / GitHub Issue #18
> Baseline: `master` @ `657286e6f95f362e6408699967e16db5f1eb21cf`
> Updated: 2026-09-03

## 1. Goal and scope

### In scope

- 保持 B2 的普通订单信任边界：客户端 `amount`、`activityId`、秒杀 `couponId` 不参与普通订单计价、落库或任何秒杀状态读写；只有 `userCouponId` 能表示通用持有券。
- 以服务端商品现价、购物车数量和数据库时间校验通用券归属、持有券状态/有效期、模板启用/有效期、门槛与全量商品范围。
- 在普通订单真实 MySQL 事务内原子锁定通用券，并让锁券、订单、明细、券绑定和商品库存任一步失败时整体回滚。
- 把评价粒度固定为 `(order_id, product_id)`；提交时由服务端登录态校验订单归属、完成状态和订单明细商品，并由数据库唯一约束处理串行、重复和并发提交。
- 将公开商品评价改为专用字段白名单 DTO 和脱敏展示名；“我的评价”和管理端查询继续使用各自受保护的契约。
- 将评价检查契约改为 `orderId + productId`，并最小调整现有用户端评价入口，使一个订单中的不同商品可以分别评价。
- 交付可重复执行、遇到脏数据或错误/部分定义会显式失败的 B7 增量迁移，并同步新建库基线。

### Out of scope

- 优惠叠加、部分商品折扣分摊、退款优惠分摊、新券类型、领取并发模型重构或新增营销页面。
- 秒杀库存、预占、用户占用、RabbitMQ 和 B6 补偿状态机；B7 只验证普通订单不会触碰它们。
- B8 商品缓存/ES 同步、B9 全局 Axios/AI 契约、B10 Flyway 发布框架和 B11 阶段级交付。
- 评价审核规则重构、敏感内容审核、图片上传链路和管理端评价页面改版。
- 自动删除、合并、改写历史评价，生产数据清洗、生产迁移、部署或发布声明。

## 2. Current behavior and constraints

- B2 已让 `OrderServiceImpl#create` 从服务端商品价格计算 `original_price`，固定普通订单秒杀字段为 `NULL/0`，并把库存扣减、通用券调用、订单和明细写入置于同一 Spring 事务。
- `CouponServiceImpl#lockAndDiscount` 先用应用节点 `LocalDateTime.now()` 检查持有券过期时间，再执行只约束 `id + user_id + status=0` 的更新。下单门禁没有在同一数据库原子条件中覆盖模板停用、固定有效期和持有券有效期，应用时间与 MySQL 时间也可能偏移。
- 券范围逻辑对未知 `scope_type` 默认放行；可用券预览接受客户端 `totalAmount`，存在把不可信金额用于门槛和展示折扣的路径。
- 并发订单使用同一持有券时数据库条件更新能产生一个赢家，但当前实现不能证明赢家所依据的模板状态和有效期与锁定发生在同一数据库时点。
- `ReviewController` 直接绑定并返回 `Review` 实体；Service 只覆盖 `userId/status/createTime/updateTime` 后插入，不验证订单归属、订单完成状态或商品属于订单明细。
- 评价重复检查只按 `orderId + userId`，导致同一订单的第二种商品不能评价；检查与插入之间也存在竞态，数据库没有 `(order_id, product_id)` 唯一约束。
- 公开商品评价查询 `select r.*`，即使当前页面只显示少数字段，响应仍可包含 `userId`、`orderId` 和其他内部字段；`/user/review/**` 当前整体受登录拦截器保护，与“公开商品评价”契约不一致。
- `mysql/review_table.sql` 是带 `DROP TABLE` 的初始化脚本，现有基线只有普通索引 `idx_review_order(order_id)`，没有可用于已有库安全升级的 B7 增量脚本。

## 3. Design decisions

### 3.1 普通订单与秒杀边界

- 保留 `OrderCreateDTO.amount/activityId/couponId` 字段以兼容既有请求结构，但普通订单创建路径禁止读取这些字段参与计价、券选择、订单类型判断或状态写入。
- 普通订单仍固定写 `is_seckill=0`、`seckill_activity_id=NULL`、`seckill_coupon_id=NULL`、`seckill_price=NULL`；唯一可用优惠输入是 `userCouponId`。
- `original_price` 是服务端对已校验购物车快照按商品当前价格和数量求和的结果；门槛、折扣和最终 `amount` 全部由该值计算，客户端金额仅作为被忽略的兼容字段。
- B7 不注入秒杀 Mapper/Service，不访问 `seckill:*` Redis Key。测试对秒杀表及隔离 Redis 中的秒杀哨兵数据做前后快照，只允许普通订单号序列等非秒杀 Key 变化。

### 3.2 通用券资格与时间事实源

- MySQL `NOW(3)` 是领券和下单时券资格的唯一时间事实源，应用节点时间不参与领取时间、到期时间或是否可用的最终判断。
- 领券事务先取得 `coupon_template ... FOR SHARE` 的 current read，确认锁已返回后再单独执行 `SELECT NOW(3)` 得到 `eligibility_time`。随后以被锁模板和这个锁后数据库时点校验启用状态、固定期窗口或正数 `valid_days`，并执行 `INSERT ... SELECT`：`obtain_time=eligibility_time`；固定期 `expire_time=t.end_time`，领取后有效期 `expire_time=DATE_ADD(eligibility_time, INTERVAL t.valid_days DAY)`。影响行数不是 `1` 即领取失败；保留现有 Redisson 限量/每人限领并发模型，不借 B7 重构领取模型。历史持有券的 `expire_time` 不自动推断或改写。
- 下单锁券按固定顺序执行 current/locking read：先 `SELECT user_coupon ... FOR UPDATE` 独占持有券行，再按其 `template_id` 执行 `SELECT coupon_template ... FOR SHARE`。两个 locking read 都返回、确认行锁已取得后，才单独执行 `SELECT NOW(3)` 得到 `eligibility_time`。MySQL 锁定读不使用事务早先建立的 RR 一致性快照；门槛、折扣、范围、状态和有效期全部来自当前版本，锁后取得的 `eligibility_time` 是本次资格判断的数据库时间线性化点。
- 模板使用共享锁而非排他锁：不同持有券引用同一模板的订单可同时持有 `FOR SHARE` 并继续执行；管理端模板更新必须等待所有相关订单事务结束，因此不会在一次订单内混入另一版本。
- 在锁定快照完成全部规则校验后，最终 CAS 只更新已由当前事务独占的 `user_coupon`，不再 `UPDATE JOIN` 模板，避免把模板共享锁升级成热门排他锁。CAS 重复约束 `id`、当前用户、`template_id`、`status=0`、`use_order_id IS NULL`，并要求 `expire_time > eligibility_time`；被共享锁保护的模板则必须在 `eligibility_time` 满足 `status=1`，且 `valid_type=2` 或 `valid_type=1 AND start_time <= eligibility_time AND eligibility_time < end_time`。
- CAS 影响行数必须恰好为 `1`。`0` 统一表示持有券不存在、无权、已锁定/已使用/已过期、模板停用或不在固定有效期，调用方返回不泄露他人券状态的“优惠券不可用”；多于 `1` 视为数据完整性错误。成功后只使用共享锁保护的同一模板版本计算抵扣，禁止再发普通 `SELECT` 混用旧 RR 快照。未知 `valid_type`、`scope_type`、缺失范围配置、非法折扣值一律失败关闭，不默认放行。
- 持有券的 `expire_time` 对固定期和领取后有效期都必须有效；固定期额外受模板开始/结束时间约束，领取后有效期以领取时固化的 `expire_time` 为准。

### 3.3 服务端金额、范围和预览契约

- 下单路径只接受 `OrderServiceImpl` 已从当前用户购物车和商品表构造的服务端 `originalPrice`、去重商品 ID 与分类信息；`CouponService` 不接受客户端计算金额。预览与创建共用同一个 `CouponPricingPolicy`，禁止复制两份逐渐漂移的规则。
- 全店券要求订单商品集合非空；指定分类券要求每一种订单商品均属于指定分类；指定商品券要求每一种订单商品均在规范化后的配置集合中。混合订单只要有一个范围外商品就整体拒绝该券，不做部分抵扣。
- 券规则合法域固定如下，任何不满足者均视为不可用：`type` 只能为 `1` 满减、`2` 折扣、`3` 现金；`threshold` 必须非空且 `>=0`；类型 1/3 的 `discount` 必须非空且 `>0`；类型 2 的折扣率必须非空且 `0<discount<=10`。固定期要求非空 `start_time<end_time`，使用半开区间 `start_time <= NOW(3) AND NOW(3) < end_time`；领取后有效期要求正整数 `valid_days`。`scope_type` 只能为 0/1/2，分类 ID 必须为正数，商品范围字符串每个 token 都必须是正整数且解析后集合非空；空白、负数、溢出或混杂字符均失败关闭。
- 满减/现金券和折扣券均以服务端 `originalPrice` 为基准；折扣券先以高精度计算，再统一使用 `RoundingMode.HALF_UP` 保留两位小数。抵扣结果限制在 `[0, originalPrice]`，预览和创建使用完全相同的舍入与封顶策略。
- `/user/coupon/available` 保留路径但改收当前用户的 `cartItemIds`，忽略旧 `totalAmount`；服务端按与下单一致的购物车归属、数量和商品价格重算预览。预览与 `OrderServiceImpl#create` 共用 `CartSelectionValidator`：购物车项必须为 1 至 100 个互不重复的正整数，解析失败、重复、越界或无法全部匹配当前用户购物车时整体返回参数/归属错误。创建在生成 Redis 订单号、扣库存或锁券之前运行该门禁。响应增加服务端计算的 `discountAmount`，前端不再用本地总额决定门槛或折扣。
- 可用券预览只是当前快照，不锁券；最终创建仍重新校验并原子锁定。商品价格、券状态或时间在预览后变化时，以创建事务结果为准。

### 3.4 事务边界、锁顺序与失败语义

- 沿用 B2 的一个 `OrderServiceImpl#create` Spring/MySQL 事务和既有商品 ID 升序扣库存顺序。
- `lockAndDiscount` 与 `bindUseOrder` 使用 `Propagation.MANDATORY`，强制它们只能加入调用方的真实事务；不得在无事务上下文中留下独立券锁。
- 创建顺序固定为：校验当前用户购物车并计算服务端快照 → 商品 ID 升序条件扣库存 → 原子锁券并计算抵扣（如有）→ 插入普通订单 → 将已锁券绑定订单 → 批量插入订单明细 → 提交。
- 任一步抛出未检查异常都回滚已扣商品库存、`user_coupon.status/use_order_id`、订单和全部明细。MySQL 死锁或锁等待超时使当前请求整体失败，不在 Service 内局部重试，以免重复创建订单。
- 两个订单并发使用同一持有券时，原子锁券更新最多一个返回 `1`；失败方即使已先扣商品库存，也随事务回滚，不留下锁券、订单或明细。
- 模板管理与下单的锁顺序为：下单固定 `user_coupon FOR UPDATE → coupon_template FOR SHARE`；管理端只更新单个 `coupon_template`，不得在持有模板锁时反向获取 `user_coupon`。若以后出现反向依赖，须另行设计而不能直接加入 B7 路径。
- 新增安全业务异常类型区分可公开验证失败与基础设施失败。所有 B7 触及的用户券入口（至少 `UserCouponController#claim`、`available` 和 `UserOrderController#create`）只回显明确列入安全清单的业务异常；`DataAccessException`、死锁/锁等待、SQL/约束/触发器异常和其余未分类异常只在服务端记录 trace ID、异常类型和必要实体 ID，分别对外统一返回“领取优惠券失败，请稍后重试”“获取可用优惠券失败，请稍后重试”或“下单失败，请稍后重试”，绝不回显驱动消息、SQL、约束名或堆栈。

### 3.5 评价写入与幂等

- 新增 `ReviewCreateDTO`，只接收 `orderId/productId/rating/content/images`。请求中的未知 `userId/status/createTime/updateTime/id` 即使存在也不进入持久化对象。
- `ReviewService#addReview` 从 `BaseContext` 取得用户，校验必填 ID、评分和内容长度后，通过受约束的 Mapper 写入；核心资格不能只放在 Controller。
- Mapper 使用 `INSERT ... SELECT` 从 `orders` 写入评价，条件同时要求 `orders.id=orderId`、`orders.user_id=currentUserId`、`orders.status=4`，并存在 `order_detail(order_id, product_id)`。影响行数为 `0` 时返回统一的“订单不存在、未完成或商品不属于订单”，不泄露他人订单事实。
- 数据库唯一键 `uk_review_order_product(order_id, product_id)` 是重复与并发提交的最终门禁；唯一键冲突稳定映射为“该订单商品已评价”，不返回 SQL、约束名或堆栈。
- 同一订单不同 `productId` 使用不同唯一键，可分别插入；同一订单内同一商品出现多个 SKU/明细行时仍只允许一条评价。

### 3.6 评价查询、公开 DTO 与接口兼容

- 新增 `ReviewPublicVO`，公开列表只包含 `id`、`rating`、`content`、`images`、`createTime`、`displayName`。SQL 不再 `select r.*` 给公开路径，Controller 泛型也不得出现 `Review`。
- `displayName` 由服务端生成：空白名称显示“匿名用户”，非空名称按第一个 Unicode code point 截取并追加 `**`，禁止用 UTF-16 单个 `char` 截断代理对；公开响应不包含 `userId`、`orderId`、手机号、地址、支付或其他订单字段。
- `/user/review/list/{productId}` 和 `/user/review/stats/{productId}` 明确列入匿名放行；`add`、`my`、`check` 仍要求用户登录。放行使用精确路径模式，不能放开整个 `/user/review/**`。公开列表和统计都必须在 SQL 层限定 `r.status=1`，隐藏评价既不返回也不进入评分聚合。
- 公开查询参数固定为：`productId>0`、`1<=page<=10000`、`1<=size<=50`；评分筛选只接受省略、`5`、`4` 或 `3`（`3` 延续现有 UI 的“三星及以下”语义），其他值显式拒绝。评价提交要求正数 `orderId/productId`、`rating` 为 1 至 5、`content` 最长 500 字符、`images` 最长 1000 字符。
- “我的评价”使用 `ReviewMineVO` 返回当前用户自己的 `orderId/productId`、评价内容/状态和商品展示信息；管理端继续使用独立的鉴权接口与完整管理查询，不复用公开 DTO。
- 评价检查保留路径 `GET /user/review/check/{orderId}`，新增必填查询参数 `productId`，语义固定为当前用户的 `(orderId, productId)` 是否已有评价。旧调用缺少 `productId` 时显式返回参数错误，禁止回退为整单检查。
- 用户端 `reviewApi.check(orderId, productId)` 和 `AddReview.vue` 同步传两个 ID；已完成订单对每个不同 `productId` 提供评价入口，不再只取第一个商品。重复商品明细按 `productId` 去重展示入口。

## 4. Contracts and state transitions

### 4.1 通用券状态

| 动作 | 数据库前置条件 | 成功后 | 失败语义 |
|---|---|---|---|
| 原子锁券 | 本人、`status=0`、持有券/模板按 DB 时间有效、模板启用 | `status=3,use_order_id=NULL` | 影响 0 行，整个下单事务回滚 |
| 绑定订单 | 本人、`status=3,use_order_id IS NULL` | `use_order_id=order.id` | 非 1 行，整个下单事务回滚 |
| 支付核销 | 本人、`status=3,use_order_id=order.id` | `status=1,use_time=NOW(3)` | 非 1 行，支付事务回滚（沿用 B2） |
| 取消释放 | 本人、`status=3,use_order_id=order.id` | `status=0,use_order_id=NULL` | 非 1 行，取消事务回滚（沿用 B2） |

### 4.2 评价提交结果

| 情况 | 结果 |
|---|---|
| 本人已完成订单且商品在订单明细 | 插入一条服务端字段评价 |
| 他人订单、订单不存在、未完成、商品不在明细 | 零写入，统一业务错误 |
| 相同 `(order_id, product_id)` 重复或并发提交 | 唯一约束只允许一个成功，其余返回稳定重复错误 |
| 同一订单的不同商品 | 各自独立成功 |

### 4.3 API 契约

- `GET /user/coupon/available?cartItemIds=1,2`：需登录；服务端重算金额与范围，返回可用持有券及 `discountAmount`。旧 `totalAmount` 即使被发送也不参与任何判断。
- `POST /user/review/add`：需登录；请求为 `ReviewCreateDTO`，返回当前用户可见的 `ReviewMineVO`。
- `GET /user/review/check/{orderId}?productId={productId}`：需登录；返回 `{ reviewed: boolean }`。
- `GET /user/review/list/{productId}`：匿名可用；分页记录类型为 `ReviewPublicVO`。
- `GET /user/review/stats/{productId}`：匿名可用；只返回聚合评分字段。
- `GET /user/review/my`：需登录；仅返回当前用户的 `ReviewMineVO`。
- `/admin/review/**`：继续由管理端鉴权保护，契约不因公开 DTO 收窄。

## 5. File-level change surface

- `backend/fashion-server/.../service/impl/OrderServiceImpl.java`：保持普通订单秒杀字段隔离，向券服务传入服务端购物车/商品快照。
- `CouponService.java`、`CouponServiceImpl.java`、新增 `CouponPricingPolicy` 与 `CartSelectionValidator`：领券/使用的数据库时间资格、失败关闭范围/折扣、事务强制，以及预览/创建共享的计价和规模门禁。
- `UserCouponMapper.java` / `resources/mapper/UserCouponMapper.xml`：数据库时间的领取写入、持有券/模板 current locking reads、最终条件 CAS 和事务内快照读取。
- `UserCouponController.java`、`UserOrderController.java`、新增可用券响应 VO/安全业务异常：`cartItemIds` 服务端计价预览，以及领券、预览和创建的异常安全映射；不信任 `totalAmount`，不回显基础设施异常。
- `ReviewController.java`、`ReviewService.java`、`ReviewServiceImpl.java`：DTO 输入、资格校验、重复异常映射和分层查询契约。
- `ReviewMapper.java` / `resources/mapper/ReviewMapper.xml`、必要的 `OrderDetailMapper`：受约束评价插入、二元检查、公开白名单查询。
- `backend/fashion-pojo/.../dto/ReviewCreateDTO.java`、`vo/ReviewPublicVO.java`、`vo/ReviewMineVO.java`：分离输入、公开响应与本人响应。
- `Webconfig.java`：只匿名放行评价列表和统计。
- `frontend/fashion-client/src/api/review.js`、`views/AddReview.vue`、`views/Order.vue`：二元检查和按商品评价入口。
- `frontend/fashion-client/src/api/coupon.js`、`views/CreateOrder.vue`：可用券请求改传购物车项，展示服务端抵扣结果；不进行全局 Axios 重构。
- 新增 `mysql/add_review_integrity.sql`，更新 `mysql/review_table.sql` 与 `mysql/final07.sql`：唯一约束与一致基线。
- 新增聚焦单元/契约测试、真实 MySQL 事务/并发/迁移测试及隔离 Redis 非干扰测试。

## 6. Failure handling, idempotency, and compensation

- 通用券、订单、明细和库存都在一个 MySQL 事务，不使用跨存储补偿；业务异常、唯一冲突、死锁、触发器故障或连接失败均由事务回滚。只有显式安全业务异常可把固定文案返回客户端，所有基础设施异常统一降级文案并使用 trace ID 关联服务端日志。
- 券锁定更新的 0 行是预期并发/资格失败，不重试；客户端可重新获取可用券后重新下单。SQL 异常与锁等待超时返回通用失败并记录无敏感信息的诊断。
- B7 不写秒杀 Redis/MySQL，因此不存在 B7 自身的 Redis 补偿。普通订单号 Redis 自增成功而 MySQL 回滚时允许序列产生空洞，不能复用订单号；这不是库存、占用或优惠事实。
- 评价资格与插入由一条受约束写语句完成，唯一约束承担并发幂等；禁止“先查不存在再插入”作为唯一保证。
- DuplicateKey 只在命中 B7 的 `(order_id, product_id)` 唯一约束时映射为重复评价；其他数据完整性异常保持内部错误并记录诊断，不能全部伪装为重复。
- 公开 DTO 在编译期隔离实体；以后实体新增字段不会自动进入公开响应。用户名脱敏发生在返回前，原始姓名不进入公开 VO；隐藏评价在 Mapper 行谓词阶段即被排除。

## 7. Migration, compatibility, and rollback

### 7.1 增量迁移状态机

`mysql/add_review_integrity.sql` 只升级已有环境，不使用 `DROP TABLE`，按以下顺序执行：

1. 校验 MySQL 8、目标 schema、`review/orders/order_detail/product` 表及关键列存在，`review.order_id/product_id/user_id` 为预期 `BIGINT NOT NULL`；错误或部分定义立即 `SIGNAL SQLSTATE '45000'`。
2. 在任何 DDL 前检查并输出可诊断计数/样例 ID：`order_id/product_id/user_id` 的 NULL、重复 `(order_id,product_id)`、不存在订单/商品、评价用户与订单用户不一致、商品不在订单明细。任一脏数据显式失败，不自动删、并、补或伪造。
3. 若 `uk_review_order_product` 不存在，在一次 `ALTER TABLE` 中添加 `UNIQUE KEY (order_id,product_id)`；若同名对象存在，必须校验唯一性、列数、列顺序、完整列、索引类型和可见性完全一致，否则失败。
4. 迁移后重新校验唯一约束签名。正确结构重复执行为无操作；中断后再次执行只接受“完全未添加”或“已完整正确”，错误/部分定义继续阻断。

`review_table.sql` 和 `final07.sql` 的新建库定义同步包含同名唯一键；B7 增量脚本是已有库唯一升级入口。`review_table.sql` 改为非破坏性初始化说明，不能被当作生产升级脚本执行。

### 7.2 发布顺序与兼容

- B7 只交付并在临时测试 schema 演练脚本，不执行生产迁移。
- 将来发布前先备份并暂停评价写入，运行只读脏数据预检；发现脏数据停止发布，由维护者单独审批清洗方案，B7 不自动处理。
- Issue #18 对应能力尚未生产上线，因此选择维护窗口原子切换，不引入长期双协议：先在网关/维护模式证明普通订单创建、可用券预览、评价提交与检查对外均不可达，再应用唯一约束并部署匹配版本的后端和用户端，完成烟测后同时恢复流量。
- 版本偏差证据按能力边界记录：B7 后端契约测试证明旧前端请求打到新后端时会因缺少可信 `cartItemIds/productId` 而失败关闭；新前端与旧后端的组合不声称由后端单测证明，而由 B11 切换演练记录“新前端发布前流量已关闭、旧后端替换完成后才恢复”。B7 本地交付只记录该远程/部署证据尚未执行，不伪装完成。
- 唯一约束对旧代码是收紧兼容，但旧 Controller 可能显示通用失败；维护窗口保证它不会作为正常服务长期混跑。若未来无法提供维护窗口，必须先另行设计带版本号的双协议，不能临时放宽 B7 信任边界。
- 公开评价从实体收窄为白名单是有意的安全契约变化；依赖内部 `userId/orderId` 的匿名客户端不受兼容保证。

### 7.3 回滚边界

- 应用回滚不得删除 `uk_review_order_product`；该约束与旧版本兼容且防止重新产生重复数据。
- 数据库 DDL 不在紧急回滚中逆向执行。若新版本应用异常，回滚应用并保留唯一键，暂停评价写入后调查。
- 一旦新公开 DTO 上线，不通过回滚重新暴露实体字段；必要时保留/恢复安全白名单适配层。
- B7 前优惠券代码不是有效回滚目标，因为它会重新引入 JVM 时间、模板资格竞态、未知范围放行和客户端预览金额。回滚制品必须保留 B7 的 DB 时间、locking read + CAS、失败关闭规则和服务端计价；若没有这样的安全制品，则保持普通订单创建和可用券预览关闭，不能恢复旧逻辑。已提交的券状态继续由安全的支付/取消流程核销或释放，不手工批量改写。

## 8. Verification gates

### 8.1 TDD 与聚焦行为

- 每项行为先写会因当前缺口失败的测试，再做最小实现并记录 RED/GREEN 命令和失败原因。
- 普通订单：伪造 `amount/activityId/couponId` 后金额与秒杀字段仍来自服务端/固定默认，秒杀 Mapper 不被调用。
- 通用券：他人券、状态 1/2/3、持有券过期、模板停用、固定期未开始/已结束、未知类型、NULL/负门槛、非法满减金额/折扣率、非法有效天数、范围缺失/坏 token、混合商品、未达门槛均失败；合法全店/分类/商品券按服务端原价和 `HALF_UP` 两位小数计算。预览与创建运行同一参数化用例，并证明第 100 个购物车项可通过、第 101 个在生成订单号及任何 MySQL/券写入前失败。
- 评价：他人订单、未完成订单、订单外商品失败；同一订单不同商品成功；请求体中的用户/状态/时间字段不能进入持久化；检查必须携带两个 ID。
- API：匿名仅能访问 list/stats；公开 JSON 只含白名单且展示名脱敏；隐藏评价不出现在列表/统计；my/admin 不串用公开契约。覆盖分页、评分、购物车项数量/重复/非法 ID 上下界，以及 emoji/空白用户名。
- 异常响应：分别在领券、可用券预览和订单创建注入包含 SQL、约束名和模拟凭据片段的底层异常，断言响应只含各自统一安全文案，诊断只能通过 trace ID 在服务端日志定位。

### 8.2 真实 MySQL/Redis 验证

- 使用隔离临时 MySQL 8 schema、独立 Redis DB/Key 前缀和测试专用非生产账号；配置路径通过系统属性传入，不把密码写入仓库或日志。测试完成只删除经过严格名称校验的临时 schema 和 B7 测试 Key。
- 两个线程同时用同一 `userCouponId` 创建订单，最多一个提交；失败方库存回滚，最终只有一份订单/明细/券绑定。
- 分别在锁券后、订单插入后、券绑定后、明细插入时用测试 schema 触发器注入失败，验证库存、券、订单和明细整体回滚。
- 在领券和下单两个阶段分别把应用 JVM 时钟故意前移/后移，而数据库时间保持真实，验证 `obtain_time/expire_time` 及使用资格只服从锁后单独读取的 MySQL `NOW(3)`；历史期限不被改写。
- 让管理事务持有模板排他锁并跨越固定期开始/结束边界，领券/下单请求必须等待；释放锁后单独读取的数据库时间决定成功或失败，禁止使用 locking read 语句开始时的旧时间。
- 通过可控栅栏让管理端并发修改模板状态、有效期、门槛、折扣和范围，验证下单只使用 `FOR SHARE` 锁住的一个当前版本，修改方等待提交后生效，不出现“新状态 + 旧折扣”等混合快照；另用不同持有券和同一模板并发下单，证明两个事务能同时越过模板读取栅栏而不被热门模板串行化。
- 同时提交相同 `(order_id,product_id)`，唯一键只允许一个成功；不同商品分别成功。
- 在隔离 Redis 预置秒杀库存、用户占用和预扣记录，执行普通订单成功/回滚后逐值比对完全不变；普通订单号序列 Key 可变化。

### 8.3 迁移矩阵

- 空/新建基线与干净 legacy 表；合法 legacy 无唯一键时成功添加；正确约束重复执行两次均成功且结构不变。
- 同名非唯一、反序列、单列/前缀/不可见等错误或部分定义全部阻断。
- 分别制造重复键、NULL、孤立订单、孤立商品、用户不匹配和商品不在明细，迁移在 DDL 前失败并给出诊断，原数据不被更改。
- 比较 `review_table.sql`、`final07.sql` 与增量迁移后的 `SHOW CREATE TABLE review` 关键签名一致；升级脚本不含 `DROP TABLE`。

### 8.4 完成门禁

- B7 聚焦测试和真实 MySQL/Redis 故障注入通过。
- `cd backend; mvn test` 全量通过。
- `cd frontend/fashion-client; npm run build` 通过；当前无前端 test/lint/typecheck 脚本，不作相应声明。后端契约测试只证明旧请求到新后端失败关闭；新前端/旧后端不可达属于 B11 维护窗口演练证据，B7 不提前声称通过。
- `git diff --check`、限定范围 diff、冲突标记与敏感信息扫描通过。
- 独立只读实现 Review 的 P0/P1/P2 为 0，workpack `review.md` 为 PASS，`evidence.md` 映射全部 AC 后才能标记“本地已验证”。

## 9. Decisions requiring user confirmation

Issue #18 已确认产品范围；实现前需要确认本 Design 固化的三项技术契约：

1. 领券和下单先取得所需 current row lock，再用单独 `SELECT NOW(3)` 取得锁后数据库时点；下单通过 `user_coupon FOR UPDATE → coupon_template FOR SHARE` 获取单一规则版本，再只对持有券执行原子 CAS，避免热门模板把不同订单串行化或使用等待前时间。
2. 评价检查保留 `/user/review/check/{orderId}` 路径并增加必填 `productId` 查询参数；缺参显式失败，不保留整单判断。
3. 评价增量迁移遇到任何历史重复、NULL 或非法关联只阻断并诊断，不自动清洗；紧急应用回滚保留唯一约束、公开白名单和 B7 优惠券安全边界，不能回到旧券逻辑。

## 10. Independent review

- Final verdict: PASS
- Final counts: P0=0, P1=0, P2=0, P3=0
- Review mode: 独立只读上下文；审查者未修改文件。
- Review history:
  1. 首轮 `FAIL`（P1=4、P2=4、P3=1）：补齐领券 DB 时间、模板单版本读取、异常脱敏、安全回滚、券规则合法域、公开状态谓词、参数上限和 Unicode 截取。
  2. 二轮 `FAIL`（P1=2、P2=2）：将模板排他锁改为共享锁，覆盖全部券入口异常安全，统一预览/创建购物车规模门禁，并纠正版本偏差证据归属。
  3. 三轮 `FAIL`（P1=1）：修复 locking read 等待跨过有效期边界时 `NOW(3)` 取值过早的问题。
  4. 四轮 `PASS`：所需行锁全部取得后单独读取数据库时间；复核未发现新的架构缺口或范围漂移，P0/P1/P2/P3 全部为 0。
