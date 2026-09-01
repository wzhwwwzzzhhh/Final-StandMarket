# B5-seckill-state-inventory · Independent review

> Verdict: PASS（实现阶段第二轮；P0/P1/P2/P3 均为 0；2026-09-01）

## Review scope

- Stage B B5 / Issue #14 / B5 Design / confirmed workpack / product code / tests / migration.

## Planning review findings

- 首轮：P0=0、P1=4、P2=1、P3=0。
- P1 Redis 脚本错误前写入：已改为写前验证类型/整数/溢出与安全写入顺序，并要求真实 Redis 不变性测试。
- P1 事务提交时点：已固定外层无事务、内层 `REQUIRES_NEW`，并要求真实 Spring 提交可见性/外层异常测试。
- P1 NULL/未知状态释放唯一性：已改为仅显式 `status=3` 生成 NULL，增加 `NOT NULL/CHECK` 与脏状态阻断。
- P1 对外待对账语义：已固定 `SeckillCancelResponse`、`Result.code/outcome`、精确文案及双前端映射。
- P2 clean dump：已要求带列名 INSERT、实际执行更新后的 DDL+dump 并比较 clean/upgrade schema。
- 第二轮确认首轮五项均完整关闭，未发现新增 P0-P3 或 B5/B6 范围漂移。

## Acceptance evidence review

- 实现首轮复审：`NEEDS_REVISION`，P0=0、P1=1、P2=2、P3=0。
- P1 迁移形状校验：补充 `CHECK ENFORCED=YES` 以及 `active_marker` 类型、可空性、`STORED GENERATED` 和非 NULL/精确表达式验证；真实 MySQL 覆盖普通列、VIRTUAL 与 `NOT ENFORCED`。
- P2 Redis 调用时点：真实 Spring 服务代理在 Redis 回调当下断言无线程活动事务，并以独立 JDBC 连接确认订单取消与库存回补已经提交可见。
- P2 失效单行 Mapper：移除 `selectByUserIdAndCouponId` 接口和 XML；允许多条取消历史后不再存在 `TooManyResultsException` 风险。
- 实现第二轮复审：`PASS`，P0/P1/P2/P3 均为 0；复审只读，未修改文件。
- 复审同时确认支付/取消 CAS、用户归属条件、MySQL 一次回补、Lua 写前验证与幂等、pending DTO、双前端 outcome 映射、30 分钟 TTL 和 B5/B6/B11 边界保持一致。

## Residual risks

- B6 is required to converge MySQL-committed/Redis-failed compensation windows.
- B10/B11 are required for production schema, RabbitMQ queue and deployment execution.
- B0-AC6 remains an external production release blocker.
