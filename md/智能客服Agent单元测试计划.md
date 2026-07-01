# 智能客服 Agent 单元测试计划

## 测试概述

本项目包含两个主要模块需要测试：
1. **Python Agent 服务**（agent-service）- 基于 pytest
2. **Java 后端服务**（fashion-server）- 基于 JUnit 5 + Mockito

---

## 一、Python Agent 单元测试

### 1.1 意图识别节点测试
**文件**: `tests/test_nodes.py`
**测试目标**: `app/graph/nodes.py` 中的 `identify_intent` 函数

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_identify_product_search` | 用户输入包含"找"、"推荐"等关键词 | 返回 `product_search` |
| `test_identify_order_query` | 用户输入包含"订单"、"查询"等关键词 | 返回 `order_query` |
| `test_identify_cart_operation` | 用户输入包含"购物车"、"添加"等关键词 | 返回 `cart_operation` |
| `test_identify_general_chat` | 用户输入普通聊天内容 | 返回 `general_chat` |
| `test_identify_invalid_intent` | 用户输入无法识别的内容 | 返回 `general_chat`（默认） |

### 1.2 商品搜索工具测试
**文件**: `tests/test_product_search.py`
**测试目标**: `app/tools/product_search.py` 中的 `search_products` 工具

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_search_products_success` | 成功搜索商品 | 返回商品列表字符串 |
| `test_search_products_no_results` | 搜索不存在的商品 | 返回"未找到"提示 |
| `test_search_products_with_category` | 带分类搜索 | 返回指定分类商品 |
| `test_search_products_api_error` | Java API 调用失败 | 返回错误信息 |

### 1.3 订单查询工具测试
**文件**: `tests/test_order_query.py`
**测试目标**: `app/tools/order_query.py` 中的 `query_orders` 工具

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_query_orders_success` | 成功查询订单 | 返回订单列表 |
| `test_query_orders_no_orders` | 用户无订单 | 返回"暂无订单"提示 |
| `test_query_orders_api_error` | API 调用失败 | 返回错误信息 |

### 1.4 购物车操作工具测试
**文件**: `tests/test_cart_operations.py`
**测试目标**: `app/tools/cart_operations.py` 中的 `add_to_cart` 工具

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_add_to_cart_success` | 成功添加到购物车 | 返回成功消息 |
| `test_add_to_cart_api_error` | API 调用失败 | 返回错误信息 |

### 1.5 记忆管理测试
**文件**: `tests/test_memory_manager.py`
**测试目标**: `app/memory/memory_manager.py` 中的 `ChatMemoryManager` 类

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_add_message` | 添加消息到 Redis | 消息存储成功 |
| `test_get_history` | 获取对话历史 | 返回历史消息列表 |
| `test_clear_memory` | 清空对话历史 | Redis 中对应 key 被删除 |
| `test_get_messages_as_langchain_format` | 转换为 LangChain 格式 | 返回正确格式的消息 |

### 1.6 商品同步测试
**文件**: `tests/test_product_sync.py`
**测试目标**: `app/sync/product_sync.py` 中的同步函数

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_fetch_products_from_java_success` | 成功从 Java 获取商品 | 返回商品列表 |
| `test_fetch_products_from_java_error` | Java API 调用失败 | 返回空列表 |
| `test_generate_embedding_success` | 成功生成 Embedding | 返回向量数组 |
| `test_generate_embedding_error` | Embedding API 失败 | 返回默认零向量 |
| `test_sync_products_to_es` | 同步商品到 ES | 日志显示同步成功 |
| `test_search_products_by_vector` | 向量搜索商品 | 返回搜索结果 |
| `test_search_products_by_keyword` | 关键词搜索商品 | 返回搜索结果 |

### 1.7 工作流图测试
**文件**: `tests/test_workflow.py`
**测试目标**: `app/graph/workflow.py` 中的 `agent_graph`

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_graph_product_search_flow` | 测试商品搜索流程 | 正确路由到商品搜索节点 |
| `test_graph_order_query_flow` | 测试订单查询流程 | 正确路由到订单查询节点 |
| `test_graph_general_chat_flow` | 测试普通聊天流程 | 正确路由到普通聊天节点 |

---

## 二、Java 后端单元测试

### 2.1 AgentService 测试
**文件**: `AgentServiceImplTest.java`
**测试目标**: `com.fashion.service.impl.AgentServiceImpl`

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_chat_success` | 成功调用 Python Agent | 返回 Agent 响应 |
| `test_chat_service_error` | Python Agent 服务异常 | 返回降级消息 |
| `test_chat_timeout` | Python Agent 超时 | 返回降级消息 |

### 2.2 AgentController 测试
**文件**: `AgentControllerTest.java`
**测试目标**: `com.fashion.controller.user.AgentController`

| 测试用例 | 描述 | 预期结果 |
|---------|------|---------|
| `test_chat_success` | 用户已登录，成功调用 | 返回 200 + 响应数据 |
| `test_chat_not_logged_in` | 用户未登录 | 返回错误提示"请先登录" |
| `test_chat_invalid_request` | 请求参数无效 | 返回 400 错误 |

---

## 三、测试执行命令

### Python 测试
```bash
cd agent-service
pip install pytest pytest-mock
pytest tests/ -v --cov=app --cov-report=html
```

### Java 测试
```bash
cd backend/fashion-server
mvn test -Dtest=AgentServiceImplTest,AgentControllerTest
```

---

## 四、测试覆盖率目标

| 模块 | 目标覆盖率 |
|------|-----------|
| Python Agent 核心逻辑 | ≥ 80% |
| Java AgentService | ≥ 85% |
| Java AgentController | ≥ 90% |

---

## 五、Mock 策略

### Python
- 使用 `pytest-mock` 或 `unittest.mock` 模拟外部 API 调用
- Mock `httpx.get/post` 避免真实网络请求
- Mock `redis_client` 避免依赖真实 Redis
- Mock `es_client` 避免依赖真实 Elasticsearch

### Java
- 使用 `Mockito` 模拟 `RestTemplate`
- 使用 `@WebMvcTest` 测试 Controller 层
- 使用 `@ExtendWith(MockitoExtension.class)` 进行单元测试

---

## 六、测试数据准备

### 测试用商品数据
```python
MOCK_PRODUCTS = [
    {
        "id": 1,
        "name": "夏季连衣裙",
        "description": "轻薄透气，适合夏季穿着",
        "price": 199.00,
        "stock": 100,
        "categoryId": 1,
        "categoryName": "女装"
    }
]
```

### 测试用订单数据
```python
MOCK_ORDERS = [
    {
        "id": 1001,
        "orderNo": "ORD20260529001",
        "status": "待发货",
        "totalAmount": 199.00
    }
]
```

---

## 七、测试文件目录结构

```
agent-service/
├── tests/
│   ├── __init__.py
│   ├── conftest.py              # 测试配置和 fixtures
│   ├── test_nodes.py            # 节点测试
│   ├── test_product_search.py   # 商品搜索测试
│   ├── test_order_query.py      # 订单查询测试
│   ├── test_cart_operations.py  # 购物车测试
│   ├── test_memory_manager.py   # 记忆管理测试
│   ├── test_product_sync.py     # 商品同步测试
│   └── test_workflow.py         # 工作流测试

backend/fashion-server/
├── src/test/java/com/fashion/
│   ├── controller/
│   │   └── AgentControllerTest.java
│   └── service/
│       └── AgentServiceImplTest.java
```
