# AI 智能导购 — 手写代码步骤

> 你手写 LangGraph 核心逻辑，共约 230 行。按顺序完成，每完成一个文件跑 `python -c "compile(open('agent-service/app/agent/...').read(), '...', 'exec')"` 检查语法。

## 文件结构

```
agent-service/app/
├── agent/
│   ├── __init__.py        # 空（已存在）
│   ├── graph.py           # Step 5 — StateGraph 组装（~30行）
│   ├── nodes.py           # Step 3 — Agent 节点函数（~80行）
│   └── prompts.py         # Step 1 — System Prompt（~20行）
└── tools/
    ├── __init__.py         # 空（已存在）
    ├── search_product.py   # Step 2 — ES 商品搜索（~50行）
    └── recommend.py        # Step 4 — 搭配推荐（~50行）
```

---

## Step 1：Prompt 模板

**文件**: `agent-service/app/agent/prompts.py`

```python
SYSTEM_PROMPT = """你是 StandMarket 时尚商城的 AI 导购助手，名叫"小衣"。

## 能力边界
1. **商品推荐** — 根据用户需求推荐合适的商品
2. **搭配建议** — 给出上下衣/配饰的搭配方案
3. **订单查询** — 查询用户订单状态（调用工具）
4. **尺码建议** — 根据身高体重推荐尺码
5. **闲聊** — 友好回答，但引导回购物话题

## 回答规则
- 回答简洁友好，控制在 3 句以内
- 推荐商品时说明理由（材质/版型/场景）
- 推荐搭配时至少包含 2 件可搭配单品
- 如果用户问订单，引导用户提供订单号
- 如果用户问尺码，询问身高体重
- 不知道的不要瞎编，说"这个我需要查一下"
- 使用中文回答，语气亲切但专业

## 工具调用规则
当你需要查询数据时，使用提供的工具。不要在回答中编造商品信息。
"""

INTENT_PROMPT = """分析用户消息的意图，只返回一个词：
- search: 用户想找商品/浏览/看看有什么
- recommend: 用户要搭配推荐/穿搭建议
- order: 用户查询订单/物流
- size: 用户问尺码
- chat: 其他闲聊

用户消息: {message}
"""

REPLY_PROMPT = """你是导购"小衣"，根据以下信息生成回答：

用户意图: {intent}
商品/订单数据: {data}
对话历史: {history}

生成 1-3 句回答，自然友好。"""
```

**验证**: `python -c "compile(open('agent-service/app/agent/prompts.py').read(), 'prompts.py', 'exec')"`

---

## Step 2：ES 商品搜索工具

**文件**: `agent-service/app/tools/search_product.py`

```python
from elasticsearch import Elasticsearch
from app.config import settings

es = Elasticsearch(hosts=[settings.es_host])

def search_products(query: str, size: int = 5) -> list[dict]:
    """ES 多字段搜索商品，返回列表中包含 id/name/price/image/description/score"""
    body = {
        "query": {
            "multi_match": {
                "query": query,
                "fields": ["name^3", "description^2", "tag"],
                "fuzziness": "AUTO",
                "type": "best_fields"
            }
        },
        "size": size
    }
    resp = es.search(index="products", body=body)
    hits = resp["hits"]["hits"]
    results = []
    for h in hits:
        src = h["_source"]
        src["score"] = h["_score"]
        results.append(src)
    return results
```

**验证**: `python -c "from app.tools.search_product import search_products; print('import ok')"`（需要在 agent-service/ 目录下运行）

---

## Step 3：Agent 节点函数

**文件**: `agent-service/app/agent/nodes.py`

核心逻辑：每个节点接收 `AgentState` dict，返回更新后的 dict。

