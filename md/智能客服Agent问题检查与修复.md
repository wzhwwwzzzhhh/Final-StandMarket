# 智能客服 Agent 问题检查与修复报告

## 检查日期
2026-05-29

---

## 发现的问题及修复

### 1. BaseContext 方法调用错误 ✅ 已修复

**问题描述**：
- `AgentController.java` 使用了 `BaseContext.getCurrentId()`
- 但项目实际使用的是 `BaseContext.getUserId()`

**影响**：用户登录后无法获取 userId，导致 Agent 无法调用订单查询等需要用户 ID 的工具

**修复方案**：
```java
// 修复前
Long userId = BaseContext.getCurrentId();

// 修复后
Long userId = BaseContext.getUserId();
```

**文件**：`backend/fashion-server/src/main/java/com/fashion/controller/user/AgentController.java`

---

### 2. Python Agent 未使用 ES 向量检索 ✅ 已修复

**问题描述**：
- 商品搜索工具直接调用 Java API，没有使用 ES 的向量检索能力
- ES 同步了商品描述向量但从未使用

**影响**：无法实现语义搜索，只能关键词匹配

**修复方案**：
```python
# 修复前：直接调用 Java API
response = httpx.get(f"{settings.JAVA_BACKEND_URL}/user/product", params=params)

# 修复后：优先使用 ES 向量检索，降级到关键词搜索，最后降级到 Java API
products = search_products_by_vector(keyword, top_k=5)
if not products:
    products = search_products_by_keyword(keyword, top_k=5)
if not products:
    # 降级到 Java API
    response = httpx.get(...)
```

**文件**：`agent-service/app/tools/product_search.py`

---

### 3. Python 依赖缺少 langchain-core ✅ 已修复

**问题描述**：
- `requirements.txt` 缺少 `langchain-core` 显式依赖
- 可能导致版本不兼容

**修复方案**：
```
langchain-core==0.3.34
```

**文件**：`agent-service/requirements.txt`

---

### 4. 前端错误处理优化 ✅ 已修复

**问题描述**：
- 前端错误提示固定为"服务暂时不可用"
- 没有显示后端返回的具体错误信息

**修复方案**：
```javascript
// 修复前
this.addMessage('assistant', '抱歉，服务暂时不可用，请稍后再试。')

// 修复后
this.addMessage('assistant', response.data?.msg || '抱歉，服务暂时不可用，请稍后再试。')
```

**文件**：`frontend/fashion-client/src/components/AgentChat.vue`

---

## 需要注意的问题（未修复，需手动处理）

### 5. ES IK 分词器未安装 ⚠️ 需手动处理

**问题描述**：
- ES 8.x 默认不包含 IK 分词器
- 中文分词会失效，影响搜索效果

**解决方案**：
```bash
# 进入 ES 容器
docker exec -it fashion-es bash

# 安装 IK 分词器
elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.17.0/elasticsearch-analysis-ik-8.17.0.zip

# 重启容器
docker restart fashion-es
```

**或者修改 docker-compose.yml**：
```yaml
services:
  elasticsearch:
    image: elasticsearch:8.17.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    volumes:
      - es_data:/usr/share/elasticsearch/data
      - ./plugins/ik:/usr/share/elasticsearch/plugins/ik  # 挂载 IK 分词器
```

---

### 6. DeepSeek Embedding API 可能不支持 ⚠️ 需确认

**问题描述**：
- `product_sync.py` 使用 DeepSeek 生成 Embedding
- DeepSeek 可能不支持 Embedding API（仅支持聊天）

**解决方案**：
选项 A：使用 OpenAI Embedding
```python
def generate_embedding(text: str) -> list:
    response = httpx.post(
        "https://api.openai.com/v1/embeddings",
        headers={"Authorization": f"Bearer {OPENAI_API_KEY}"},
        json={"model": "text-embedding-3-small", "input": text}
    )
    return response.json()["data"][0]["embedding"]
```

选项 B：使用本地模型
```python
from sentence_transformers import SentenceTransformer

model = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')

def generate_embedding(text: str) -> list:
    return model.encode(text).tolist()
```

选项 C：使用阿里云 Embedding
```python
def generate_embedding(text: str) -> list:
    response = httpx.post(
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding",
        headers={"Authorization": f"Bearer {DASHSCOPE_API_KEY}"},
        json={"model": "text-embedding-v2", "input": {"texts": [text]}}
    )
    return response.json()["output"]["embeddings"][0]["embedding"]
```

---

### 7. RestTemplate 未使用 Spring 管理 ⚠️ 建议优化

**问题描述**：
- `AgentServiceImpl` 直接 `new RestTemplate()`
- 未使用 Spring 管理的 Bean，无法享受连接池等优化

**建议方案**：
```java
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
public class AgentServiceImpl implements AgentService {
    @Autowired
    private RestTemplate restTemplate;
    // ...
}
```

---

### 8. 商品描述字段可能不存在 ⚠️ 需确认数据库

**问题描述**：
- MySQL 的 `product` 表可能没有 `description` 字段
- 需要同步到 ES 的描述内容为空

**解决方案**：
```sql
-- 检查字段是否存在
DESC product;

-- 如果不存在，添加字段
ALTER TABLE product ADD COLUMN description TEXT COMMENT '商品描述';
```

---

## 架构优化建议

### 9. 添加 RestTemplate 超时配置

```java
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(30000);
    return new RestTemplate(factory);
}
```

### 10. 添加 Agent 限流控制

```java
// 使用 Redis 实现滑动窗口限流
public boolean isRateLimited(Long userId) {
    String key = "agent:rate_limit:" + userId;
    long count = redisTemplate.opsForZSet().zCard(key);
    if (count >= 10) { // 每分钟最多 10 次
        return true;
    }
    redisTemplate.opsForZSet().add(key, System.currentTimeMillis(), System.currentTimeMillis());
    redisTemplate.expire(key, 60, TimeUnit.SECONDS);
    return false;
}
```

### 11. 添加 LangSmith 调试支持

```python
# .env 文件添加
LANGCHAIN_TRACING_V2=true
LANGCHAIN_API_KEY=your_langsmith_key
LANGCHAIN_PROJECT=fashion-agent
```

---

## 总结

| 问题 | 状态 | 优先级 |
|------|------|--------|
| BaseContext 方法错误 | ✅ 已修复 | P0 |
| ES 向量检索未使用 | ✅ 已修复 | P0 |
| Python 依赖不完整 | ✅ 已修复 | P1 |
| 前端错误处理优化 | ✅ 已修复 | P1 |
| ES IK 分词器 | ⚠️ 需手动安装 | P1 |
| DeepSeek Embedding 支持 | ⚠️ 需确认 | P0 |
| RestTemplate 配置 | ⚠️ 建议优化 | P2 |
| 商品描述字段 | ⚠️ 需确认数据库 | P0 |

---

## 下一步行动

1. **确认 DeepSeek 是否支持 Embedding API**
   - 如果不支持，需要切换到其他 Embedding 服务
   - 推荐使用阿里云 DashScope 或本地模型

2. **检查 MySQL product 表结构**
   - 确认是否有 `description` 字段
   - 如果没有，需要添加

3. **安装 ES IK 分词器**
   - 按照上述方案安装

4. **测试完整链路**
   - 启动所有服务
   - 测试商品搜索、订单查询、购物车操作
