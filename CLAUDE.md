# Final-StandMarket 项目指南

## 项目概述

高并发秒杀电商系统「末路衣橱」，面向时尚服装行业的全栈电商平台，包含管理端和用户端两个前端应用，后端基于 Spring Boot 微服务架构。

## 技术栈

### 后端

- **Java 8** + **Maven** 多模块项目
- **Spring Boot 2.7.15** — 框架基础
- **MyBatis** — ORM（XML 映射，`mapper-locations: classpath:mapper/*.xml`）
- **Redis 7.0** — 缓存、分布式锁、秒杀库存预扣减
- **RabbitMQ 3.12** — 异步下单削峰填谷、延迟队列（死信队列）处理超时订单
- **Redisson 3.13.6** — 分布式锁，防止重复秒杀
- **MySQL 8.0** + **Druid** 连接池
- **Swagger 3.0** — API 文档
- **JWT** — 前后端分离鉴权（admin 和 user 两套密钥）
- **AliOSS** — 阿里云对象存储（商品图片）
- **PageHelper** — 分页
- **WebSocket** — 实时推送

### 前端

- **管理端 (fashion-admin)**: Vue 3 + Vite 6 + Element Plus + ECharts + Axios
- **用户端 (fashion-client)**: Vue 3 + Vite 6 + Element Plus + Axios + Vue Router 4

### 智能助手

- **agent-service**: Python LangGraph 智能客服，基于 FastAPI + LangGraph + Redis 记忆 + Elasticsearch 商品搜索

## 项目结构

```
Final-StandMarket/
├── pom.xml                        # 根 Maven 项目（artifact: fashion-shop）
├── backend/                       # 后端
│   ├── pom.xml                    # fashion-backend 聚合模块
│   ├── fashion-common/            # 通用模块：常量、异常、工具类、配置属性
│   ├── fashion-pojo/              # POJO 模块：实体、DTO、VO
│   └── fashion-server/            # 服务端：Controller / Service / Mapper
│       └── src/main/java/com/fashion/
│           ├── config/            # 配置类（Redisson, Swagger, RabbitMQ, Web, OSS）
│           ├── controller/admin/  # 管理端接口
│           ├── controller/user/   # 用户端接口
│           ├── dto/               # 服务端专用 DTO
│           ├── interceptor/       # JWT 拦截器
│           ├── mapper/            # MyBatis Mapper 接口
│           ├── service/           # Service 接口 + impl 实现
│           └── util/              # 工具类
├── frontend/
│   ├── fashion-admin/             # 管理前端（Element Plus + ECharts）
│   └── fashion-client/            # 用户前端（Element Plus）
├── agent-service/                 # Python AI 客服（FastAPI + LangGraph）
├── docker/                        # Docker 编排
├── mysql/                         # SQL 脚本
└── jmeter/                        # 性能测试脚本
```

## 后端分层规范

### Controller 层

- `admin/` — 管理端接口，URL 以 `/admin` 开头
- `user/` — 用户端接口，URL 以 `/user` 开头
- 统一返回 `Result<T>` 包装

### Service 层

- 接口定义在 `service/`，实现类在 `service/impl/`
- 命名规范: `XxxService` / `XxxServiceImpl`

### Mapper 层

- XML 映射文件在 `resources/mapper/`
- 驼峰命名自动映射已开启

## 构建与运行

### 后端

```bash
# 编译打包
cd backend
mvn clean package -DskipTests

# 运行
java -jar fashion-server/target/fashion-server-1.0-SNAPSHOT.jar
```

### 前端

```bash
# 用户端
cd frontend/fashion-client
npm install
npm run dev          # 开发模式
npm run build        # 生产构建

# 管理端
cd frontend/fashion-admin
npm install
npm run dev
npm run build
```

### 智能客服

```bash
cd agent-service
pip install -r requirements.txt
python app/main.py
```

## 配置说明

主要配置集中在 `application.yml`，通过占位符 `${fashion.xxx}` 引用外部配置：

- `application-dev.yml` — 开发环境（已 gitignore）
- `application-prod.yml` — 生产环境（已 gitignore）
- 需要配置：数据源、Redis、RabbitMQ、阿里云 OSS

## 秒杀核心链路

```
用户请求 → 前端限流 → Redis+Lua 预减库存 → RabbitMQ 异步下单 → 数据库落单
```

关键技术点：

- **Redis + Lua**: 保证库存扣减原子性，防止超卖
- **RabbitMQ 异步**: 流量缓冲，保护数据库
- **Redisson 锁**: 一人一单严格保证
- **延迟队列 + 死信队列**: 30 分钟未支付自动取消订单释放库存
- **库存预热**: 活动开始前缓存预热，避免缓存击穿

## 数据库

主要数据表（详见 `final07.sql`）:

- `product` / `product_sku` — 商品与 SKU
- `orders` / `order_detail` — 订单
- `seckill_activity` / `seckill_coupon` / `seckill_order` — 秒杀
- `shopping_cart` — 购物车
- `user` / `employee` — 用户与员工
- `category` — 分类
- `special_offer` — 特惠活动
- `address_book` — 地址簿
- `combination` / `combination_product` — 组合套餐

## 功能拓展工作流

每一步功能拓展必须按以下流程执行：

1. **第一层**: 确定做什么业务（记录在主计划 MD）
2. **第二层**: 确定该业务的具体执行步骤（记录在阶段 MD）
3. **第三层**: 确定每步要修改/添加哪些代码文件（代码实现）
4. **每个阶段完成后**:
   - 审查子代理（code-reviewer）审查代码
   - 审查报告记录在阶段 MD 文件中
   - 前端检查：新功能页面是否正常、相关页面有无冗余

> 规则: 每一步的详细步骤都要用 MD 文档保存，存放在 `docs/plans/` 目录下。禁止在没有文档规划的情况下直接写代码。
> 例外: 前端代码不需要详细步骤文档，只需在后端步骤文档中附带说明涉及的前端文件即可。

## Git 工作流

- 主分支: `master` / `main`
- 不要提交 `application-dev.yml`、`application-prod.yml`、`*.properties`、`.env` 等敏感配置
- `target/`、`dist/`、`node_modules/` 已 gitignore

## 项目级 Skills（`.claude/skills/`）

仅覆盖「开发 → 测试 → 审查 → 提交」四步，不涉及 push/PR/分支管理。轻量、项目内生效，已 gitignore。

- **test-driven-development** — 开发：先写失败测试再写实现，防逻辑 bug
- **verification-before-completion** — 测试：声称"完成/通过"前必须先跑验证命令（如 `mvn test`、`npm run build`），凭证据不凭断言
- **conventional-commit** — 提交：按本仓库历史的 `feat: Phase N 描述` 风格写提交信息

**审查环节**不依赖额外 skill：使用官方 `pr-review-toolkit` 插件，以及项目 `.claude/agents/` 下的 `ai-module-security-review`、`ai-guide-test-writer` 等 agent。

> 新增 skill：在 `.claude/skills/` 下建文件夹 + `SKILL.md`（带 frontmatter）即可，项目内生效。