```python
import httpx
from app.tools.search_product import search_products
from app.tools.recommend import recommend_outfit
from app.agent.prompts import INTENT_PROMPT

# AgentState 结构:
# {
#   "message": str,          # 用户消息
#   "userId": int,           # 用户ID
#   "sessionId": str,        # 会话ID
#   "history": list,         # 历史消息
#   "intent": str,           # 意图分类
#   "search_results": list,  # 搜索结果
#   "recommendations": list, # 推荐结果
#   "order_info": dict,      # 订单信息
#   "reply": str             # 最终回复
# }


def recognize_intent(state: dict) -> dict:
    """意图识别节点：调用 LLM 分类用户意图"""
    import openai
    from app.config import settings

    client = openai.OpenAI(
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url
    )
    prompt = INTENT_PROMPT.format(message=state["message"])
    resp = client.chat.completions.create(
        model=settings.model_name,
        messages=[{"role": "user", "content": prompt}],
        temperature=0,
        max_tokens=10
    )
    intent = resp.choices[0].message.content.strip().lower()
    valid_intents = ["search", "recommend", "order", "size", "chat"]
    state["intent"] = intent if intent in valid_intents else "chat"
    return state


def search_product_node(state: dict) -> dict:
    """商品搜索节点：意图为 search 时执行"""
    results = search_products(state["message"])
    state["search_results"] = results
    return state


def recommend_node(state: dict) -> dict:
    """搭配推荐节点：意图为 recommend 时执行"""
    results = recommend_outfit(state["message"])
    state["recommendations"] = results
    return state


def order_node(state: dict) -> dict:
    """订单查询节点：调 Java 后端接口查订单"""
    try:
        url = f"{settings.backend_base_url}/user/order/list?userId={state['userId']}"
        resp = httpx.get(url, timeout=5)
        state["order_info"] = resp.json() if resp.status_code == 200 else {"error": "查询失败"}
    except Exception as e:
        state["order_info"] = {"error": str(e)}
    return state


def generate_reply(state: dict) -> dict:
    """回复生成节点：根据意图和数据生成最终回答"""
    import openai
    from app.config import settings
    from app.agent.prompts import REPLY_PROMPT

    # 组装数据
    data = {}
    if state.get("search_results"):
        products = state["search_results"][:3]
        data["products"] = [f"{p['name']} ¥{p['price']}" for p in products]
    if state.get("recommendations"):
        data["recommendations"] = state["recommendations"]
    if state.get("order_info"):
        data["order"] = state["order_info"]

    client = openai.OpenAI(
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url
    )
    prompt = REPLY_PROMPT.format(
        intent=state["intent"],
        data=str(data),
        history=state["history"][-3:] if state.get("history") else []
    )
    resp = client.chat.completions.create(
        model=settings.model_name,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.7,
        max_tokens=200
    )
    state["reply"] = resp.choices[0].message.content
    return state
```

**验证**: `python -c "compile(open('agent-service/app/agent/nodes.py').read(), 'nodes.py', 'exec')"`

---

## Step 4：搭配推荐工具

**文件**: `agent-service/app/tools/recommend.py`

```python
from elasticsearch import Elasticsearch
from app.config import settings

es = Elasticsearch(hosts=[settings.es_host])

OUTFIT_RULES = {
    "上衣": ["下装", "裤", "裙", "牛仔裤"],
    "T恤": ["牛仔裤", "休闲裤", "短裤", "半身裙"],
    "衬衫": ["西裤", "牛仔裤", "半身裙", "阔腿裤"],
    "连衣裙": ["开衫", "外套", "腰带", "凉鞋"],
    "外套": ["连衣裙", "T恤", "衬衫", "牛仔裤"],
    "毛织": ["牛仔裤", "休闲裤", "半身裙"],
    "下装": ["上衣", "T恤", "衬衫", "毛织"],
    "裤": ["T恤", "衬衫", "毛织", "卫衣"],
    "裙": ["衬衫", "毛织", "T恤", "开衫"],
}


def recommend_outfit(query: str, size: int = 4) -> list[dict]:
    """根据用户查询做搭配推荐，返回搭配组合"""
    body = {
        "query": {
            "multi_match": {
                "query": query,
                "fields": ["name^3", "description^2", "tag"],
                "fuzziness": "AUTO"
            }
        },
        "size": size
    }
    try:
        resp = es.search(index="products", body=body)
        return [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        return []


def get_complementary(item_name: str, category_id: int) -> list[dict]:
    """根据某商品找搭配单品"""
    # 根据 category_id 推断品类，找互补品类商品
    # 简化版：返回同价位段的其他品类商品
    body = {
        "query": {
            "bool": {
                "must_not": [{"term": {"categoryId": category_id}}],
                "must": [{"range": {"price": {"gte": 50, "lte": 500}}}]
            }
        },
        "size": 3
    }
    try:
        resp = es.search(index="products", body=body)
        return [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        return []
```

