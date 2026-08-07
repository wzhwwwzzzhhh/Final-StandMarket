# 阶段A - 安全加固

> 主计划：[安全加固与功能拓展主计划.md](安全加固与功能拓展主计划.md)
> 目标定位：面试/简历亮眼优先 —— 每项修复都要能讲出"发现过程 + 修复方案 + 验证结果"
> 创建日期：2026-08-07

---

## 现状核实（已读代码确认）

| 项 | 确认结果 |
|----|----------|
| 管理端鉴权 | `Webconfig.java:20-23` 只有用户端拦截器；`JwtUserInterceptor.preHandle` 在 token 为空/无效时 `return true` 放行，形同虚设；`/admin/**` 接口匿名可调 |
| 管理端登录 | **完全缺失**：无登录接口、无前端登录页、无路由守卫；`employee` 表有 `username/password/status` 字段，初始账号 `admin/123456`（明文） |
| 用户端密码 | 注册/登录/改密全部明文存储与比对（`UserServiceImpl.java`） |
| 用户端登录模式 | Redis token（UUID 作 key）+ `RedisKey.USER_LOGIN_KEY`，30 分钟过期 —— 管理端可复用此模式 |
| 购物车越权 | `ShoppingCartServiceImpl.java` 按 id 直接操作，未校验 userId 归属 |
| 订单越权 | `UserOrderController.java:50` getById 未校验归属 |
| 支付回调 | `PayNotifyController.java` 只验签，未比对金额 |
| AI 订单工具 | `agent-service/app/agent/nodes.py:43` 调 `/user/order/list?userId=X`，后端忽略该参数、改从 JWT 取 userId，且无 token 被拦截 |

---

## A1 管理端登录 + 鉴权（最高优先级）

**目标**：`/admin/**` 全部接口要求登录，前端补登录页与守卫。

### A1.1 后端登录接口

**新增文件**：
- `backend/fashion-server/src/main/java/com/fashion/dto/AdminLoginDto.java` —— `{username, password}`
- `backend/fashion-server/src/main/java/com/fashion/vo/AdminLoginVo.java` —— `{token, employeeId, name}`

**修改文件**：
| 文件 | 改动 |
|------|------|
| `mapper/EmployeeMapper.java` | 新增 `Employee getByUsername(String username)` |
| `resources/mapper/EmployeeMapper.xml` | 新增对应 select |
| `service/EmployeeService.java` + `service/impl/EmployeeServiceImpl.java` | 新增 `login(AdminLoginDto)` |
| `controller/admin/EmployeeController.java` | 新增 `POST /admin/login`（放行名单外） |
| `constant/RedisKey.java` (fashion-common) | 新增 `ADMIN_LOGIN_KEY = "admin:login:"` |

**登录逻辑**（参照用户端 Redis token 模式）：
1. 按 username 查 employee；不存在 → 401
2. 校验 status=1（启用），禁用账号拒绝登录
3. 校验密码（A2 完成后为 BCrypt 比对）
4. 生成 UUID token → `redisTemplate.opsForHash().putAll(ADMIN_LOGIN_KEY + token, empMap)` → 过期 30 分钟
5. 返回 AdminLoginVo

### A1.2 管理端鉴权拦截器

**新增文件**：`backend/fashion-server/src/main/java/com/fashion/interceptor/AdminLoginInterceptor.java`
- 拦截 `/admin/**`，但放行 `/admin/login`
- 从 Header `Authorization` 取 token → 查 `ADMIN_LOGIN_KEY + token`
- 有效 → `BaseContext` 存 employeeId；无效 → 401 JSON

**修改文件**：`config/Webconfig.java`
- `registry.addInterceptor(adminLoginInterceptor).addPathPatterns("/admin/**").excludePathPatterns("/admin/login").order(0)`

**注意**：`JwtUserInterceptor` 目前对 `/**` 放行，需确认它不会干扰 `/admin/**`（它无 token 时 return true 放行，安全；有用户 token 时把 userId 写进 BaseContext —— 需要避免 admin 拦截器与它冲突，建议 admin 请求排除 jwtUserInterceptor 或用独立 context）。

### A1.3 前端管理端登录

**新增文件**：
- `frontend/fashion-admin/src/views/Login.vue` —— 登录表单（username + password）
- `frontend/fashion-admin/src/api/auth.js` —— `login()` / `logout()`，注入 token 拦截器

