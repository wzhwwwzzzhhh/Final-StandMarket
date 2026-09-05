# B10-db-migration-baseline · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| 空测试库可以按文档初始化到当前结构 | 全新空库 `fashion_shop_b10_fresh` 经 Flyway migrate（baselineOnMigrate=true）→ `flyway_schema_history` 显示 V1/V2/V3 全 APPLIED（success=1），无 BASELINE 行；30 张表（29+history）；category=10（V3 seed）。 | ✅ |
| 已有结构的测试库可以建立 baseline 后执行后续迁移 | 存量库 `fashion_shop_b10_empty`（有结构无 history）→ Flyway baseline 在 V1（BASELINE 行，V1 未执行）→ V2/V3 APPLIED。真实 dev 库 `fashion_shop` 修复后 baseline 同样成功（v1 BASELINE + v2/v3 APPLIED）。 | ✅ |
| 重复启动不会重复执行或修改已成功迁移 | 对已 baseline 的 `fashion_shop_b10_empty` 二次 migrate：`Successfully validated 3 migrations` → `Schema is up to date. No migration necessary.`（BUILD SUCCESS）。 | ✅ |
| 支付、退款、评价、收藏、优惠券和审计表均在迁移历史中可追踪 | V1 含 payment/refund/review/favorite/coupon_template/user_coupon/operation_log 及全部 29 表；V1 文件本身 `git log` 可追踪；V2 不变量校验对 20 个唯一索引（含 uk_payment_active_order、uk_review_order_product、uk_seckill_order_active_user_coupon、idx_orders_number、idx_user_phone 等）逐一断言。 | ✅ |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-05 11:5x | `mysqldump --no-data --skip-triggers --set-gtid-purged=OFF fashion_shop_b10_scratch > V1__baseline_existing_schema.sql` | 0；29 个 CREATE TABLE | scratch 库 = final07.sql(19 表) + favorite/payment/refund/coupon/operation_log + add_payment_active_unique/add_refund_review_state/add_discount_to_activity/add_product_cache_consistency/add_indexes；无 PROCEDURE/FUNCTION/TRIGGER/VIEW/EVENT；payment 注释干净（订单ID 非乱码） |
| 2026-09-05 12:0x | V2 生成（information_schema 程序化，29 表 + 全部列 + 20 唯一索引，478 行） | 0 | 引用即失败只读模式，无 DDL/DML |
| 2026-09-05 12:0x | V3 生成（category INSERT IGNORE，10 行，与 final07 同源） | 0 | 管理员凭据带外初始化，不入迁移（P1-2） |
| 2026-09-05 12:1x | Flyway 8.5.13 plugin migrate @ 全新空库 `fashion_shop_b10_fresh` | 0；V1/V2/V3 APPLIED | 场景 A：空库路径 |
| 2026-09-05 12:1x | Flyway migrate @ 存量库 `fashion_shop_b10_empty`（有结构无 history） | 0；BASELINE v1 + V2/V3 APPLIED | 场景 B：存量库路径 |
| 2026-09-05 12:1x | Flyway migrate 二次 @ `fashion_shop_b10_empty` | 0；`Schema is up to date. No migration necessary.` | 幂等 |
| 2026-09-05 12:2x | Flyway migrate @ `fashion_shop_b10_broken`（旧 19 表结构克隆） | 1；`Migration of schema ... to version "2 - verify baseline invariants" failed! ... Table 'fashion_shop_b10_broken.coupon_template' doesn't exist`（SQL 42S02/1146） | 场景 C：残缺库 fail 注入，P1-1 防线按设计拦截 |
| 2026-09-05 12:1x | 本地 `fashion_shop` 修复：应用 coupon/operation_log/add_seckill_mq_reliability/add_product_cache_consistency/add_refund_review_state/add_seckill_state_inventory/add_review_integrity/add_discount_to_activity/add_indexes | 0；19→29 表 | 另 add_unique_index_orders_number 报 index already exists（幂等跳过） |
| 2026-09-05 12:2x | 脏数据对账（用户授权）：DELETE payment order=43829 status=0（4 条）；UPDATE orders SET status=4 WHERE status IN (2,3)（4 条） | 0 | 解除 B1/B2 guard；B1 唯一约束、B2 chk_orders_stock_deducted 补全 |
| 2026-09-05 12:3x | Flyway migrate @ 真实 `fashion_shop` | 0；BASELINE v1 + v2 verify APPLIED + v3 seed APPLIED | 存量 dev 库 happy path；V3 Duplicate entry 为 INSERT IGNORE 预期 |
| 2026-09-05 13:2x | `cd backend && mvn test` | 0；572 tests run, 0 failures, 0 errors, 147 skipped | 含 8 处构造函数 @Autowired 修复后回归 |
| 2026-09-05 13:3x | `git diff --check` | clean | 无空白/冲突标记 |

