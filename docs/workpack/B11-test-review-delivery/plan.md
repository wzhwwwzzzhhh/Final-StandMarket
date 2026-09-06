# B11-test-review-delivery · Workpack plan

> Status: 待确认
> Requirement source: [阶段 B B11](../plans/阶段B-P0P1交易链路修复.md#b11测试审查和交付p0-门禁)
> Design: 无（沿用阶段 B 已确认边界；无新高风险决策）

## Scope

### In scope

B11 是阶段 B 的 P0 交付门禁，核心是**验证与收口**（非新功能）：

1. **服务可运行验证**：本地后端完整启动（Flyway baseline 后）、关键流程冒烟。
2. **Java 测试覆盖核对与补齐**：对照 B11「至少覆盖」的 10 类场景核对现有 572 测试，识别真实缺口（同名建议文件缺失 ≠ 场景缺失，需按场景核对）；对确认缺口按 TDD 补齐。
3. **Python 全量测试**：创建隔离 venv 安装依赖，`python -m pytest -q` 全量收集并跑绿（现有 19 个测试文件；依赖阻塞如实记录）。
4. **前端生产构建**：`npm run build` 两端。
5. **最终命令**：`mvn test` + 两端 build + pytest 全绿。
6. **阶段级收口**：阶段 B 结论与遗留问题汇总写回 `docs/plans/阶段B-P0P1交易链路修复.md` §九；`项目进度跟踪.md` 同步。

### Out of scope

- 新业务功能（B10 之外范围变化需先改需求源）。
- 生产部署/CD（阶段 B 门禁之外）。
- B0-AC6 外部密钥轮换（外部平台阻塞，登记在 B0 workpack）。

## Acceptance mapping

| AC（阶段 B B11） | Planned behavior | Verification |
|---|---|---|
| 后端测试通过 | `mvn test` 全绿 | 记录退出码与测试数 |
| Python 全量测试通过 | 隔离 venv + `pytest -q` 全量收集通过 | 记录收集数与退出码；依赖阻塞如实记录 |
| 用户端生产构建通过 | `npm run build` | 记录退出码与产物 |
| 管理端生产构建通过 | `npm run build` | 记录退出码与产物 |
| 关键流程可运行 | 应用完整启动，注册/登录/商品/支付回跳/订单/退款/秒杀/评价主流程可用 | 服务启动日志 + 冒烟请求 |
| 阶段级审查摘要写入阶段 B 文档 | §九 实现后审查补全 | 文档 diff |
| 项目进度跟踪同步 | `项目进度跟踪.md` 更新 B10/B11 状态 | 文档 diff |

## Slices

单切片：`test-review-delivery`（验证 + 补齐 + 收口，一个内聚交付单元）。

## File-level change surface

| 文件 | 变更 |
|---|---|
| `backend/fashion-server/src/test/java/com/fashion/...` | 按场景核对后补齐缺口测试（TDD） |
| `docs/plans/阶段B-P0P1交易链路修复.md` | §九 实现后审查 + §八 检查表勾选 |
| `docs/plans/项目进度跟踪.md` | 阶段状态同步 |
| `docs/workpack/README.md` | 注册本 workpack |
| 验证产物 | evidence 记录命令与结果 |

## Risks and rollback

- **应用完整启动依赖环境变量**：`FASHION_JWT_ADMIN_SECRET_KEY`、`FASHION_JWT_USER_SECRET_KEY`、`FASHION_AGENT_BASE_URL`（用户提供，不伪造）。缺失时应用启动项记录 `tooling_blocked`，其余验证不受影响。
- **Python 依赖安装阻塞**：如 venv 安装 Redis/ES 依赖失败，如实记录，不冒充通过。
- 无代码行为变更风险（纯验证 + 测试补齐）。

## Verification commands

```bash
cd backend && mvn test
cd frontend/fashion-client && npm run build
cd frontend/fashion-admin && npm run build
cd agent-service && python -m venv .venv && .venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python -m pytest -q
```