**修改文件**：
- `frontend/fashion-admin/src/api/*.js`（9 个）→ 统一抽 `frontend/fashion-admin/src/utils/request.js`，全局拦截器注入 `Authorization`、401 跳登录
- `frontend/fashion-admin/src/router/index.js` → 新增 `/login` 路由 + `beforeEach` 守卫（无 token 跳登录）+ 404 兜底
- `frontend/fashion-admin/src/App.vue` → 顶部加"退出登录"

**验证**：无 token 访问 `/admin/product/page` → 401；登录后正常访问；禁用账号拒绝。

---

## A2 密码安全（BCrypt）

**目标**：用户与员工密码哈希化，杜绝明文。

**新增依赖**：`fashion-server/pom.xml` 添加 `spring-security-crypto`（轻量，只引 crypto 不用整套 Spring Security）：
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

**修改文件**：
| 文件 | 改动 |
|------|------|
| `service/impl/UserServiceImpl.java` | `register()` 存 `BCrypt.hashpw(password)`；`login()` 密码登录用 `BCrypt.checkpw`；`changePassword()` 旧密码校验与新密码存储均改哈希 |
| `service/impl/EmployeeServiceImpl.java`（A1 新增 login 逻辑） | 存储/校验统一 BCrypt |
| 数据修复 SQL | 将已有 `user`/`employee` 明文密码替换为 BCrypt 哈希（admin/123456 → 预生成哈希，用 `UPDATE` 脚本，放 `mysql/` 新脚本文件） |

**验证**：注册新用户 → 数据库密码非明文；用明文登录成功；错误密码失败；admin 用 BCrypt 后旧明文登录被拒。

> ⚠️ 注意：改密码哈希后，**旧的登录测试账号全部需要同步迁移**（用脚本批量 UPDATE），否则无法登录。

---

## A3 下单金额防篡改

**目标**：服务端重算订单金额，忽略前端传值。

**修改文件**：`service/impl/OrderServiceImpl.java`（下单逻辑）
- 下单时根据购物车项 `product_sku` / `product` 价格**服务端重算** `totalAmount`，丢弃前端传入金额
- 校验商品归属与库存（与 Phase B 的 B5 联动）
- 前端 `CreateOrder.vue` 提交时仅传商品 id 列表，不再传金额（可保留展示）

**验证**：构造带篡改金额的请求 → 订单金额为服务端计算值。

---

## A4 支付回调金额校验

**目标**：回调验签后比对 `total_amount` 与订单金额，防重放/篡改。

**修改文件**：`controller/notify/PayNotifyController.java`
- 验签通过后，用 `out_trade_no`（订单号）查订单实际应付金额
- 与回调 `total_amount` 比对，不一致 → 拒绝并记日志
- 同时校验订单状态（已支付则幂等返回成功，防重复回调）

**验证**：篡改回调金额 → 拒绝；重复回调 → 幂等成功。

---

## A5 购物车接口越权（IDOR）

**目标**：所有按 id 操作的购物车接口校验归属。

**修改文件**：
- `service/impl/ShoppingCartServiceImpl.java` 的 `updateQuantity` / `delete` / `batchDelete`
- 各方法先 `selectById` 校验 `userId` 与 `BaseContext.getUserId()` 一致，不一致返回错误

**验证**：用户 B 的 token 操作用户 A 的购物车项 → 拒绝。

---

## A6 订单详情越权（IDOR）

**修改文件**：
- `controller/user/UserOrderController.java` 的订单详情接口
- 查询后校验 `order.userId == BaseContext.getUserId()`，不一致返回 403/404

**验证**：用户 B 查用户 A 的订单详情 → 拒绝。

---

## A7 AI 订单查询工具（修复 + 鉴权）

**目标**：让 agent-service 的订单查询真正可用且不越权。

**修改文件**：
- 后端：新增带鉴权的 agent 专用订单接口（如 `GET /user/agent/order/list`），从 `BaseContext` 取 userId（走用户登录态）
- `agent-service/app/agent/nodes.py`：订单查询工具改为调用新接口；由 Java `AgentController` 透传用户 token，Python 侧携带
- 相关提示词与回复逻辑同步调整

**验证**：AI 会话中问"我的订单" → 返回当前登录用户订单；非本人订单不返回。

---

## A8 管理端前端统一请求层（与 A1.3 合并）

