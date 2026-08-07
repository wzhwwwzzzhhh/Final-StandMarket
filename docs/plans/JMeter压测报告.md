# JMeter 压测报告

## 测试状态

**待环境执行。** 本机已安装 Apache JMeter 5.6.3，并已完成商品列表脚本的非 GUI 解析和发压验证；由于 `localhost:8080` 未启动，10,000 个请求均为连接失败，不能作为性能基线或优化后指标。

## 测试环境

| 项目 | 当前状态 |
|---|---|
| 测试时间 | 待完整环境就绪后记录 |
| 应用 | Spring Boot 2.7.15，默认 `http://localhost:8080` |
| 数据库 | MySQL 8.0，执行 `mysql/add_indexes.sql` 后测试 |
| 中间件 | Redis 7.0、RabbitMQ 3.12 均需启动 |
| 限流 | Sentinel 本地规则，资源 `seckill`，默认 500 QPS，快速失败 |
| 压测工具 | Apache JMeter 5.6.3 |

## 前置条件

1. 对目标库执行 `mysql/add_indexes.sql`，并通过 `SHOW INDEX FROM <table>` 核验索引。
2. 启动 MySQL、Redis、RabbitMQ 和后端应用，创建库存不少于预期成功请求数的有效秒杀券。
3. 调用管理端预热接口，将券库存与活动时间写入 Redis。
4. 以独立压测账号获取用户 JWT，传给 `-Jtoken`。不能复用单一用户模拟 10,000 用户，否则 Redisson 和每人限购会使结果失真。
5. 压测执行后等待 RabbitMQ 消费完成，再核验订单数和剩余库存。

## 执行命令

```powershell
jmeter -n -t jmeter/商品列表查询压测.jmx -Jtarget_host=localhost -Jtarget_port=8080 -l jmeter/results/product.jtl -e -o jmeter/results/product-html
jmeter -n -t jmeter/秒杀抢购压测.jmx -Jtarget_host=localhost -Jtarget_port=8080 -Jcoupon_id=1 -Jtoken=<JWT> -l jmeter/results/seckill.jtl -e -o jmeter/results/seckill-html
jmeter -n -t jmeter/混合场景压测.jmx -Jtarget_host=localhost -Jtarget_port=8080 -Jcoupon_id=1 -Jtoken=<JWT> -Jduration=300 -l jmeter/results/mixed.jtl -e -o jmeter/results/mixed-html
```

## 场景结果

### 商品列表查询

配置：100 并发，10 秒爬坡，100 次循环，`GET /user/product`。

| 指标 | 优化前 | 优化后 |
|---|---:|---:|
| 平均 RT | 待环境执行 | 待环境执行 |
| P90 RT | 待环境执行 | 待环境执行 |
| P99 RT | 待环境执行 | 待环境执行 |
| QPS | 待环境执行 | 待环境执行 |
| 错误率 | 待环境执行 | 待环境执行 |

### 秒杀抢购

配置：200 并发，5 秒爬坡，50 次循环，`POST /user/seckill/coupon/{couponId}/orders`。

| 指标 | 优化前 | 优化后 |
|---|---:|---:|
| 平均 RT | 待环境执行 | 待环境执行 |
| P90 RT | 待环境执行 | 待环境执行 |
| P99 RT | 待环境执行 | 待环境执行 |
| QPS | 待环境执行 | 待环境执行 |
| 业务错误率 | 待环境执行 | 待环境执行 |
| Sentinel 限流次数 | 不适用 | 待环境执行 |
| 超卖数 | 待环境执行 | 待环境执行 |

### 混合场景

配置：200 并发，10 秒爬坡，持续 5 分钟；JMeter 吞吐控制器按每线程 80% 商品查询和 20% 秒杀请求分流。

| 指标 | 优化前 | 优化后 |
|---|---:|---:|
| 平均 RT | 待环境执行 | 待环境执行 |
| QPS | 待环境执行 | 待环境执行 |
| 错误率 | 待环境执行 | 待环境执行 |
| Sentinel 限流次数 | 不适用 | 待环境执行 |
| 超卖数 | 待环境执行 | 待环境执行 |

## 超卖核验

每次秒杀场景必须在异步消费完成后执行以下 SQL。`over_sold_count = 0` 才能通过。

```sql
SELECT
    sc.id AS coupon_id,
    sc.stock AS remaining_stock,
    COUNT(so.id) AS created_orders,
    GREATEST(COUNT(so.id) - :initial_stock, 0) AS over_sold_count
FROM seckill_coupon sc
LEFT JOIN seckill_order so ON so.coupon_id = sc.id AND so.status IN (1, 2)
WHERE sc.id = :coupon_id
GROUP BY sc.id, sc.stock;
```

## 优化项与预期

| 优化项 | 可观察结果 |
|---|---|
| `product(category_id, status, sales)`、`product(tag, status, sales)` | 商品列表筛选命中复合索引，降低全表扫描与排序成本 |
| 订单、秒杀订单及用户相关索引 | 订单号、用户列表、地址簿、购物车和登录查询使用索引访问 |
| Sentinel 500 QPS 快速失败 | 超过阈值的秒杀入口请求返回“系统繁忙”，不继续占用 Redis、锁和 MQ 资源 |
| Redis Lua + Redisson + RabbitMQ | 预扣库存原子执行、重复提交受限、数据库落单异步化；超卖数应保持 0 |

## 本次脚本验证

2026-08-07 执行：商品和秒杀脚本均完成非 GUI 运行；混合脚本以 `-Jduration=1` 完整运行，用于验证可配置调度和非 GUI 执行。所有请求目标均为未启动的 `localhost:8080`。

| 项目 | 结果 |
|---|---|
| JMX 解析 | 商品、秒杀、混合三个脚本均通过 |
| 实际请求数 | 商品 10,000；秒杀 10,000；混合场景 4,146（`-Jduration=1` 冒烟） |
| 连接状态 | 目标 `localhost:8080` 未启动，已完成场景均为 100% 连接错误 |
| 性能指标结论 | 无效，待完整环境执行 |
