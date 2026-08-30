# Design 索引

> 本目录只记录高风险、需要明确技术决策的设计，不为普通修复增加文档门槛。

## 需要 Design 的变化

- 支付/退款外部契约与可信状态；
- 订单、退款、库存、秒杀状态机；
- Redis/MySQL 一致性与 RabbitMQ 补偿；
- 数据库迁移；
- 鉴权、凭据、资源归属；
- 公开 API 与跨服务契约。

阶段 B 已经明确且没有新歧义的技术边界可以直接引用，不重复设计。

## 状态

`草稿 → 审查 PASS → 用户确认 → 已确认`

P0/P1 审查发现会阻止确认。独立审查不可用时标记 `tooling_blocked`。

## 当前 Design

| 阶段 | 领域 | Design | 状态 | Review |
|---|---|---|---|---|
| B1 | 支付 | [支付可信边界与并发幂等](payment/B1-payment-trust-boundary-design.md) | 已确认 | PASS |
| B2 | 订单/库存 | [普通订单库存与状态闭环](order/B2-order-inventory-state-design.md) | 已确认（2026-08-30） | PASS |