已在 A1.3 覆盖：统一 `utils/request.js` + token 注入 + 401 处理 + 路由守卫。此处补充：
- `fashion-admin/src/api/*.js` 全部改为引用统一 request 实例
- 清理 console.log 残留（联动 Phase C C6）

---

## 执行顺序

```
A1 管理端登录 + 鉴权（最大漏洞，先修）
  ↓
A2 密码 BCrypt（依赖 A1 的登录改造）
  ↓
A4 支付回调金额校验（独立，快）
  ↓
A5 购物车越权 + A6 订单越权（独立，快）
  ↓
A3 下单金额防篡改（涉及下单逻辑，与 Phase B 联动）
  ↓
A7 AI 订单工具（涉及后端 + Python + 前端，放最后）
```

## 验证总清单

- [x] `/admin/**` 匿名访问 → 401（AdminLoginInterceptor 拦截，Webconfig order 0）
- [x] admin 登录成功 → 正常访问；登录失败/禁用账号 → 拒绝（EmployeeServiceImpl.login）
- [x] 数据库密码为 BCrypt 哈希，非明文（register/save/changePassword 均哈希 + 迁移脚本 mysql/migrate_password_bcrypt.sql）
- [x] 篡改下单金额 → 服务端重算（OrderServiceImpl.create 基于购物车×商品价重算）
- [x] 篡改支付回调金额 → 拒绝；重复回调幂等（PayNotifyController 金额比对 + status 幂等）
- [x] 用户 B token 操作/查看用户 A 数据 → 拒绝（购物车归属校验 + 订单详情归属校验）
- [x] AI 订单查询返回当前登录用户订单（/user/agent/order/list 从 BaseContext 取 userId）
- [x] 前端 admin 无 token 跳登录；401 统一处理（request.js + router 守卫）

> 编译/测试验证：`cd backend && mvn compile` 通过；`mvn test` 6/6 通过；`fashion-admin && fashion-client` 均 `npm run build` 通过。
> 额外修复：`frontend/fashion-client/src/views/ProductDetail.vue` 存在一处多余 `}` 导致 CSS 语法错误、阻塞 build，已顺手修复（非安全项）。

## 审查

### code-reviewer 审查结果（2026-08-07）

| # | 发现 | 处置 |
|---|------|------|
| 1 | **下单重算丢失活动/秒杀券优惠**（`OrderServiceImpl.create` 只按商品价重算，忽略 activityId/couponId，用户被多收） | **已修复**：注入 `SeckillActivityMapper`/`SeckillCouponMapper`，服务端按与结算页一致的逻辑应用优惠，`orders.amount = 商品总价 - 优惠`，并写入 `originalPrice`/`seckillActivityId`/`seckillCouponId`/`isSeckill` |
| 2 | 游客 AI 聊天被拒（`/user/agent/chat` 要求登录态） | **保留现状**：改前即被 `loginInterceptor` 拦截（`/user/**` 未放行）+ 控制器 `userId must be positive` 双重拒绝，游客聊天从未可用；放开会重新引入 A7 越权，故不放行 |
| 3 | `/upload/oss` 完全匿名可上传（OSS 滥用） | **已修复**：`AdminLoginInterceptor` 追加拦截 `/upload/**`，且该路径放行**用户 token 或管理端 token** 任一有效登录态（用户端 FileUpload/AddReview 也用此接口） |
| 4 | 购物车多 SKU 更新命中错误行（`findByUserIdAndProductId` 取最近一条） | **记录为已知限制**：控制器 update 契约只传 productId+number、不传 skuInfo，属既有接口契约缺陷，不在 A5（归属校验）范围内；归属安全已修 |
| 5 | `BaseContext.getUserId()!=null?getUserId():1L` 兜底默认成用户1，有越权风险 | **已修复**：抽取 `currentUserId()`，未登录直接抛错，删除 1L 兜底 |
| 6 | 管理端 token 活跃不续期（30 分钟强制下线） | **已修复**：`AdminLoginInterceptor` 校验通过后 `expire` 续期 30 分钟，与用户 token 一致 |
| 7 | Redis admin hash 存了 BCrypt 密码哈希 | **接受现状**：私有 Redis key，与用户 token 模式一致，无实际风险 |
| - | 拦截器顺序、支付回调金额比对（BigDecimal scale 无关）、订单 productIds=购物车id 契约、token 端到端透传、admin request.js 401 防跳环 | **确认正确**，无改动 |

> 修复后复验：`mvn compile` + `mvn test`（6/6）通过；两端 `npm run build` 通过。
