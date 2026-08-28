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

## 本地工作流

当前工程规则以 `docs/开发规范.md` 为准，使用三条按需组合的本地工作流：

- **PM / 需求**：`fsm-pm-workflow`，管理方向、PRD、验收和状态。
- **架构**：`fsm-architecture-workflow`，只处理支付/退款契约、交易状态机、Redis/MySQL 一致性、MQ、迁移、鉴权和跨服务契约等高风险决策。
- **开发交付**：`fsm-development-workflow`，管理 workpack、测试先行、独立审查、证据和本地归档。

工作流路由：

1. 阶段 B 的 B0-B11 以 `docs/plans/阶段B-P0P1交易链路修复.md` 为已确认需求源，不重复创建 PRD。
2. 阶段 B 之外的新增用户能力先写 PRD；小型 Bug 和已确认行为修复可以直接进入 workpack。
3. 高风险新决策走 `Design → 独立 Review → 用户确认`；已由阶段 B 明确且没有新歧义的决定直接引用。
4. 标准代码改动使用 `docs/workpack/<phase>-<slice>/` 下的 `plan.md`、`review.md`、`evidence.md`；计划未经用户确认不修改产品代码。
5. 一个 workpack 只包含一至三个紧密相关切片。单文件、约十行、无安全/交易/状态机/迁移/公开契约影响的修改可以走快速通道。
6. 独立审查不可用时记录 `tooling_blocked`，不能冒充 PASS；不再依赖一个并不存在的固定 `code-reviewer` 配置。

本地验证要求：

- Java 后端：在 `backend/` 执行相关聚焦测试，交付前执行 `mvn test`。
- 用户端/管理端：各自执行 `npm run build`；当前没有 test/lint/typecheck 脚本，不得声称相应检查通过。
- Python Agent：在 `agent-service/` 执行 `python -m pytest -q`；依赖阻塞必须如实记录。
- 所有修改：执行 `git diff --check`，复核限定范围 diff 和敏感信息。

## Git 工作流

- 主分支: `master` / `main`
- 基础 GitHub CI、敏感扫描和协作模板位于 `.github/`；生产 CD 尚未启用。
- 未经用户明确要求，不执行 commit、push、建 PR、合并或修改远程仓库设置；获得授权后也必须走功能分支和全绿 CI，禁止直推 `master` 或绕过失败检查。
- 单人串行开发可以使用当前分支；并行或文件可能重叠时使用独立分支和 worktree。
- 提交前必须有 `review.md` PASS、完整 `evidence.md` 和新鲜本地验证；提交信息遵循 Conventional Commits。远程完成还要求目标提交的 GitHub checks 全绿。
- 不要提交 `application-dev.yml`、`application-prod.yml`、`*.properties`、`.env` 等敏感配置
- `target/`、`dist/`、`node_modules/` 已 gitignore
