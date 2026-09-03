# B6-rabbitmq-reliability · Independent review

> Verdict: PASS for implementation review；P0=0、P1=0、P2=0、P3=1
> Implementation status: 真实 MySQL 8、Redis 7.0.15、RabbitMQ 3.12.14、三存储联合与 broker restart 均已验证；最终回归通过，可标记“本地已验证”
> Reviewer: independent read-only reviewer；三轮均为 0 file modifications

## Review scope

- Stage B B6、Issue #16、已确认 B6 Design、已确认后的 workpack、产品代码、测试、迁移和隔离测试基础设施。

## Scope and drift

- 所有产品代码、三张可靠性表及 Mapper、Redis token/registry Lua、可靠 publisher/callback、有限消费重试与业务 DLQ、timeout fallback、统一取消补偿、对账 scanner、迁移和真实依赖 fixture 均映射到已确认 B6 Design/plan。
- 未发现可以删除且不降低验收证据或可靠性契约的独立模块。
- 疑似范围外改动：0。未引入新接口、前端/AI、支付退款、促销或其他 B6 外业务能力；`SeckillCouponServiceImpl` 的较大删除属于替换 B5 旧 MQ 旁路。

## Review rounds

### Round 1 — FAIL

- P0=0、P1=0、P2=3、P3=2。
- 三个 P2：Redis fixture 接受任意 7.x；仅凭 DB15 即允许写入；MySQL/Rabbit 联合 fixture 使用异步 purge。

### Corrections and round 2

- Redis fixture 在任何写入前强制 loopback、database=15、`exclusive=true`、Redis 7.0.x 与 `DBSIZE=0`；安全门单测覆盖 7.2/6.2、非 exclusive、非空/未知 DB 拒绝。
- 联合 fixture 的 5 个 queue purge 全部改为同步 `purgeQueue(..., false)`；真实 MySQL/RabbitMQ 5/5 重跑通过。
- Round 2：PASS；P0=0、P1=0、P2=0、P3=1；未发现修正引入新的 P0/P1/P2。

### Real-environment corrections and round 3

- 真实 Redis 首轮发现通用 `connection.execute("SSCAN", ...)` 被 Lettuce 作为标量 `ByteArrayOutput` 解码；以失败集成测试和聚焦单测固定缺口后，改为暴露并解包 `DecoratedRedisConnection`，使用 `NestedMultiOutput` 保留游标及嵌套成员。
- 真实并发对账首轮发现 claim 败者把另一实例的有效补偿租约误报为基础设施故障；增加失败测试后，以二次读取仅识别 `SUCCEEDED` 或未过期 `IN_PROGRESS`，其他真实故障仍使本轮失败。
- Round 3：PASS；P0=0、P1=0、P2=0、P3=1；reviewer 复核连接抽象、租约竞态、状态误判、异常处理和幂等边界，未发现新的 P0/P1/P2。

## Remaining finding

- P3：隔离 `b6-integration.yml` 的 MySQL healthcheck 与 Redis command/healthcheck 仍可能令本地测试密码出现在 container command/inspect 元数据。密码来自忽略文件且不得复用生产值；后续具备 Docker 环境时优先改用 file/secret 注入。

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| B6-AC1–B6-AC9 | 单元/合约与全部 34 个真实 MySQL/Redis/RabbitMQ 集成用例通过；覆盖 MQ 不可达回滚、重复消息、有限重试、业务死信、事务时序、对账、并发和重启幂等 | PASS |
| B6-AC10 | 真实 MySQL 迁移 8/8；包含首次、重跑、合法中断前滚、错误/部分定义和脏数据门禁 | PASS |
| B6-AC11 | 随机 vhost、最小权限、持久 failure queue、三种 failure routing 及 RabbitMQ 3.12.14 容器重启后持久消息保留均已验证；B11 zero-inflight 仍是独立发布门禁 | PASS（B11 仍阻止发布） |
| B6-AC12 | B6 聚焦 161、完整后端 395、真实集成 34、独立 Review 与 Git 检查均通过 | PASS |

## Residual risks

- 产品 SCAN reader 与项目既定 Lettuce 驱动绑定；若未来切换 Redis driver，必须增加对应适配和真实集成证据。
- 本地隔离 Compose 凭据会出现在容器参数/inspect；不得复用生产凭据，后续验证环境应改用 file/secret 注入。
- B0-AC6、B10、B11 以及 B6 的远程交付状态继续阻止生产发布；“本地已验证”不等于可部署。
