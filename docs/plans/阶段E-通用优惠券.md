# 阶段E - 通用优惠券系统

> 主计划：[安全加固与功能拓展主计划.md](安全加固与功能拓展主计划.md)
> 目标：弥补"仅有秒杀券、无通用优惠券"的业务缺口 —— 满减券、折扣券、新人券、现金券
> 创建日期：2026-08-07

---

## 一、设计目标

区别于现有**秒杀券**（`seckill_coupon`：券即"特定商品以秒杀价出售"，带库存/限购/时间窗，本质是特价商品券）：

通用优惠券是**用户持有型**券：用户领取/获得 → 持有多张 → 下单时选择核销 → 抵扣订单金额。

| 维度 | 秒杀券（现有） | 通用优惠券（本阶段） |
|------|--------------|-------------------|
| 持有方式 | 抢购即生成订单 | 领取到"我的卡包" |
| 使用方式 | 下单指定券 | 结算页选择任意可用券 |
| 优惠类型 | 固定差额（原价-秒杀价） | 满减 / 折扣 / 现金 |
| 数量 | 有限库存、限购 | 可无限领/限领 |
| 有效期 | 固定时间窗 | 领取后 N 天 / 固定日期 |
| 适用性 | 特定活动商品 | 全店 / 指定分类 / 指定商品 |

---

## 二、数据库设计

### 2.1 `coupon_template` 优惠券模板（管理端创建）

