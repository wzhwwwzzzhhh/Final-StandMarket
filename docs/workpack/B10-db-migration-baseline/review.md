# B10-db-migration-baseline · Independent review

> Verdict: **PASS**（2026-09-05，独立只读上下文；无 P0/P1）

## Scope and drift

审查覆盖 plan.md、evidence.md、Design、V1/V2/V3 迁移、pom/yml 与 8 处 Java 修复。独立重跑了三路径 Flyway 验证、`mvn test`（572/0/0/147）与 `git diff --check`，均与 evidence 一致。范围补充（8 处构造函数修复 + dev 库脏数据对账）有用户授权记录，未发现未记录漂移。

## Findings

| 级别 | 位置 | 问题 | 处置 |
|---|---|---|---|
| P2-1 | design §5/D4、resources/init.sql | Design 确认项「init.sql 标注已被 V1 取代或删除」被静默遗漏；init.sql 的 `shopping_cart` 结构与 V1 不兼容，是 `spring.sql.init` 双写地雷 | 已补：init.sql 加头注释标注已被 V1 取代；plan.md 记录处置 |
| P3-1 | design §6、plan Risks、mysql/README | 「V2 每次 migrate 都运行」表述不准确——Flyway 只执行一次 V2（首次 migrate / baseline 时），二次运行是 no-op；实际防线在首次 migrate 生效 | 已补：改为「V2 在 baseline/空库路径的首次 migrate 时执行」 |
| P3-2 | V1 | V1 从 dev 库 dump 携带 `AUTO_INCREMENT` 计数器（orders=43829 等），全新库不从 1 开始 | 已补：evidence + mysql/README 记录该特性，作为基线来源的预期结果 |
| P3-3 | plan/evidence | 8 处修复 + 数据对账的「用户授权」为自述，无可审计授权件 | 已补：记录决策时间与选项；plan 状态更新为本地已验证 |
| 附注 | application-dev.yml | gitignored 配置含活体 OSS/微信/支付宝密钥与 DB 密码——B0 凭据域，非 B10 范围 | 不处理（B0-AC6 域），仅提示 |

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| 空测试库初始化到当前结构 | `fashion_shop_b10_fresh`：V1/V2/V3 全 APPLIED（success=1）、无 BASELINE 行、30 表、category=10 | PASS |
| 已有结构库 baseline + 后续迁移 | `fashion_shop_b10_empty` 与真实 `fashion_shop`：v1 BASELINE + V2/V3 APPLIED | PASS |
| 重复启动幂等 | 二次 migrate：`Schema is up to date. No migration necessary.` | PASS |
| 关键表迁移历史可追踪 | V1 含 AC4 全部表；V1↔V2 清单 29/29 表、0 列漂移、20 唯一索引全断言 | PASS |

## Residual risks

- `application-dev.yml` 中活体凭据（B0-AC6 域，B10 不处理）。
- 生产 baseline 前仍需 pre-baseline 目标库 diff（D2 设计，workpack 已在本地 fashion_shop 执行同类核对）。
- V1 携带 dev AUTO_INCREMENT 计数器（P3-2，已记录）。
