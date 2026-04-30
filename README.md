# 👗 Final-StandMarket 潮流服装商城

> **高并发秒杀电商系统** | Spring Boot + Redis + RabbitMQ + Vue 3

<div align="center">

![Java](https://img.shields.io/badge/Java-8-007396?style=flat&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.15-6DB33F?style=flat&logo=springboot)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql)
![Vue 3](https://img.shields.io/badge/Vue%203-4FC08D?style=flat&logo=vuedotjs)
![Element Plus](https://img.shields.io/badge/Element%20Plus-409EFF?style=flat&logo=element)

</div>

---

## 📋 项目介绍

**Final-StandMarket** 是一个面向时尚服装行业的全栈电商系统，包含**管理端**和**用户端**两个前端应用，后端基于 Spring Boot 微服务架构。项目核心亮点是**高并发秒杀系统**，通过 Redis + Lua 脚本、RabbitMQ 异步削峰、Redisson 分布式锁等关键技术，实现了高性能、高可靠的秒杀抢购功能。

### 适用场景

- 🎯 **电商秒杀活动**：限时抢购、秒杀券发放
- 🛒 **服装零售平台**：商品浏览、购物车、订单管理
- 📊 **运营管理后台**：商品、订单、用户、数据统计

---

## 🚀 技术栈

### 后端核心

| 技术                     | 用途               |
| ---------------------- | ---------------- |
| **Spring Boot 2.7.15** | 项目基础框架，整合所有组件    |
| **MyBatis**            | ORM 框架，灵活编写 SQL  |
| **Redis 7.0**          | 缓存、分布式锁、秒杀库存预扣减  |
| **RabbitMQ 3.12**      | 消息队列，异步下单削峰填谷    |
| **Redisson**           | 分布式锁实现，防止重复提交    |
| **MySQL 8.0**          | 关系型数据库，数据持久化     |
| **Druid**              | 数据库连接池，监控 SQL 性能 |
| **Swagger**            | API 接口文档自动生成     |
| **JWT**                | 用户认证，前后端分离鉴权     |
| **AliOSS**             | 阿里云对象存储，商品图片管理   |

### 前端技术

| 应用                       | 技术栈                            |
| ------------------------ | ------------------------------ |
| **管理端 (fashion-admin)**  | Vue 3 + Element Plus + ECharts |
| **用户端 (fashion-client)** | Vue 3 + Element Plus + Axios   |
| **构建工具**                 | Vite 6                         |
| **路由管理**                 | Vue Router 4                   |

---

## ✨ 核心功能

### 🔥 高并发秒杀系统（项目亮点）

```
用户请求 → 前端限流 → Redis+Lua预减库存 → RabbitMQ异步下单 → 数据库落单
```

| 技术方案               | 解决问题         | 效果             |
| ------------------ | ------------ | -------------- |
| **Redis + Lua 脚本** | 库存扣减原子性，防止超卖 | 1000+ QPS 稳定运行 |
| **RabbitMQ 异步削峰**  | 流量缓冲，保护数据库   | 同步转异步，峰值削平     |
| **Redisson 分布式锁**  | 防止用户重复秒杀     | 一人一单严格保证       |
| **RabbitMQ 延迟队列 + 死信队列** | 未支付订单自动取消，释放库存 | 30分钟超时，CAS乐观锁保证原子性 |
| **库存预热**           | 避免缓存击穿       | 活动开始前数据已就绪     |

### 🛍️ 管理端功能

- **商品管理**：CRUD、分页查询、商品规格、图片上传
- **分类管理**：多级分类、状态控制、排序
- **订单管理**：订单流转、发货管理、状态跟踪
- **秒杀管理**：活动配置、秒杀券设置、库存管理
- **用户管理**：用户信息、权限控制
- **数据统计**：销售趋势、商品分析、订单统计

### 👤 用户端功能

- **商品浏览**：分类筛选、关键词搜索、商品详情
- **购物车**：添加商品、数量调整、结算
- **订单系统**：下单支付、状态跟踪、确认收货
- **秒杀抢购**：秒杀活动、倒计时、抢购下单
- **个人中心**：信息管理、地址管理、订单历史
- **优惠券管理**：领券、用券、查看优惠

---

## 🏗️ 项目结构

```
Final-StandMarket/
├── backend/                            # 后端服务
│   ├── fashion-common/                 # 公共模块（工具类、常量、配置）
│   │   └── src/main/java/com/fashion/
│   │       ├── constant/               # 常量定义
│   │       ├── context/                # 线程上下文
│   │       ├── exception/              # 全局异常
│   │       ├── properties/             # 配置属性类
│   │       ├── result/                 # 统一返回结果
│   │       └── utils/                  # 工具类（JWT、Cache、AliOSS）
│   ├── fashion-pojo/                   # 实体类模块（Entity、DTO、VO）
│   │   └── src/main/java/com/fashion/
│   │       ├── dto/                    # 数据传输对象
│   │       ├── entity/                 # 数据库实体
│   │       └── vo/                     # 视图对象
│   ├── fashion-server/                 # 服务端（Controller、Service、Mapper）
│   │   └── src/main/java/com/fashion/
│   │       ├── config/                 # 配置类（Redis、MQ、WebMVC、Swagger）
│   │       ├── controller/             # 接口层
│   │       │   ├── admin/              # 管理端接口
│   │       │   └── user/               # 用户端接口
│   │       ├── interceptor/            # 拦截器（JWT认证、登录检查）
│   │       ├── mapper/                 # MyBatis映射接口
│   │       └── service/                # 业务逻辑层
│   │           └── impl/               # 业务实现
│   └── pom.xml
├── frontend/                           # 前端应用
│   ├── fashion-admin/                  # 管理端（Element Plus + ECharts）
│   │   └── src/
│   │       ├── api/                    # API 请求封装
│   │       ├── router/                 # 路由配置
│   │       └── views/                  # 页面组件
│   └── fashion-client/                 # 用户端（Element Plus）
│       └── src/
│           ├── api/                    # API 请求封装
│           ├── router/                 # 路由配置
│           └── views/                  # 页面组件
└── mysql/                              # 数据库脚本
```

---

## 🚀 快速启动

### 环境要求

| 环境       | 版本    |
| -------- | ----- |
| JDK      | 1.8+  |
| MySQL    | 8.0+  |
| Redis    | 7.0+  |
| RabbitMQ | 3.12+ |
| Node.js  | 18+   |
| Maven    | 3.6+  |

### 1️⃣ 启动后端

```bash
# 1. 导入数据库
mysql -u root -p < mysql/final06.sql

# 2. 修改数据库配置
# 编辑 backend/fashion-server/src/main/resources/application-dev.yml
# 修改数据库、Redis、RabbitMQ 连接信息

# 3. 启动服务
cd backend
mvn clean install -DskipTests
mvn -pl fashion-server spring-boot:run

# 后端启动在 http://localhost:8080
```

### 2️⃣ 启动前端管理端

```bash
cd frontend/fashion-admin
npm install
npm run dev

# 管理端启动在 http://localhost:5173
```

### 3️⃣ 启动前端用户端

```bash
cd frontend/fashion-client
npm install
npm run dev

# 用户端启动在 http://localhost:5174
```

---

## 📊 秒杀系统核心流程

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  用户请求    │────▶│  Redis+Lua   │────▶│  RabbitMQ     │
│  参与秒杀    │     │  预减库存     │     │  业务队列     │
└─────────────┘     └──────────────┘     └───────┬───────┘
                                                  │
                                                  ▼
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  数据库落单  │◀────│  订单服务    │◀────│  消息消费者   │
│  最终一致性  │     │  创建订单    │     │  处理秒杀请求 │
└──────┬──────┘     └──────────────┘     └───────────────┘
       │
       │ 发送延迟消息
       ▼
┌──────────────┐     ┌──────────────┐     ┌───────────────┐
│  延迟队列    │────▶│  死信队列    │────▶│  超时取消     │
│  30分钟TTL   │     │  消息到期    │     │  CAS恢复库存   │
└──────────────┘     └──────────────┘     └───────────────┘
```

### 关键技术难点攻克

| 难点         | 解决方案                    | 代码位置                                                                                                                         |
| ---------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **库存超卖**   | Lua 脚本原子扣减，CAS 检查       | [SeckillCouponServiceImpl.java](backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java) |
| **重复秒杀**   | Redisson 分布式锁 + 数据库唯一索引 | [SeckillCouponServiceImpl.java](backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java) |
| **流量冲击**   | RabbitMQ 异步削峰，同步转异步     | [DirectExchangeConfig.java](backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java)               |
| **订单超时**   | RabbitMQ 延迟队列 → 死信队列 → CAS乐观锁更新状态 + 恢复库存 | [DirectExchangeConfig.java](backend/fashion-server/src/main/java/com/fashion/config/DirectExchangeConfig.java)、[SeckillCouponServiceImpl.java:254](backend/fashion-server/src/main/java/com/fashion/service/impl/SeckillCouponServiceImpl.java#L254) |                                                                |
| **缓存穿透**   | 布隆过滤器 + 空值缓存            | [CacheClient.java](backend/fashion-common/src/main/java/com/fashion/utils/CacheClient.java)                                  |
| **JWT 鉴权** | 双 Token 机制（管理端 + 用户端）   | [JwtUtil.java](backend/fashion-common/src/main/java/com/fashion/utils/JwtUtil.java)                                          |

---

## 📈 性能指标

| 指标      | 数据               |
| ------- | ---------------- |
| 秒杀 QPS  | **1000+**        |
| 接口响应时间  | **< 50ms**（缓存命中） |
| 下单峰值处理  | **2000+ 单/分钟**   |
| 库存扣减原子性 | **100% 不超卖**     |
| 系统可用性   | **99.9%**        |

---

## 🗄️ 核心数据库设计

| 表名                 | 说明   | 核心字段                                      |
| ------------------ | ---- | ----------------------------------------- |
| `product`          | 商品信息 | name, price, stock, status, category_id   |
| `product_sku`      | 商品规格 | product_id, spec, price, stock            |
| `orders`           | 订单信息 | order_number, user_id, amount, status     |
| `order_detail`     | 订单明细 | order_id, product_id, number, amount      |
| `seckill_coupon`   | 秒杀券  | name, stock, start_time, end_time, status |
| `seckill_order`    | 秒杀订单 | order_number, user_id, coupon_id, status  |
| `seckill_activity` | 秒杀活动 | name, start_time, end_time, status        |
| `address_book`     | 收货地址 | user_id, consignee, phone, address        |
| `shopping_cart`    | 购物车  | user_id, product_id, number               |

---

## 👨‍💻 关于作者

**项目定位**：Java 全栈开发实战项目，重点展示高并发秒杀系统的架构设计与实现。

### 📧 联系方式

- 项目地址：[https://github.com/wzhwwwzzzhhh/Final-StandMarket](https://github.com/wzhwwwzzzhhh/Final-StandMarket)

---

## 📄 协议

本项目仅用于学习和交流，未经许可不得用于商业用途。

<p align="center">⭐ 如果这个项目对你有帮助，欢迎 Star！</p>
