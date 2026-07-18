# 智能客服 Agent 助手 - 任务总结

## 项目概述

为时尚电商系统（Final-StandMarket）添加基于 LangChain + LangGraph 的智能客服 Agent，支持商品推荐、订单查询、购物车管理等能力。

---

## 技术架构

### 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| Java 后端 | Spring Boot 2.7.15 | 业务逻辑（订单、购物车等） |
| Python Agent | FastAPI + LangChain + LangGraph | 智能客服核心 |
| LLM | DeepSeek API | 对话生成 |
| Elasticsearch | 8.x (Docker) | 商品知识库向量检索 |
| Redis | 7.0 (现有) | 对话记忆 + 缓存 |
| MySQL | 8.0 (现有) | 业务数据存储 |
| 前端 | Vue 3 + Element Plus | 悬浮聊天窗口 |

### 架构设计

```
Vue 前端（悬浮聊天窗口）
    ↓ HTTP
Java 后端 (8080) - AgentController 转发
    ↓ HTTP REST
Python Agent 服务 (8000) - LangGraph 工作流
    ↓
ES (知识库) + Redis (记忆) + DeepSeek (LLM)
```

---

## 核心功能

### 1. 商品推荐
- 基于 Elasticsearch 向量检索
- 根据用户描述推荐匹配商品
- 返回商品列表 + 推荐理由

### 2. 订单查询
- 查询订单状态
- 物流信息跟踪

### 3. 购物车管理
- 帮助用户添加商品到购物车
- 添加后提醒用户确认

### 4. 闲聊对话
- 常见问题解答
- 时尚穿搭建议

---

## 数据流

### 商品描述同步流程
```
管理端添加商品描述 → MySQL (product 表)
    ↓
定时任务 (每分钟)
    ↓
读取新增/更新商品
    ↓
生成 Embedding (DeepSeek)
    ↓
写入 ES (dense_vector)
```

### 用户对话流程
```
用户提问 → 前端悬浮窗口
    ↓
Java AgentController (校验登录态)
    ↓
转发到 Python Agent (HTTP POST)
    ↓
LangGraph 工作流:
  1. 意图识别 (LLM)
  2. 选择工具
  3. 执行工具 (ES检索/Java API调用)
  4. 生成回复 (LLM)
    ↓
返回 JSON → Java → 前端展示
```

---

## 目录结构

```
Final-StandMarket/
├── backend/
│   └── fashion-server/
│       └── src/main/java/com/fashion/
│           ├── controller/user/
│           │   └── AgentController.java        # Agent 请求转发
│           └── service/
│               └── AgentService.java           # 调用 Python Agent
│
├── agent-service/                              # Python Agent 服务
│   ├── app/
│   │   ├── main.py                             # FastAPI 入口
│   │   ├── config.py                           # 配置
│   │   ├── graph/
│   │   │   └── customer_service.py             # LangGraph 工作流
│   │   ├── tools/
│   │   │   ├── product_search.py               # ES 商品检索工具
│   │   │   ├── order_query.py                  # 订单查询工具
│   │   │   └── cart_operations.py              # 购物车操作工具
│   │   ├── memory/
│   │   │   └── redis_memory.py                 # Redis 对话记忆
│   │   └── sync/
│   │       └── product_sync.py                 # 商品描述同步 ES
│   ├── requirements.txt
│   └── Dockerfile
│
├── docker/
│   └── elasticsearch/
│       └── docker-compose.yml                  # ES Docker 配置
│
└── frontend/
    └── fashion-client/
        └── src/
            └── components/
                └── AgentChat.vue               # 悬浮聊天窗口
```

---

## API 设计

### Java → Python Agent
```
POST http://localhost:8000/api/chat
Request:
{
  "user_id": 123,
  "session_id": "uuid",
  "message": "我想买一件夏季连衣裙"
}

Response:
{
  "reply": "为您推荐以下夏季连衣裙...",
  "products": [...],
  "action": null
}
```

### Python → Java (工具调用)
```
GET  http://localhost:8080/user/product?keyword=连衣裙
GET  http://localhost:8080/user/order/{orderId}
POST http://localhost:8080/user/cart
```

---

## 任务清单

### 阶段一：基础设施
- [ ] 1. 创建 Python Agent 服务目录结构
- [ ] 2. 配置 Elasticsearch Docker 环境
- [ ] 3. 安装 Python 依赖（LangChain, LangGraph, FastAPI 等）

### 阶段二：Agent 核心
- [ ] 4. 实现 LangGraph 工作流（意图识别 + 路由）
- [ ] 5. 实现商品检索工具（ES 向量检索）
- [ ] 6. 实现订单查询工具（调用 Java API）
- [ ] 7. 实现购物车操作工具（调用 Java API）
- [ ] 8. 实现 Redis 对话记忆管理

### 阶段三：数据同步
- [ ] 9. 实现商品描述同步 ES 定时任务
- [ ] 10. 创建 ES 索引映射（dense_vector）

### 阶段四：Java 集成
- [ ] 11. 添加 AgentController 请求转发
- [ ] 12. 添加 AgentService 调用 Python 服务

### 阶段五：前端集成
- [ ] 13. 创建悬浮聊天窗口组件
- [ ] 14. 集成 Agent API 调用

### 阶段六：测试
- [ ] 15. 测试完整对话链路
- [ ] 16. 测试商品推荐功能
- [ ] 17. 测试购物车添加功能

---

## 安全控制

- 登录后可用（JWT 校验）
- 频率限制（可选后续添加）
- 敏感词过滤（可选后续添加）

---

## 注意事项

1. DeepSeek API Key 需要配置在环境变量中
2. ES 容器需要足够内存（建议 2GB+）
3. Java 8080 端口使用完毕后需释放
4. 所有 MD 文档放在 `D:\market-handsome\Final-StandMarket\md` 目录