## 备份与可恢复性

- 迁移前全量备份：`docs/workpack/B10-db-migration-baseline/backups/fashion_shop_before_b10_20260905_121110.sql`（84KB，`--single-transaction --set-gtid-purged=OFF`）。
- 回滚策略：只追加不撤销；以备份恢复 + 前向修复迁移处理；`clean-disabled=true` 防误删。

## 范围补充记录（用户授权）

以下三项决策均在 2026-09-05 经 AskUserQuestion 由用户拍板（选项记录可溯源）：
1. **Design D1-D4 确认**：双模式+V2 校验 / 我连本地库生成 baseline / category 幂等 V3 seed / 存量脚本保留为历史参考。
2. **本地库 B1/B2 脏数据对账授权**：删 order 43829 的 4 条重复待支付流水 + 完成 4 条履约中测试订单（保留 dev 数据）。
3. **B8 构造函数缺陷修复授权**：扫描确认 8 处同类问题后一并修复（当时约定「若有下一个启动问题继续处理」）。
- 8 处 Bean 构造函数 `@Autowired` 修复（B6/B8 预存在装配缺陷，master 应用此前无法启动）：ProductCatalogMutationCoordinator、SeckillReliablePublisher、SeckillBusinessDeadLetterService、SeckillCompensationExecutor、SeckillCompensationRecoveryTask、SeckillInvalidMessageService、SeckillMessageRecoveryTask、SeckillReconciliationService（+16 行，行为不变；独立审查确认注解均在生产构造函数、seam 未误标）。
- 本地 dev 库脏数据对账（见上）：4 条重复待支付流水删除、4 条履约中测试订单完成。
- 发现（未修）：`fashion_shop` 历史乱码注释（payment/refund/review 旧表，已由 V1 干净版本取代）；应用完整启动仍需 FASHION_AGENT_BASE_URL 与 JWT 密钥环境变量（用户启动时提供，未伪造）。
- `resources/init.sql` 已加头注释标注「已被 V1 取代」（P2-1 处置），避免与 V1 的 shopping_cart 结构双写。
- V1 携带 dev 库 AUTO_INCREMENT 起始值（如 orders=43829），全新库自增不从 1 开始（P3-2，基线来源的预期特性）。

## Not run or blocked

- **应用全量启动（`spring-boot:run`）未作为 B10 完成依据**：B10 §8 门禁以 Flyway 插件三路径验证为准。应用完整启动在修复 8 处构造函数后仍缺 `FASHION_AGENT_BASE_URL`/JWT 环境变量（正常启动由用户提供），且被环境安全策略禁止用占位密钥强制启动；不作为本 workpack 阻塞，另记环境要求。

## Local delivery summary

- Flyway 已接入（pom + application.yml），V1/V2/V3 版本化迁移全部就位并三路径验证通过。
- 本地 `fashion_shop` 已从 19 表旧结构修复到 29 表全结构并成功 baseline。
- 后端 `mvn test` 572 通过，`git diff --check` 干净。
- 待 Phase 3 独立审查通过后标记本地交付完成。