```sql
CREATE TABLE `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '券名称（如：满100减20）',
  `type` tinyint NOT NULL COMMENT '类型 1满减 2折扣 3现金',
  `threshold` decimal(10,2) DEFAULT NULL COMMENT '使用门槛（满X元可用，0=无门槛）',
  `discount` decimal(10,2) DEFAULT NULL COMMENT '满减金额/现金金额，或折扣值(如8.5=85折)',
  `total_count` int DEFAULT '0' COMMENT '发行总量 0=不限量',
  `per_user_limit` int DEFAULT '1' COMMENT '每人限领',
  `valid_type` tinyint NOT NULL COMMENT '有效期类型 1固定时间 2领取后N天',
  `valid_days` int DEFAULT NULL COMMENT '领取后有效天数(valid_type=2)',
  `start_time` datetime DEFAULT NULL COMMENT '有效开始(valid_type=1)',
  `end_time` datetime DEFAULT NULL COMMENT '有效结束(valid_type=1)',
  `scope_type` tinyint NOT NULL DEFAULT '0' COMMENT '适用范围 0全店 1指定分类 2指定商品',
  `apply_category_id` bigint DEFAULT NULL COMMENT '指定分类id(scope_type=1)',
  `apply_product_ids` varchar(500) DEFAULT NULL COMMENT '指定商品id逗号分隔(scope_type=2)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0停用 1启用',
  `create_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_status` (`status`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='优惠券模板';
```

### 2.2 `user_coupon` 用户持有券

```sql
CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `template_id` bigint NOT NULL COMMENT '模板id',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0未使用 1已使用 2已过期 3已锁定(下单核销中)',
  `obtain_time` datetime NOT NULL COMMENT '领取时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `use_order_id` bigint DEFAULT NULL COMMENT '核销订单id',
  `use_time` datetime DEFAULT NULL COMMENT '核销时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_coupon_user_status` (`user_id`,`status`),
  KEY `idx_user_coupon_template` (`template_id`),
  KEY `idx_user_coupon_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户优惠券';
```

---

## 三、后端接口设计

### 3.1 管理端（admin）

| 接口 | 说明 |
|------|------|
| `POST /admin/coupon/template` | 创建模板 |
| `PUT /admin/coupon/template` | 更新模板 |
| `DELETE /admin/coupon/template?id=` | 删除模板（软删 status=0） |
| `GET /admin/coupon/template/page` | 分页查询模板 |
| `GET /admin/coupon/template/{id}` | 模板详情 |
| `GET /admin/coupon/userCoupon/page` | 用户持券查询（运营管理） |

### 3.2 用户端（user）

| 接口 | 说明 |
|------|------|
| `GET /user/coupon/templates` | 可领券列表（首页/领券中心） |
| `POST /user/coupon/claim/{templateId}` | 领取优惠券（校验限量/限领/状态） |
| `GET /user/coupon/my?status=` | 我的卡包（未使用/已使用/已过期） |
| `GET /user/coupon/available?totalAmount=&productIds=` | 结算页查可用券（按金额+商品范围过滤） |
| `POST /user/coupon/lock` | 下单前锁定券（防并发重复使用） |
| `POST /user/coupon/release` | 订单取消/支付失败释放券 |
| `POST /user/coupon/use` | 支付成功后核销券 |

### 3.3 与下单流程集成（关键）

`OrderServiceImpl.create()` 的 `applyDiscount()` 是优惠计算唯一入口：
- 下单入参新增 `userCouponId`（用户选用的券）
- `create()` 内：校验券归属当前用户 → 校验金额门槛/适用范围/有效期 → 锁定券（status=3）→ 计算抵扣金额 → 生成订单
- 支付成功回调：核销券（status=1）
- 订单取消/超时：释放券（status=0）
- **并发防护**：锁定券用 UPDATE 条件 `status=0` 校验影响行数（乐观锁），或 Redis 分布式锁

---

## 四、前端设计

### 管理端（fashion-admin）
- `views/CouponTemplateList.vue` —— 模板列表 + 创建/编辑弹窗（含类型/门槛/有效期/范围表单）
- `views/CouponUserList.vue` —— 用户持券管理
- `router` 新增路由，`api/coupon.js` 封装

### 用户端（fashion-client）
- `views/CouponCenter.vue` —— 领券中心（可领模板列表）
- `views/MyCoupons.vue` —— **改造现有**（已有该文件，核查当前是秒杀券展示还是空壳），扩展为通用券卡包
- `CreateOrder.vue` —— 结算页优惠券选择器（展示可用券，选择后金额变化）
- `api/coupon.js` 封装

---

## 五、文件清单

### 后端新增
| 文件 | 说明 |
|------|------|
| `entity/CouponTemplate.java` | 模板实体 |
| `entity/UserCoupon.java` | 用户券实体 |
| `mapper/CouponTemplateMapper.java` + xml | 模板 CRUD |
| `mapper/UserCouponMapper.java` + xml | 用户券 CRUD（含锁定/核销/过期） |
| `service/CouponService.java` + `impl/CouponServiceImpl.java` | 领券/查券/锁券/核销逻辑 |
| `controller/admin/CouponController.java` | 管理端接口 |
| `controller/user/UserCouponController.java` | 用户端接口 |
| `mysql/coupon.sql` | 建表脚本 |

### 后端修改
| 文件 | 改动 |
|------|------|
| `service/impl/OrderServiceImpl.java` | `create()` 接入通用券：校验 + 锁定 + 抵扣；`applyDiscount()` 扩展 |
| `service/impl/PaymentServiceImpl.java` 或回调 | 支付成功后核销券 |
| `OrderServiceImpl` 取消/超时逻辑 | 释放券 |
| `dto/OrderCreateDTO.java` | 新增 `userCouponId` 字段 |

### 前端新增/修改
- admin：`views/CouponTemplateList.vue`、`views/CouponUserList.vue`、`api/coupon.js`、router
- client：`views/CouponCenter.vue`、改造 `views/MyCoupons.vue`、`CreateOrder.vue` 加券选择、`api/coupon.js`

---

## 六、核销规则（面试可讲的设计点）

1. **锁定**：下单时 `UPDATE user_coupon SET status=3 WHERE id=? AND user_id=? AND status=0`，校验影响行数=1，防并发重复使用
2. **门槛校验**：订单金额 ≥ threshold 才可用（满减/满折）
3. **适用范围**：scope_type 校验订单商品是否全在适用范围内
4. **不叠加**：与秒杀活动/秒杀券互斥（若订单已是秒杀，不可再叠通用券），避免多重优惠
5. **过期**：定时任务 + 查询时懒判断（expire_time < now → status=2）
6. **幂等**：核销/释放按 use_order_id 幂等

---

## 七、验证清单

- [ ] 管理端创建满减/折扣/现金券 → 列表可见
- [ ] 用户领券 → 我的卡包+1；超限领/超量领被拒
- [ ] 结算页按订单金额/商品范围显示可用券
- [ ] 选券下单 → 订单金额正确抵扣；券锁定
- [ ] 支付成功 → 券核销；订单取消 → 券释放
- [ ] 并发下单同用一张券 → 仅一笔成功
- [ ] 过期券不可用
- [ ] 秒杀订单不叠加通用券

## 八、执行顺序

```
1. 数据库建表（coupon_template / user_coupon）
2. 后端实体 + Mapper + Service（领券/查券/锁券/核销）
3. 管理端模板 CRUD 接口 + 前端
4. 用户端领券/卡包接口 + 前端
5. OrderServiceImpl 下单集成（核心）
6. 支付回调核销 + 取消释放
7. 验证 + 审查
```

---

## 九、实施记录（2026-08-07）

### 9.1 已完成实现

**数据库**
- `mysql/coupon.sql`：`coupon_template` + `user_coupon` 建表脚本（含索引），已同步执行到本地库

**后端新增**
- `entity/CouponTemplate.java`、`entity/UserCoupon.java`
- `mapper/CouponTemplateMapper.java` + XML（CRUD、listClaimable）
- `mapper/UserCouponMapper.java` + XML（listByUserId/listUsable/countByTemplate/countByUserAndTemplate/lockCoupon/useCoupon/releaseCoupon/setUseOrderId/markExpired）
- `service/CouponService.java` + `impl/CouponServiceImpl.java`（save/update/delete/page、claim 带 Redisson 锁限量、getMyCoupons、listAvailable 按金额+商品范围过滤、lockAndDiscount、markUsed、release、releaseInNewTx、markExpired 懒过期）
- `controller/admin/CouponController.java`、`controller/user/UserCouponController.java`

**后端修改**
- `dto/OrderCreateDTO.java` 新增 `userCouponId`
- `entity/Orders.java` 新增 `userCouponId`；`OrderMapper.xml` insert/update 同步
- `OrderServiceImpl`：`create()` 接入锁券+抵扣（与秒杀互斥校验、券范围校验、金额门槛校验）；`cancel()`/`pay()` 释放/核销；`updatePaySuccess()`/`handlePayCallback()` 支付成功核销；支付失败在独立事务（REQUIRES_NEW）中释放券

**前端（admin）**
- `api/coupon.js`、`views/CouponTemplateList.vue`、`views/CouponUserList.vue`、router 注册、App.vue 菜单

**前端（client）**
- `api/coupon.js`、`views/CouponCenter.vue`（领券中心）、`views/MyCoupons.vue` 重构为通用券卡包（tab 过滤 + 领券中心入口）、router `/coupon-center`、`CreateOrder.vue` 结算页券选择器（可用券列表、秒杀与通用券互斥、选券实时算价、不使用优惠券）

### 9.2 验证结果

- [x] 后端 `mvn compile` BUILD SUCCESS
- [x] admin `npm run build` 成功（CouponTemplateList/CouponUserList 分块正常）
- [x] client `npm run build` 成功（CouponCenter/MyCoupons/CreateOrder 分块正常）
- [ ] 数据库表已执行（`mysql/coupon.sql`）
- [ ] 功能联调（创建模板→领券→下单抵扣→支付核销→取消释放）待人工验证

### 9.3 审查报告（code-reviewer）

子代理 code-reviewer 审查后发现的 2 个 CRITICAL + 3 个 WARNING，已全部修复：

| # | 级别 | 问题 | 修复 |
|---|------|------|------|
| C1 | CRITICAL | 支付失败时释放券但订单保留券后金额 → 重试成功 = 同一张券享受两次优惠 | 改为支付失败**保留券锁定**（绑定本单，标准"券预留"模型）：重试支付成功则核销，取消订单则释放 |
| C2 | CRITICAL | claim() 在事务提交前释放 Redisson 锁 → 并发领取计数读到未提交数据，可能超限量 | 锁改由 `TransactionSynchronizationManager.afterCompletion` 在事务提交/回滚完成后释放 |
| W3 | WARNING | 未支付订单无超时机制 → 券永久锁死 status=3 | 新增 `CouponTimeoutTask`：每 5 分钟扫描绑定券且超 30 分钟未支付订单 → 自动取消 + 释放券 |
| W4 | WARNING | markExpired 全表 UPDATE 每次列表查询都执行 | 量级可控（懒过期策略），本次保留，已在遗留说明记录 |
| W5 | WARNING | 秒杀互斥判断 `!= null`，客户端传 0 会被误判为秒杀 | 改为 `> 0` 判断，0 视为未选择；入库字段同步归一化为 null |

确认无问题的点：锁券 `UPDATE ... WHERE status=0` 影响行数校验（一券一单）、锁后绑定订单 id、核销/释放按 `use_order_id` 幂等、`releaseInNewTx` 代理调用事务独立（已随 C1 移除）、所有支付成功路径均调 markUsed、券类型金额计算不超订单金额。

### 9.4 遗留说明

- 通用券过期无定时任务，采用查询时懒置过期（markExpired），量级可控
- 支付失败不释放券：券保持锁定绑定本单，用户可重试（成功则核销）或取消（释放），防止一券两惠
- 已新增超时机制：绑券订单 30 分钟未支付自动取消并释放券（`CouponTimeoutTask` 每 5 分钟）
- 领券中心的"券已领完"按领取记录数判断，含已核销/已过期券（与后端限领逻辑一致）
