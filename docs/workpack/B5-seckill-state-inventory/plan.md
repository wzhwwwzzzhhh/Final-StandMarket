# B5-seckill-state-inventory · Workpack plan

> Status: 本地已验证（2026-09-01；等待远程交付授权）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B5 / GitHub Issue #14
> Design: `docs/design/seckill/B5-seckill-state-inventory-design.md`（已确认，2026-09-01）
> Baseline: `master` @ `bfa4d77dbedfd539bf41076d19e418e92119f9f6`
> Plan review: PASS（第二轮独立复审，P0/P1/P2/P3 均为 0；用户已确认）

## Scope

### In scope

- 用专用 CAS 收敛秒杀支付、用户取消、管理取消和超时取消状态迁移。
- 取消 CAS 与 MySQL 库存回补使用同一真实事务，Redis 用 Lua 原子恢复库存与参与资格。
- 修复取消后数据库唯一约束仍阻止重新参与的问题，并提供可验证的幂等迁移。
- 用户端接入真实取消接口，管理端按钮与后端状态边界一致，禁止随机/假支付结果。
- 延迟关闭统一为 30 分钟。
- 以 TDD、真实 MySQL/Redis、双前端构建、独立 Review 和证据完成本地交付。

### Out of scope

- B6 的 MQ confirm/return、有限重试、业务死信、补偿记录和对账。
- 真实第三方秒杀支付网关与计数型 `limit_per_user`。
- 生产迁移、RabbitMQ 队列操作、部署与 B7-B11 其他工作。
- 主工作区三项 B1 未提交工作流文档及 B2-B4 已归档内容。

## Current findings

- `updatePayTime` 会把已由确认支付更新到 `2` 的订单再次递增为 `3`。
- 主动取消和超时取消没有共享受检事务；用户取消不补库存，超时取消的 MySQL/Redis 写入可半完成。
- Redis 取消只 `INCR` 库存，未从已购 ZSET 删除用户，且不是 Lua 原子操作。
- 永久 `UNIQUE(user_id,coupon_id)` 使已取消用户仍无法再次插单。
- 延迟队列仍为 15 分钟；用户端取消只显示假成功而不调用 API。
- 当前目标代码未发现随机支付实现，但缺少防回归契约。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| B5-AC1 | 确认支付用单条 `status=1 -> 2` CAS同时写支付时间；`updatePayTime` 不改状态 | Mapper SQL 合约、Service 测试、真实 MySQL 支付/取消竞态 |
| B5-AC2 | 用户、管理和超时取消仅由 `status=1` CAS赢家触发一次 MySQL 库存回补 | Service/Mapper 测试、真实 Spring/MySQL 并发与回滚 |
| B5-AC3 | Redis Lua 写前验证参数/key 类型/整数范围，再安全执行库存恢复和移除用户；缺 key、wrong-type、非整数、上溢、重复和并发不产生部分或重复回补 | Lua 合约与真实 Redis 集成测试 |
| B5-AC4 | 外层无事务、内层 `REQUIRES_NEW` 保证 Lua 前 MySQL 已提交；Redis 失败以 `code=1/REDIS_RECONCILIATION_PENDING` 对外，不诱导重复取消 | 真实 Spring 提交时点测试、故障注入、Controller/双前端契约 |
| B5-AC5 | 只有显式已取消记录释放活动唯一约束；`NULL`/未知状态迁移阻断，待支付/已支付仍保持每用户每券唯一 | 完整基线导入、迁移测试、生产 Mapper + MySQL 插入场景 |
| B5-AC6 | 用户端调用真实取消 API；管理端仅对待支付显示操作；无随机/前端假支付路径 | 前端/API 静态契约、两端生产构建 |
| B5-AC7 | 延迟关闭精确为 30 分钟，代码与文档一致；旧 Rabbit 队列切换由 B11 门禁约束 | 配置合约测试、源码/文档扫描 |

## Slices

### Slice 1 — 支付与 MySQL 取消状态机

