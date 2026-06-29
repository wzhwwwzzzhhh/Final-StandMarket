import httpx
import openai
from app.tools.search_product import search_products
from app.tools.recommend import recommend_outfit
from app.agent.prompts import INTENT_PROMPT, REPLY_PROMPT
from app.config import settings


def recognize_intent(state: dict) -> dict:
    """意图识别节点：调用 LLM 分类用户意图"""
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