**验证**: `python -c "compile(open('agent-service/app/tools/recommend.py').read(), 'recommend.py', 'exec')"`

---

## Step 5：Graph 组装

**文件**: `agent-service/app/agent/graph.py`

```python
from langgraph.graph import StateGraph, END
from app.agent.nodes import *

# AgentState 类型
def create_initial_state(message: str, user_id: int, session_id: str, history: list = None) -> dict:
    return {
        "message": message,
        "userId": user_id,
        "sessionId": session_id,
        "history": history or [],
        "intent": "",
        "search_results": [],
        "recommendations": [],
        "order_info": {},
        "reply": ""
    }


def router(state: dict) -> str:
    """意图路由：根据 intent 选择下一节点"""
    intent_map = {
        "search": "search_product",
        "recommend": "recommend",
        "order": "order_query",
        "size": "chat_reply",
        "chat": "chat_reply",
    }
    return intent_map.get(state["intent"], "chat_reply")


def build_graph() -> StateGraph:
    workflow = StateGraph(dict)

    # 注册节点
    workflow.add_node("recognize_intent", recognize_intent)
    workflow.add_node("search_product", search_product_node)
    workflow.add_node("recommend", recommend_node)
    workflow.add_node("order_query", order_node)
    workflow.add_node("generate_reply", generate_reply)

    # 设置入口
    workflow.set_entry_point("recognize_intent")

    # 条件路由
    workflow.add_conditional_edges("recognize_intent", router, {
        "search_product": "search_product",
        "recommend": "recommend",
        "order_query": "order_query",
        "chat_reply": "generate_reply",
    })

    # 工具节点 → 回复生成
    workflow.add_edge("search_product", "generate_reply")
    workflow.add_edge("recommend", "generate_reply")
    workflow.add_edge("order_query", "generate_reply")

    # 结束
    workflow.add_edge("generate_reply", END)

    return workflow.compile()


agent_graph = build_graph()
```

**验证**: `python -c "compile(open('agent-service/app/agent/graph.py').read(), 'graph.py', 'exec')"`

---

## Step 6：更新 main.py 接入 Graph

**文件**: `agent-service/app/main.py`（已存在，修改 `/chat` 端点）

```python
@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=422, detail="message cannot be empty")

    session_id = req.sessionId or uuid.uuid4().hex[:16]
    history = await get_history(session_id)

    # 执行 LangGraph
    initial_state = create_initial_state(
        message=req.message,
        user_id=req.userId,
        session_id=session_id,
        history=history
    )
    result = await agent_graph.ainvoke(initial_state)

    # 保存到 Redis 记忆
    await save_message(session_id, "user", req.message)
    await save_message(session_id, "assistant", result["reply"])

    products = result.get("search_results") or result.get("recommendations") or []
    return ChatResponse(
        reply=result["reply"],
        sessionId=session_id,
        products=[ProductItem(**p) for p in products if isinstance(p, dict)]
    )
```

记得在文件顶部加 import：

```python
from app.agent.graph import create_initial_state, agent_graph
from app.redis_memory import get_history, save_message
```

---

## 执行顺序总结

| 步骤     | 文件                        | 行数  | 耗时    |
| ------ | ------------------------- | --- | ----- |
| Step 1 | `prompts.py`              | ~20 | 5min  |
| Step 2 | `tools/search_product.py` | ~50 | 5min  |
| Step 3 | `agent/nodes.py`          | ~80 | 15min |
| Step 4 | `tools/recommend.py`      | ~50 | 5min  |
| Step 5 | `agent/graph.py`          | ~30 | 10min |
| Step 6 | `main.py` 修改              | ~15 | 5min  |

写完以后跑 `cd agent-service && python -c "from app.agent.graph import agent_graph; print('Graph 构建成功')"` 验证。