1. RED：增加 Mapper/Service 测试，证明 `updatePayTime` 二次递增、支付/取消忽略 CAS 结果、用户取消无回补、超时路径非同一事务。
2. GREEN：新增 `markPaid`、可信取消和用户归属取消专用 CAS；移除合法入口对通用任意状态写的依赖。
3. GREEN：新增外层无事务编排与 `REQUIRES_NEW` 取消事务 Bean，将唯一成功 CAS 与 `restoreStock` 放在同一代理事务；库存零行抛异常并整体回滚。
4. GREEN：用户、管理和超时入口复用统一取消编排；保留 B4 用户归属 SQL。
5. 使用真实 Spring/MyBatis/MySQL 验证支付/取消、主动/超时竞态、回补故障回滚，以及 Lua 调用点提交可见、无外层活动事务、外层后续异常不能回滚已提交取消事实，记录 RED/GREEN。

### Slice 2 — Redis 回补、重新参与与 30 分钟契约

1. RED：增加 Lua/Redis、迁移和 UI 合约测试，证明当前不移除用户、重复回补风险、永久唯一约束、15 分钟 TTL 和用户端假成功。
2. GREEN：实现 `seckill_rollback.lua` 及执行结果映射；脚本在写前完整验证 key 类型、参数、非负整数和上溢条件，仅在 MySQL 提交后由 CAS赢家调用。
3. GREEN：增加“只有状态 3 才释放”的活动订单生成标记、`status NOT NULL/CHECK` 与唯一索引迁移，同步干净库基线和带列名 dump INSERT；对 NULL/未知状态及错误/部分定义显式失败。
4. GREEN：用户端 API/页面接入真实取消；管理端操作仅限待支付并展示准确结果；增加无随机支付契约。
5. GREEN：延迟 TTL 改为 30 分钟并补 RabbitMQ 旧队列 B11 切换说明。
6. 在真实 Redis 验证成功、重放、并发、缺 key、wrong-type、非整数、负值和上溢时两个 key 的不变性；在真实 MySQL 执行完整 clean DDL+dump、升级、重跑、重新参与和迁移门禁。

### Slice 3 — 集成验证与交付证据

1. 合并运行全部 B5 聚焦测试与显式 MySQL/Redis 集成门禁。
2. 执行后端完整 `mvn test`，分别执行管理端/用户端 `npm run build`；项目没有的 lint/typecheck/test 不得声称通过。
3. 执行 `git diff --check`、限定范围 diff、状态写/随机支付/15 分钟残留和敏感信息扫描。
4. 独立实现 Review；关闭全部 P0/P1/P2 后补齐 `review.md`、`evidence.md`。
5. 用户另行授权后才执行 commit、push、PR、CI 与 merge；B6 完成前禁止部署。

## File-level change surface

### Expected production files

- `backend/fashion-server/src/main/java/com/fashion/service/SeckillOrderService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillOrderServiceImpl.java`
- 新增秒杀取消事务 Bean/内部结果类型。
- `backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java`
- `backend/fashion-server/src/main/java/com/fashion/mapper/SeckillOrderMapper.java`
- `backend/fashion-server/src/main/resources/mapper/SeckillOrderMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/mapper/SeckillCouponMapper.java`
- `backend/fashion-server/src/main/resources/mapper/SeckillCouponMapper.xml`
- `backend/fashion-server/src/main/java/com/fashion/controller/user/UserSeckillOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/controller/admin/SeckillOrderController.java`
- `backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java`
- `backend/fashion-server/src/main/resources/lua/seckill_rollback.lua`
- `frontend/fashion-client/src/api/seckill.js`
- `frontend/fashion-client/src/views/SeckillOrder.vue`
- `frontend/fashion-admin/src/views/SeckillOrderList.vue`
- `mysql/final07.sql` 与新增 B5 幂等迁移脚本。

### Expected tests

- 秒杀状态机 Service/Mapper/Controller 单元与静态契约测试。
- 真实 Spring/MyBatis/MySQL 竞态、回滚和重新参与集成测试。
- B5 迁移脚本静态/真实 MySQL 测试。
- Lua 静态契约与真实 Redis 集成测试。
- 两端 UI/API 合约测试及生产构建。

