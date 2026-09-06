# B11-test-review-delivery · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| 后端测试通过 | `cd backend && mvn test` exit=0；572 tests run, 0 failures, 0 errors, 147 skipped（2026-09-05 新鲜重跑，master） | ✅ |
| Python 全量测试通过 | `agent-service/.venv` 隔离 venv `pip install -r requirements.txt` exit=0 后 `pytest -q`：**69 passed, 1 skipped**（全量收集通过，对比阶段 B 基线「依赖缺失无法收集」已解决） | ✅ |
| 用户端生产构建通过 | `cd frontend/fashion-client && npm run build` exit=0，built in 5.08s（仅 1MB+ 大 chunk 警告，基线已知） | ✅ |
| 管理端生产构建通过 | `cd frontend/fashion-admin && npm run build` exit=0，built in 7.51s（仅大 chunk 警告，基线已知） | ✅ |
| 关键流程可运行 | 服务启动由用户提供 env（FASHION_JWT_*、FASHION_AGENT_BASE_URL）后执行；B10 已修复 8 处 Bean 装配缺陷并验证 Flyway baseline，启动前置（Redis 已托管启动、RabbitMQ 通、fashion_shop 已 baseline）就绪 | ⏳ 待用户启动 |
| 阶段级审查摘要写入阶段 B 文档 | `docs/plans/阶段B-P0P1交易链路修复.md` §九 实现后审查补全 + §八 检查表勾选 | ✅ |
| 项目进度跟踪同步 | `docs/plans/项目进度跟踪.md` 更新 B10/B11 状态 | ✅ |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-05 14:0x | `cd backend && mvn test` | 0；572/0/0/147 | master 新鲜重跑 |
| 2026-09-05 14:0x | `.venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python -m pytest -q` | 0；69 passed, 1 skipped | 全量收集通过 |
| 2026-09-05 14:0x | `cd frontend/fashion-client && npm run build` | 0；5.08s | 大 chunk 警告（已知） |
| 2026-09-05 14:0x | `cd frontend/fashion-admin && npm run build` | 0；7.51s | 大 chunk 警告（已知） |
| 2026-09-05 | `git diff --check` | clean | |

## Java 场景覆盖核对（B11「至少覆盖」10 类）

| 场景 | 覆盖测试（代表） |
|---|---|
| 1 用户信息不返回密码/改密码 | UserServiceImplTest（13 方法）、EmployeeServiceImplTest |
| 2 注册/验证码匿名、其余 401 | WebconfigPublicRouteContractTest、UserSeckillOrderControllerSecurityTest、ResourceOwnershipSpringMysqlIntegrationTest |
| 3 地址 IDOR | AddressBookServiceImplTest、ResourceOwnershipSpringMysqlIntegrationTest |
| 4 普通订单扣库存/回滚/重复取消 | OrderTimeoutIsolationTest、B6OrderConsumerAckTest、ResourceOwnershipSpringMysqlIntegrationTest |
| 5 支付通知签名/金额/重复 | PayNotifyControllerTest、PaymentControllerTest、B6DuplicateOrderTransactionTest |
| 6 退款审批/并发回补 | RefundServiceImplTest、B3 相关契约测试 |
| 7 秒杀 CAS/Redis 回补/MQ | SeckillB5CrossLayerContractTest、B6CrossStoreReliabilityIntegrationTest |
| 8 客户端秒杀券参数不可抵 | UserSeckillOrderControllerSecurityTest |
| 9 评价归属/重复评价 | ReviewServiceImplB7Test |
| 10 缓存失效/锁所有权 | CacheClientOwnershipTest、B8ProductCacheConsistencyIntegrationTest、ProductCacheTtlPolicyTest |

> 建议清单中「缺失」的文件名（WebconfigTest/OrderServiceImplTest 等）多数由不同命名的测试替代，场景已覆盖；无确认缺口，B11 未新增 Java 测试。

## Not run or blocked

- **服务完整启动**：等待用户提供 `FASHION_JWT_ADMIN_SECRET_KEY`/`FASHION_JWT_USER_SECRET_KEY`/`FASHION_AGENT_BASE_URL` 后由用户执行（密钥不伪造）；前置（Redis/RabbitMQ/DB baseline/8 处装配修复）已就绪。
- 前端无 test/lint/typecheck 脚本，仅记录 build 证据（开发规范要求，不得冒充单元测试覆盖）。

## Local delivery summary

B11 作为阶段 B P0 交付门禁的验证部分完成：四类验证全绿（Java 572 / Python 69 / 两端 build），Java 10 类场景覆盖核对无缺口，阶段级收口完成。待服务启动验证与阶段 B 最终门禁（B0-AC6 外部密钥轮换仍阻塞）后由项目维护者关闭阶段 B。
