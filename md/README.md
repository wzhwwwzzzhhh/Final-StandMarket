# 时尚服装店铺项目

## 项目介绍

时尚服装店铺项目是基于苍穹外卖架构改造的服装电商系统，包含用户端和管理端两个前端应用，以及一个基于Spring Boot的后端服务。项目实现了商品管理、订单管理、用户管理、秒杀活动等功能，使用Redis实现了秒杀功能的高并发处理。

## 技术栈

### 后端技术
- Spring Boot 2.7.15
- MyBatis Plus 3.5.3.1
- Redis 3.0.1
- MySQL 8.0.31
- JWT 0.9.1
- AliOSS 3.15.1

### 前端技术
- Vue 3.5.13
- Element Plus 2.8.6
- Vue Router 4.4.5
- Axios 1.7.9

## 项目结构

```
Final-StandMarket/
├── backend/               # 后端代码
│   ├── fashion-common/    # 公共模块
│   ├── fashion-pojo/      # 实体类模块
│   ├── fashion-server/    # 后端服务
│   └── pom.xml            # 后端父pom
├── frontend/              # 前端代码
│   ├── fashion-client/    # 用户端前端
│   └── fashion-admin/     # 管理端前端
├── fashion_shop.sql       # 数据库脚本
└── README.md              # 项目说明
```

## 数据库设计

项目使用MySQL数据库，数据库名为`fashion_shop`。主要表结构包括：

- `address_book` - 地址簿
- `category` - 商品分类
- `product` - 商品信息
- `product_sku` - 商品SKU
- `combination` - 组合商品
- `combination_product` - 组合商品关系
- `employee` - 员工信息
- `order_detail` - 订单明细
- `orders` - 订单信息
- `shopping_cart` - 购物车
- `user` - 用户信息
- `seckill_activity` - 秒杀活动
- `seckill_coupon` - 秒杀券
- `special_offer` - 特价商品
- `seckill_order` - 秒杀订单

## 核心功能

### 后端功能
- 用户认证与授权
- 商品管理（CRUD）
- 订单管理
- 购物车管理
- 地址管理
- 秒杀活动管理
- 秒杀券管理
- 特价商品管理
- Redis秒杀实现

### 用户端前端功能
- 首页展示
- 商品列表与详情
- 购物车管理
- 订单管理
- 秒杀活动参与
- 用户登录注册

### 管理端前端功能
- 商品管理
- 订单管理
- 用户管理
- 秒杀活动管理
- 数据统计

## 安装与部署

### 1. 环境准备
- JDK 8+
- MySQL 8.0+
- Redis 3.0+
- Node.js 16+

### 2. 数据库配置
1. 创建数据库：`CREATE DATABASE fashion_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
2. 执行数据库脚本：`fashion_shop.sql`

### 3. 后端服务配置
1. 修改 `fashion-server/src/main/resources/application-dev.yml` 中的数据库和Redis配置
2. 启动后端服务：`mvn spring-boot:run`

### 4. 前端项目配置
1. 安装依赖：
   ```bash
   # 用户端
   cd frontend/fashion-client
   npm install
   
   # 管理端
   cd frontend/fashion-admin
   npm install
   ```

2. 启动前端服务：
   ```bash
   # 用户端
   cd frontend/fashion-client
   npm run dev
   
   # 管理端
   cd frontend/fashion-admin
   npm run dev
   ```

## 访问地址

- 后端服务：`http://localhost:8080`
- 用户端前端：`http://localhost:3000`
- 管理端前端：`http://localhost:3001`

## 秒杀功能实现

### 核心原理
1. **Redis预占库存**：将秒杀商品的库存预加载到Redis中
2. **Lua脚本保证原子性**：使用Lua脚本执行库存扣减操作，保证并发安全
3. **分布式锁**：防止超卖和重复购买
4. **异步处理**：秒杀请求异步处理，提高系统吞吐量

### 秒杀流程
1. 用户点击秒杀按钮
2. 前端发送秒杀请求到后端
3. 后端检查活动状态和库存
4. Redis扣减库存
5. 创建秒杀订单
6. 返回秒杀结果

## 项目特点

1. **高并发处理**：使用Redis实现秒杀功能的高并发处理
2. **前后端分离**：采用Vue3和Element Plus实现现代化的前端界面
3. **模块化设计**：后端采用模块化设计，易于扩展和维护
4. **安全性**：使用JWT实现身份认证，防止未授权访问
5. **可扩展性**：支持水平扩展，应对高并发场景

## 后续优化方向

1. **引入缓存**：使用Redis缓存热点数据，提高系统性能
2. **消息队列**：使用消息队列处理异步任务，如订单处理、库存更新
3. **分布式事务**：使用分布式事务保证数据一致性
4. **微服务架构**：将系统拆分为多个微服务，提高系统的可扩展性和可靠性
5. **容器化部署**：使用Docker和Kubernetes实现容器化部署，简化部署和管理

## 许可证

本项目采用MIT许可证。
