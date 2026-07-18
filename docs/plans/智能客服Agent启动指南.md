# 智能客服 Agent 启动指南

## 前置条件

- JDK 1.8+
- Python 3.10+
- Docker Desktop
- MySQL 8.0、Redis 7.0、RabbitMQ 3.12（现有）
- DeepSeek API Key

---

## 步骤 1：启动 Elasticsearch

```bash
cd docker/elasticsearch
docker-compose up -d
```

验证 ES 是否启动成功：
```bash
curl http://localhost:9200
```

---

## 步骤 2：配置 Python Agent 环境

```bash
cd agent-service

# 复制环境变量文件
copy .env.example .env

# 编辑 .env 文件，填入 DeepSeek API Key
notepad .env
```

`.env` 文件内容示例：
```env
DEEPSEEK_API_KEY=sk-your-api-key-here
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat

JAVA_BACKEND_URL=http://localhost:8080

ES_HOST=localhost
ES_PORT=9200
ES_INDEX=products

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DB=0
REDIS_PASSWORD=

MEMORY_TTL=86400
```

安装 Python 依赖：
```bash
pip install -r requirements.txt
```

---

## 步骤 3：启动 Python Agent 服务

```bash
cd agent-service
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

验证 Agent 服务是否启动成功：
```bash
curl http://localhost:8000/health
```

预期返回：
```json
{"status": "healthy", "service": "fashion-agent"}
```

---

## 步骤 4：启动 Java 后端

```bash
cd backend
mvn clean install -DskipTests
mvn -pl fashion-server spring-boot:run
```

或者在 IDE 中直接运行 `FashionApplication`。

验证 Java 后端是否启动成功：
```bash
curl http://localhost:8080/actuator/health
```

---

## 步骤 5：启动前端

```bash
cd frontend/fashion-client
npm install
npm run dev
```

前端启动后访问：http://localhost:5174

---

## 步骤 6：测试智能客服

1. 登录系统
2. 页面右下角会出现"💬 客服助手"悬浮按钮
3. 点击打开聊天窗口
4. 尝试以下对话：
   - "推荐一些夏季连衣裙"
   - "我的订单状态"
   - "查看购物车"

---

## 常见问题

### 1. Python Agent 启动失败

**错误**：`ModuleNotFoundError: No module named 'langgraph'`

**解决**：
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### 2. Elasticsearch 连接失败

**错误**：`ConnectionError: Could not connect to http://localhost:9200`

**解决**：
```bash
# 检查 Docker 容器是否运行
docker ps | grep fashion-es

# 如果未运行，重新启动
cd docker/elasticsearch
docker-compose up -d
```

### 3. Java 调用 Agent 超时

**错误**：`调用 Agent 服务失败: Read timed out`

**解决**：
- 检查 Python Agent 是否在 8000 端口运行
- 检查防火墙设置
- 增加超时时间（修改 `AgentServiceImpl.java`）

### 4. 商品同步失败

**日志**：`从 Java 后端获取商品失败`

**解决**：
- 确保 Java 后端在 8080 端口运行
- 检查商品表是否有数据
- 手动触发同步：`curl -X POST http://localhost:8000/api/sync-products`

---

## 架构说明

### 数据流

```
用户提问 → 前端悬浮窗口 (Vue)
    ↓ HTTP POST /api/user/agent/chat
Java 后端 (8080) - AgentController
    ↓ HTTP POST /api/chat
Python Agent (8000) - LangGraph 工作流
    ↓
意图识别 → 工具调用 → LLM 生成回复
    ↓
ES (商品检索) + Redis (记忆) + DeepSeek (LLM)
```

### LangGraph 工作流

```
用户输入
    ↓
[identify_intent] - 意图识别
    ↓
[route_by_intent] - 路由分发
    ├── product_search → 搜索商品
    ├── order_query → 查询订单
    ├── cart_operation → 购物车操作
    └── general_chat → 直接回复
    ↓
[generate_response] - LLM 生成回复
    ↓
返回给用户
```

---

## 后续优化建议

1. **添加 IK 分词器**：ES 需要安装 IK 分词器插件以支持中文分词
2. **向量检索优化**：当前使用关键词搜索，可升级为纯向量检索
3. **多轮对话优化**：增强 LangGraph 的状态管理，支持更复杂的多轮对话
4. **商品描述字段**：需要在 MySQL 的 product 表中添加 description 字段
5. **限流控制**：添加请求频率限制，防止滥用
6. **监控日志**：集成 LangSmith 进行 Agent 行为可视化调试

---

## 文件清单

### Python Agent 服务
- `agent-service/app/main.py` - FastAPI 入口
- `agent-service/app/config.py` - 配置管理
- `agent-service/app/graph/workflow.py` - LangGraph 工作流
- `agent-service/app/graph/state.py` - 状态定义
- `agent-service/app/graph/nodes.py` - 节点处理逻辑
- `agent-service/app/graph/edges.py` - 路由边
- `agent-service/app/graph/llm.py` - LLM 配置
- `agent-service/app/tools/product_search.py` - 商品搜索工具
- `agent-service/app/tools/order_query.py` - 订单查询工具
- `agent-service/app/tools/cart_operations.py` - 购物车工具
- `agent-service/app/memory/redis_memory.py` - Redis 连接
- `agent-service/app/memory/memory_manager.py` - 记忆管理
- `agent-service/app/sync/es_client.py` - ES 客户端
- `agent-service/app/sync/product_sync.py` - 商品同步
- `agent-service/requirements.txt` - Python 依赖
- `agent-service/.env.example` - 环境变量示例

### Java 后端
- `backend/fashion-server/src/main/java/com/fashion/controller/user/AgentController.java`
- `backend/fashion-server/src/main/java/com/fashion/service/AgentService.java`
- `backend/fashion-server/src/main/java/com/fashion/service/impl/AgentServiceImpl.java`
- `backend/fashion-pojo/src/main/java/com/fashion/dto/AgentChatRequest.java`
- `backend/fashion-pojo/src/main/java/com/fashion/dto/AgentChatResponse.java`

### 前端
- `frontend/fashion-client/src/components/AgentChat.vue` - 悬浮聊天窗口
- `frontend/fashion-client/src/api/agent.js` - Agent API 封装

### 基础设施
- `docker/elasticsearch/docker-compose.yml` - ES Docker 配置
