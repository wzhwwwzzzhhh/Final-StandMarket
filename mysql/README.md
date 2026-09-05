# MySQL 脚本说明

> 数据库结构变更的**唯一事实来源已迁移到 Flyway 版本化迁移**，位于 `backend/fashion-server/src/main/resources/db/migration/`。
> 本目录脚本自 B10 起仅作**历史参考与结构核对依据**，不再是迁移队列。

## 版本化迁移（Flyway，事实来源）

```text
backend/fashion-server/src/main/resources/db/migration/
├─ V1__baseline_existing_schema.sql        # 当前全量结构（schema-only，29 表；保留 dev 库 AUTO_INCREMENT 起始值）
├─ V2__verify_baseline_invariants.sql      # 严格只读不变量校验（表/列/唯一索引，引用即失败）
└─ V3__seed_reference_data.sql             # category 参考数据幂等 seed（INSERT IGNORE）
```

规则：

1. **已发布迁移不可修改**。新增结构只能追加新版本（`V4__...` 起），命名 `V<递增版本>__<描述>.sql`。
2. `V2` 在首次 `migrate` 时执行（空库路径在 `V1` 之后、存量库路径在 baseline 之后；Flyway 不重跑已应用迁移）：任何预期表/列/索引缺失会**中止迁移**，防止结构不完整存量库被静默 baseline。
3. `V2` 必须严格只读（仅 `SELECT ... WHERE 1=0` 引用 + 条件失败），不得包含 DDL/DML。
4. 管理员/员工登录凭据**不得**进入版本化迁移（P1-2 强制带外初始化：文档化人工 SQL + 强随机口令 + 首登改密）。
5. 演示数据（`data_enrichment.sql`）不进入迁移，仅开发使用。

## 故障恢复

- `migrate` 失败会在 `flyway_schema_history` 留下 `FAILED` 行并中止后续版本。
- 恢复流程：修复根因后执行 `mvn org.flywaydb:flyway-maven-plugin:8.5.13:repair`（清除 FAILED 行）→ 重新 `migrate`。不要手工删 history 行。
- 生产迁移前必须 `mysqldump --single-transaction --set-gtid-purged=OFF` 全量备份并在测试库演练。

## 历史脚本（仅参考）

以下脚本用于理解历史结构与 B1-B9 增量，**不要**把它们当作自动发布的迁移队列：

| 文件 | 用途 |
|---|---|
| `final07.sql` | 历史全量导出（19 表收敛基座，含 B6/B7 产物）；schema 与部分种子数据 |
| `payment_table.sql` / `refund_table.sql` / `favorite_table.sql` / `coupon.sql` / `operation_log.sql` | 独立建表脚本（已并入 V1） |
| `add_payment_active_unique.sql` | B1 支付活动唯一约束（已并入 V1；含脏数据 guard） |
| `add_order_inventory_state.sql` | B2 普通订单库存状态（已并入 V1；含履约订单 guard） |
| `add_refund_review_state.sql` | B3 退款四状态（已并入 V1） |
| `add_seckill_state_inventory.sql` | B5 秒杀状态机（已并入 V1） |
| `add_seckill_mq_reliability.sql` | B6 可靠消息表（已并入 V1） |
| `add_review_integrity.sql` | B7 评价唯一约束（已并入 V1） |
| `add_product_cache_consistency.sql` | B8 商品目录版本表（已并入 V1） |
| `add_discount_to_activity.sql` / `add_indexes.sql` / `add_unique_index_orders_number.sql` / `migrate_password_bcrypt.sql` | 增量/索引/一次性数据回填（已并入 V1 或属数据迁移） |

## 生产环境规则

1. **禁止**在已有生产库上直接执行完整导出 SQL 来“同步本地数据库”。
2. 结构变更统一走 Flyway 版本化迁移；已发布脚本不可修改。
3. 每次生产迁移前必须有 MySQL 可验证备份；删除字段、修改大表、批量更新等高风险操作必须单独评审。
4. 初始生产数据从全量脚本中拆出可审查的 schema 与 seed，去除测试用户、测试订单、密钥和演示数据。
5. 生产数据库日常数据以服务器/云数据库为准；本地排查只能使用脱敏副本。
6. 回滚策略为**只追加、不撤销**：以「迁移前全量备份恢复 + 前向修复迁移」处理，禁止删表/反向 DDL 兜底。
