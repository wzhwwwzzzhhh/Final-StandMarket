# B10-db-migration-baseline · Workpack plan

> Status: 本地已验证（2026-09-05，独立审查 PASS）
> Requirement source: [阶段 B B10](../plans/阶段B-P0P1交易链路修复.md#b10数据库发布基线p1)
> Design: [B10 数据库发布基线 Design](../design/db/B10-database-migration-baseline-design.md)（已确认 2026-09-05，D1-D4）

## Scope

### In scope

- 引入 Flyway（`flyway-core` + `flyway-mysql`，Spring Boot 2.7.15 BOM 管理版本）到 `fashion-server`。
- `application.yml` 增加 `spring.flyway.*` 配置：`baseline-on-migrate=true`、`baseline-version=1`、`clean-disabled=true`、`validate-on-migrate=true`。
- 生成 `db/migration/V1__baseline_existing_schema.sql`：从本地活库 `mysqldump --no-data --skip-triggers` 导出，并与 `final07.sql` + `add_*` + 独立建表脚本对照核对（D2，我连本地库生成）。
- 生成 `V2__verify_baseline_invariants.sql`：严格只读不变量校验（表/列/索引/显式命名 CHECK），空库与存量库两路径都执行（D1 双模式）。
- 生成 `V3__seed_reference_data.sql`：category 参考数据幂等 seed（`INSERT IGNORE`）；管理员凭据带外初始化、不进迁移（D3）。
- 空测试库按文档初始化到当前结构；已有结构库建立 baseline 后继续后续迁移；重复启动幂等。
- `mysql/README.md` 重写为「历史脚本仅参考」+ FAILED 恢复流程；`README.md` 同步迁移工作流（D4 保留历史脚本）。

### Out of scope

- 生产部署/CD、真实退款/支付 API、业务逻辑改动。
- 演示数据（`data_enrichment.sql`）进入迁移。
- 管理员/员工登录凭据进入迁移（独立审查 P1-2 强制带外初始化）。
- `flyway clean` / undo。

## 执行中用户授权的范围补充

- **发现**：master 应用自 B6/B8 合并后未真实启动过 Spring 上下文（CI 仅跑 Mockito 单测）；启动暴露出 8 处「多构造函数无 `@Autowired`」的 Bean 装配缺陷，导致 `No default constructor found`。
- **修复（用户授权「顺手修」，约 1 行/处）**：
  - `ProductCatalogMutationCoordinator`、`SeckillReliablePublisher`、`SeckillBusinessDeadLetterService`、`SeckillCompensationExecutor`、`SeckillCompensationRecoveryTask`、`SeckillInvalidMessageService`、`SeckillMessageRecoveryTask`、`SeckillReconciliationService`
  - 均为给真实构造函数补 `@Autowired`（共 +16 行），不改变行为；`mvn test` 572 通过验证。
- **发现**：本地 `fashion_shop` 严重落后于代码结构（19 表 vs 文档化 29 表 + B1-B9 增量 + 乱码注释）。
- **修复（用户授权「脚本拼装全量 V1」+「授权清理两处脏数据」）**：应用 B1-B9 缺失结构与索引；删除 order 43829 的 4 条重复待支付流水、完成 4 条履约中测试订单。备份见 `backups/`。
- **环境阻塞（非 B10 缺陷）**：应用完整启动还需 `FASHION_AGENT_BASE_URL`、JWT 密钥环境变量（由用户正常启动提供）；本 workpack 以 Flyway 插件三路径验证为准，不伪造密钥强制启动。

## Acceptance mapping

| AC（阶段 B B10） | Planned behavior | Verification |
|---|---|---|
| 空测试库可以按文档初始化到当前结构 | 空库 `migrate` 完整执行 V1，生成全部当前表/索引 | 新建空测试库执行 migrate，核对表清单 + `flyway_schema_history` |
| 已有结构的测试库可以建立 baseline 后执行后续迁移 | 存量非空库自动 baseline（version=1，不执行 V1），V2+ 可追加 | 对本地 `fashion_shop` 执行 migrate，查 `flyway_schema_history` v1 BASELINE |
| 重复启动不会重复执行或修改已成功迁移 | Flyway schema_history + checksum 幂等 | 二次 migrate 报 0 migrations |
| 支付、退款、评价、收藏、优惠券和审计表均在迁移历史中可追踪 | V1 含 `payment/refund/review/favorite/coupon/operation_log` 等全量表 | 核对清单逐表对照，`git log` 可追踪 V1 |

## Slices

单切片：`db-migration-baseline`（配置 + baseline 生成 + V2 校验 + 文档，一个内聚可验收交付单元）。

## 强制验收项（来自设计复审 PASS 的前置条件）

1. **V2 实现选型先验证**：MySQL 顶层普通 SQL 无法直接 `IF...SIGNAL`。先写 V2 草稿在 Flyway 8.5.13 上验证方案：存储过程式 `DELIMITER` 块（P3-5 需验证），或「引用即失败」覆盖表/列/索引（`SELECT col FROM tbl WHERE 1=0`、`USE INDEX`）+ CHECK 校验走 `information_schema` 过程式或降级 pre-baseline diff。
2. **V2 清单程序化生成**：断言清单从 V1/权威清单机器生成（同一 manifest，与 pre-baseline diff 共用），杜绝手写漂移；V2 只断言最小必要不变量（B1-B9 关键表/列/唯一索引/显式命名 CHECK），不断言 CHECK 总数等脆弱项。
3. **V2 严格只读**：仅 SELECT + 条件失败，不含任何 DDL/DML，失败时无可回滚的部分改动。
4. **三路径验证**（合并前必须全绿）：空库 happy / 存量 happy / 残缺库 fail，对应 §8 门禁 1/2/5。
5. **FAILED 恢复流程文档化**：`flyway repair` 恢复步骤写入 `mysql/README.md` 或 `evidence.md`。

## File-level change surface

| 文件 | 变更 |
|---|---|
| `backend/fashion-server/pom.xml` | + Flyway 依赖（2 行） |
| `backend/fashion-server/src/main/resources/application.yml` | + `spring.flyway.*`（约 6 行） |
| `backend/fashion-server/src/main/resources/db/migration/V1__baseline_existing_schema.sql` | 新增（schema-only，从活库 dump + 对照核对） |
| `backend/fashion-server/src/main/resources/db/migration/V2__verify_baseline_invariants.sql` | 新增（严格只读不变量校验） |
| `backend/fashion-server/src/main/resources/db/migration/V3__seed_reference_data.sql` | 新增（category 幂等 seed，`INSERT IGNORE`，非凭据） |
| `mysql/README.md` | 重写：历史脚本仅参考 + FAILED 恢复流程 |
| `README.md` | 迁移工作流说明 |
| `docs/workpack/README.md` | 注册本 workpack |

## Risks and rollback

- **存量库结构不完整被误标 baseline**：V2 不变量校验在 baseline/空库路径的首次 `migrate` 时执行（Flyway 不重跑），结构缺失当场 `SIGNAL`/引用失败；执行前另做 pre-baseline 目标库 diff（V1 对象全集 × 目标库实际），双保险，均记录在 `evidence.md`。
- **V2+ 与已应用结构冲突**：`validate-on-migrate` 启动失败；只追加修正版本，不改已发布脚本。
- **V2 自身失败堵死 V3+**：严格只读、清单程序化生成防漂移、三路径验证合并前全绿；FAILED 恢复流程（`flyway repair`）文档化。
- **回滚**：只追加不撤销；以迁移前全量备份（`--single-transaction`）+ 前向修复为策略。`clean-disabled=true` 防误删。
- **演示数据/凭据泄漏**：V1 仅结构；seed 只含 category 参考数据；管理员凭据带外初始化，不入仓库。

## Verification commands

```bash
# 1) 编译与回归（无 Java 行为变更）
cd backend && mvn test

# 2) 空库初始化：用本地 MySQL 新建空测试库后启动应用或 flyway:migrate，核对表清单 + schema_history
# 3) 存量库 baseline：对本地 fashion_shop 执行 migrate，验证 v1 BASELINE 且无迁移执行；二次执行为 no-op
# 4) 全量检查
git diff --check
```