## Branch and dirty-worktree handling

- B5 在 `D:\market-handsome\Final-StandMarket-worktrees\b5-seckill-state-inventory`、分支 `codex/b5-seckill-state-inventory` 开发，基线为 `master@bfa4d77`。
- 主工作区仍在 `codex/b1-payment-trust-boundary`，三项用户未提交工作流文档保持原状，不 reset、stash、暂存或混入 B5。
- B2-B4 worktree/分支暂不清理；B5 只暂存本 workpack、Design、必要产品代码和测试。

## Risks and rollback

- **跨存储半完成**：固定外层无事务、内层 `REQUIRES_NEW`，先独立提交 MySQL、后执行 Redis；错误返回业务成功包中的 `REDIS_RECONCILIATION_PENDING`，B6 前不得部署。
- **重复/部分回补**：MySQL CAS和 Redis ZSET 成员分别作为一次性令牌；CAS输家不调用 Lua，Lua 在写前验证所有可预见错误并安全排序写入，重放不重复加库存。
- **事务被 catch 吞掉**：取消事务 Bean 不把数据库异常转成正常返回；库存回补零行抛错，由真实代理回滚。
- **唯一约束误放宽**：只有显式 `status=3` 生成 `NULL`；状态列为 `NOT NULL + CHECK(1,2,3)`，脏状态迁移阻断，订单号唯一约束保留。
- **Rabbit 队列参数冲突**：B5 只改声明与契约，生产切换必须由 B11 停写、排空、删除和重建旧队列。
- **错误回滚版本**：不得回到 `updatePayTime` 隐式增状态或取消不补库存的制品；schema 新列/索引保留。

## Verification commands

实现阶段命令以实际新增测试类名为准，最终至少执行：

```powershell
$B5_MYSQL_CONFIG = 'D:\market-handsome\Final-StandMarket\backend\fashion-server\src\main\resources\application-dev.yml'
if (-not (Test-Path -LiteralPath $B5_MYSQL_CONFIG)) { throw 'B5 MySQL/Redis config is missing' }
git -C 'D:\market-handsome\Final-StandMarket' check-ignore --quiet -- 'backend/fashion-server/src/main/resources/application-dev.yml'
if ($LASTEXITCODE -ne 0) { throw 'B5 config is not ignored by Git' }

Set-Location backend
mvn -pl fashion-server -am -DskipTests install
mvn -pl fashion-server '-Dtest=SeckillOrderStateMachineTest,SeckillOrderMapperContractTest,SeckillRollbackLuaContractTest,SeckillUiContractTest,SeckillStateMigrationSqlTest' test
mvn -pl fashion-server '-Db5.integration=true' "-Db5.config=$B5_MYSQL_CONFIG" '-Dtest=SeckillStateSpringMysqlIntegrationTest,SeckillRollbackRedisIntegrationTest,SeckillStateMigrationMysqlIntegrationTest' test
mvn test

Set-Location ../frontend/fashion-client
npm ci
npm run build

Set-Location ../fashion-admin
npm ci
npm run build

Set-Location ../..
git diff --check
git diff --stat
git diff --name-only
rg -n 'status\s*=\s*status\s*\+|x-message-ttl.*900000|Math\.random|new Random' backend/fashion-server/src/main frontend/fashion-client/src/views/SeckillOrder.vue frontend/fashion-admin/src/views/SeckillOrderList.vue
```

显式集成测试只使用随机前缀的隔离 MySQL schema 和 B5 专用 Redis key，结束后清理；不得输出、复制或提交本地凭据。Redis 测试必须包含 wrong-type、非整数、负值和上溢且断言库存/用户 key 均不变；MySQL 测试必须实际执行更新后的 clean DDL+dump。若本地 Redis/RabbitMQ 不可用，必须如实记录阻塞，不能用 mock 冒充真实集成证据。
