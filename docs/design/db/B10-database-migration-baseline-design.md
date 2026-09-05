# B10 数据库发布基线 · Design

> Status: 已确认（2026-09-05，用户确认 D1-D4）
> Requirement source: [阶段 B B10](../plans/阶段B-P0P1交易链路修复.md#b10数据库发布基线p1)
> Updated: 2026-09-05

## 1. Goal and scope

把「人工按顺序猜测多个 SQL 文件」的数据库维护方式，收敛为「一个可重复、可审查、可回滚的版本化迁移基线」：以 Flyway 接入现有库，建立 baseline，后续结构变更只追加新版本。

### In scope

- 引入 Flyway（`flyway-core` + `flyway-mysql`，Spring Boot 2.7.15 BOM 管理版本 8.5.13）。
- 生成 `V1__baseline_existing_schema.sql`：当前全量结构（schema-only，不含演示数据）。
- 生成 `V2__verify_baseline_invariants.sql`：自动校验 baseline 不变量（表/列/索引/CHECK 全集），在空库与存量库两条路径上都执行，缺失即 `SIGNAL` 失败（防结构缺失被静默固化）。
- `application.yml` 增加 Flyway 配置：`baseline-on-migrate=true`、`baseline-version=1`、`clean-disabled=true`、`validate-on-migrate=true`。
- 空测试库可按文档初始化到当前结构；已有结构库可建立 baseline 后继续后续迁移。
- `mysql/README.md` 与 `README.md` 同步迁移工作流说明。

### Out of scope

- 不引入生产部署/CD、真实退款/支付 API、业务逻辑改动。
- 不在迁移内注入演示数据（`data_enrichment.sql` 保持开发专用）与任何登录凭据。
- 不提供 `flyway clean` / undo。

## 2. Current behavior and constraints

当前结构 = `mysql/final07.sql`（19 张表，已包含 B6/B7 产物）+ 6 张独立建表脚本（`payment`/`refund`/`favorite`/`coupon_template`+`user_coupon`/`operation_log`/B8 `product_projection_*`）+ 约 10 个增量脚本的 ALTER（`add_*`）。

- 现有 `mysql/*.sql` 脚本不是可重复的迁移队列：部分脚本有 `SIGNAL` guard（`add_*`），部分 `IF NOT EXISTS`，`add_discount_to_activity.sql` 依赖目标库是否已执行。
- 没有任何脚本能一次性代表「当前完整结构」；**权威结构来源是本地开发库（`fashion_shop`）**。
- 全仓脚本**无**存储过程/函数/触发器/事件/视图（`add_*` 内的迁移辅助过程均在脚本末尾 `DROP`）。`migrate_password_bcrypt.sql` 是一次性**数据回填**（不贡献结构 DDL），归类为数据迁移而非结构清单。
- 存在大量 CHECK 约束（如 `chk_refund_status_b3`、`chk_seckill_message_domains`、`chk_orders_stock_deducted`），需纳入不变量校验。
- `application.yml` 未配置 `spring.sql.init`，Spring Boot 2.7 默认 `spring.sql.init.mode=embedded` 对 MySQL 不自动执行；`resources/init.sql` 无代码引用、无冲突，但它是已跟踪文件且会创建 `shopping_cart`（与 V1 双写地雷），Flyway 落地后应删除或标注已被 V1 取代。
- Flyway 版本由 Spring Boot 2.7.15 BOM 管理（8.5.13）；MySQL 支持需要 `flyway-mysql` 模块。

## 3. Design decisions

### D1 版本化模型：V1 baseline + V2 不变量 + append-only

- `V1__baseline_existing_schema.sql` = 当前全量结构（schema-only）。
- `spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1`：
  - **空库**：schema 为空，Flyway 不 baseline，直接执行 V1（全量结构）、V2（不变量校验）与后续 V3+。
  - **存量库**：schema 非空且无 `flyway_schema_history`，Flyway 自动把 V1 标为 BASELINE（不执行 V1），执行 V2 不变量校验与 V3+。
- **自动不变量防线（防 P1）**：`V2__verify_baseline_invariants.sql` 用 `information_schema` + `SIGNAL` 断言全部预期表/列/索引/CHECK 存在。存量库结构不完整（缺某 B1-B9 增量）时 **V2 当场失败并中止启动**，而不是被静默 baseline；空库路径上 V1 刚建全结构，V2 通过。
- 已发布迁移不可修改，新增结构只追加 `V3__...` 起的新版本。

### D2 baseline 来源：从活库 dump 生成并对照脚本核对

- 用 `mysqldump --no-data --skip-triggers`（**不带 `--routines`**）从本地 `fashion_shop` 导出当前结构作为 V1 蓝本。
- 导出后校验 V1 文件内无 `PROCEDURE`/`FUNCTION`/`TRIGGER`/`VIEW`/`EVENT`。
- 对照 `final07.sql` + `add_*`（仅结构部分）+ 独立建表脚本逐表核对：每个预期表/列/索引/**CHECK 约束**/字符集/排序规则必须出现在 V1，防止 dump 与脚本长期漂移。
- **Pre-baseline 目标库 diff**：对每个要建立 baseline 的目标库（dev/prod）执行「V1 预期对象全集 × 目标库实际对象」diff 并写入 `evidence.md`；diff 有缺口（或存在意外残留 `migrate_*` 过程）时停止，不 baseline。
- 核对表清单（AC4 可追踪）：`payment`、`refund`、`review`、`favorite`、`coupon_template`、`user_coupon`、`operation_log`、`product_projection_*` 及 `orders`/`seckill_order`/`seckill_*` 的 B1-B9 增量列与唯一索引。

### D3 seed 数据策略（凭据不进迁移）

- V1 只含结构，`CREATE TABLE` 不带 INSERT。
- **管理员/员工登录凭据不进版本化迁移**（P1：版本库提交已知口令 BCrypt = 生产后门）。admin 初始化走带外方式：文档化人工 SQL + 一次性强随机口令 + 首次登录强制改密，或在部署时经环境变量/密钥注入。
- `category` 等无敏感参考数据：可选「幂等 seed 迁移（`INSERT IGNORE`/`ON DUPLICATE KEY UPDATE`）」或「文档化初始化」，二者在 §9 由用户确认。
- `data_enrichment.sql`（演示数据）不进入迁移，保持开发专用。

### D4 存量脚本处置

- `db/migration/` 成为唯一迁移事实来源。
- `mysql/*.sql` 保留为历史参考与结构核对依据；`mysql/README.md` 重写为「历史脚本仅参考，新变更走 Flyway」。
- `resources/init.sql` 标注已被 V1 取代（或删除）。

### D5 安全配置

- `spring.flyway.clean-disabled=true`：禁止 `flyway clean` 误删。
- `spring.flyway.validate-on-migrate=true`：保护**真正 APPLY 过的迁移**（V2+）；V1 在 baseline 路径上是 BASELINE 行、无 checksum，`validate` 不覆盖——V1 的完整性靠「已发布迁移不可修改」策略 + 代码审查 + 周期空库重跑探测（见 §6）。
- 生产迁移前必须 `mysqldump --single-transaction --set-gtid-purged=OFF` 全量备份（含数据）并有恢复演练记录。

## 4. Contracts and state transitions

本 Design 不改变任何应用内业务状态机或外部契约，只改变数据库结构变更的分发方式。

迁移执行顺序（空库）：`V1`（结构）→ `V2`（不变量校验）→ `V3+`。
迁移执行顺序（存量库）：baseline(V1) → `V2`（不变量校验）→ `V3+`。

## 5. File-level change surface

| 文件 | 变更 |
|---|---|
| `backend/fashion-server/pom.xml` | + `org.flywaydb:flyway-core`、`org.flywaydb:flyway-mysql`（BOM 管理版本） |
| `backend/fashion-server/src/main/resources/application.yml` | + `spring.flyway.*` 配置块 |
| `backend/fashion-server/src/main/resources/db/migration/V1__baseline_existing_schema.sql` | 新增：当前全量结构（schema-only） |
| `backend/fashion-server/src/main/resources/db/migration/V2__verify_baseline_invariants.sql` | 新增：`information_schema` + `SIGNAL` 不变量校验 |
| `backend/fashion-server/src/main/resources/db/migration/V3__seed_reference_data.sql` | 新增：仅当 D3 选「幂等 seed」 |
| `mysql/README.md` | 重写：历史脚本仅参考 |
| `README.md` | 迁移工作流说明 |
| `resources/init.sql` | 标注已被 V1 取代（或删除） |

## 6. Failure handling, idempotency, and compensation

- **幂等**：Flyway `flyway_schema_history` 表 + checksum 保证重复启动不重复执行。
- **结构不完整存量库被误标 baseline（最大风险）**：V2 不变量校验在 baseline/空库路径的首次 `migrate` 时执行（Flyway 不重跑已应用迁移），结构缺失当场 `SIGNAL` 失败，阻止静默固化；pre-baseline 目标库 diff 作为执行前防线。两道防线均记录在 `evidence.md`。
- **V3+ 与已应用结构冲突**：`validate-on-migrate` 校验失败阻止启动；修复方式是追加修正版本，不修改已发布脚本。
- **V1 完整性探测**：周期用全新空库执行一次 `migrate`（V1 会被 APPLY 并记录 checksum），发现 V1 与基线漂移。
- **跨存储**：不涉及 Redis/RabbitMQ，无跨存储一致性负担。

## 7. Migration, compatibility, and rollback

- 回滚策略 = **只追加、不撤销**：社区版 Flyway 无 undo；出现问题时以「迁移前全量备份恢复 + 前向修复迁移」处理，禁止删表/反向 DDL 兜底（对齐 mysql/README 规则 6）。
- 备份定义：`mysqldump --single-transaction --set-gtid-purged=OFF` 全量（结构+数据，可验证），并在测试库做恢复演练；记录于 `evidence.md`。
- 未来 V3+ 迁移尽量用纯 DDL 或幂等 guard；如确需 `DELIMITER $$` + 存储过程式迁移，先用目标 Flyway 版本跑草稿验证解析（本仓历史风格未在 Flyway 8.5.13 验证）。
- 空库初始化、存量库 baseline、重复启动三类场景各自留演练记录。
- 生产执行仍受阶段 B 门禁（B0-AC6、B11）与单独授权约束。

## 8. Verification gates

1. **空库初始化**：新建空测试库 → `migrate` → 全部预期表/索引/CHECK 存在（V2 校验通过）+ `flyway_schema_history` 中 V1、V2 APPLIED。
2. **存量库 baseline**：对本地 `fashion_shop` 执行 migrate → `flyway_schema_history` 出现 version=1、type=BASELINE（V1 未重执行），V2 不变量校验 APPLIED，无结构迁移被重复执行。
3. **重复启动幂等**：连续两次 `migrate`，第二次报 0 migrations。
4. **AC4 追踪**：`payment/refund/review/favorite/coupon/operation_log` 结构均来自 V1，可 `git log` 追踪。
5. **防御有效性**：在临时残缺库上执行 migrate，V2 按预期 `SIGNAL` 失败（故障注入）。
6. 回归：`cd backend; mvn test`、`git diff --check`。

## 9. Decisions requiring user confirmation

| # | 决策 | 已确认选择 |
|---|---|---|
| D1 | 双模式 baseline + V2 自动不变量校验（空库执行 V1 / 存量库 baseline 跳过 V1） | ✅ 双模式 + V2 校验 |
| D2 | baseline 从本地活库 `mysqldump --no-data --skip-triggers` 生成 | ✅ 我连本地库生成 |
| D3 | seed 数据策略：管理员凭据带外初始化；`category` 参考数据 | ✅ category 幂等 V3 seed（`INSERT IGNORE`） |
| D4 | 存量 `mysql/*.sql` 处置 | ✅ 保留为历史参考 |

## 10. Independent review

- Verdict: **PASS（2026-09-05 复审）** — 两轮独立审查：首轮 FAIL（P1-1/P1-2 及 6 项 P2/P3），修订后复审 PASS，无 P0/P1。
- 复审强制实现前置（P2-A/B/C，作为 workpack 强制验收项，非阻塞设计）：
  - **P2-A**：MySQL 顶层普通 SQL 无法直接 `IF...SIGNAL`。V2 实现二选一：(1) `DELIMITER $$` + 存储过程式复合块（需先验证 Flyway 8.5.13 对 DELIMITER 的解析，P3-5）；(2)「引用即失败」模式——表/列用 `SELECT \`col\` FROM \`tbl\` WHERE 1=0 LIMIT 0;`、索引用 `USE INDEX`，prepare 阶段即报错；CHECK 约束无法被 SQL 引用，只能走 `information_schema` 过程式或降级为 pre-baseline diff 承担。
  - **P2-B**：V2 断言清单必须从 V1 程序化生成（同一机器 manifest，与 pre-baseline diff 共用），杜绝手写与 V1 漂移；V2 只断言**最小必要不变量**（B1-B9 关键表/列/唯一索引/显式命名 CHECK），不做脆弱全量精确匹配。
  - **P2-C**：V2 失败会堵死 V3+ 且需 `flyway repair` 恢复。强制：V2 严格只读（仅 SELECT/条件失败，无 DDL/DML）；合并前必须跑绿空库 happy / 存量 happy / 残缺库 fail 三路径；文档化 FAILED 恢复流程。
- Findings（首轮，已修订闭环）:
  - **P1-1**（已修订）：D2 人工清单无法防「结构不完整存量库被静默 baseline」→ 新增 V2 自动不变量校验 + pre-baseline 目标库 diff。
  - **P1-2**（已修订）：seed 管理员 BCrypt 进版本化迁移 = 生产后门 → 管理员凭据改带外初始化，不入迁移。
  - **P2-1**（已修订）：V2 seed 幂等性未设计、门禁 2 措辞矛盾 → 若用 seed 必须幂等；门禁 2 措辞改为「V1 未重执行、V2 校验 APPLIED」。
  - **P3-1**（已修订）：dump 去掉 `--routines --triggers`，导出后校验无 DB 侧对象。
  - **P3-2**（已修订）：`validate-on-migrate` 对 baseline 路径的 V1 无校验，如实说明并补充周期空库探测。
  - **P3-3**（已修订）：定义备份类型与恢复演练。
  - **P3-4**（已修订）：`init.sql` 标注已被 V1 取代。
  - **P3-5**（已修订）：未来过程式迁移需先用目标 Flyway 版本验证 DELIMITER。
  - **P3-6**（已修订）：`migrate_password_bcrypt` 归类为一次性数据回填，不入结构清单。
