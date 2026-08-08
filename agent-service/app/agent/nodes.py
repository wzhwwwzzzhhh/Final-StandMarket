import logging

import httpx
import openai
from app.tools.search_product import search_products
from app.tools.recommend import recommend_outfit
from app.tools.size_recommend import (
    get_physical_info,
    recommend_size,
    detect_garment_type,
)
from app.tools.format import format_orders, format_order_summary
from app.tools.outfit_rules import rule_based_reply, reason_for
from app.agent.prompts import INTENT_PROMPT, REPLY_PROMPT, SIZE_PROMPT
from app.config import settings

logger = logging.getLogger(__name__)

LLM_TIMEOUT = 8

llm_client = openai.OpenAI(
    api_key=settings.openai_api_key,
    base_url=settings.openai_base_url,
    timeout=LLM_TIMEOUT,
)

_INTENT_KEYWORDS = [
    ("order", ["订单", "物流", "快递", "到哪了", "发货", "签收", "包裹", "到货"]),
    ("size", ["尺码", "多大码", "码数", "穿什么码", "身高", "体重", "多高", "多少斤", "kg", "公斤"]),
    ("recommend", ["搭配", "穿搭", "怎么穿", "配什么", "套装", "风格", "一身"]),
    ("search", ["推荐", "买", "找", "看看", "有", "怎么选", "什么"]),
]


def _chat_completion(messages: list, max_tokens: int = 200, temperature: float = 0.7) -> str | None:
    """统一 LLM 调用入口：超时 + 异常兜底，失败返回 None，绝不抛出"""
    try:
        resp = llm_client.chat.completions.create(
            model=settings.model_name,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
        )
        return resp.choices[0].message.content
    except Exception as e:
        logger.warning("LLM 调用失败: %s", e)
        return None


def _keyword_intent(message: str) -> str:
    """关键词规则意图识别（LLM 降级用）"""
    for intent, kws in _INTENT_KEYWORDS:
        if any(kw in message for kw in kws):
            return intent
    return "chat"


def _build_chat_messages(state: dict, prompt: str, max_turns: int = 8) -> list:
    """构造 messages 数组：system + 最近历史(user/assistant) + 当前 user"""
    messages = [{"role": "system", "content": prompt}]
    history = state.get("history") or []
    for item in history[-max_turns * 2:]:
        role = item.get("role")
        if role in ("user", "assistant"):
            messages.append({"role": role, "content": item.get("content", "")})
    messages.append({"role": "user", "content": state.get("message", "")})
    return messages


def _format_products_for_llm(products: list, limit: int = 3) -> str:
    """商品列表转 LLM 可读文本，缺字段兜底"""
    lines = []
    for p in products[:limit]:
        if not isinstance(p, dict):
            continue
        name = p.get("name", "")
        price = p.get("price")
        price_txt = f"¥{price}" if price is not None else "价格待定"
        lines.append(f"{name} {price_txt}")
    return "\n".join(lines)


# ===================== 节点 =====================

def recognize_intent(state: dict) -> dict:
    """意图识别节点：LLM 优先，失败降级为关键词规则"""
    message = state["message"]
    prompt = INTENT_PROMPT.format(message=message)
    valid_intents = ["search", "recommend", "order", "size", "chat"]
    content = _chat_completion(
        messages=[{"role": "user", "content": prompt}],
        max_tokens=10,
        temperature=0,
    )
    if content:
        intent = content.strip().lower()
        if intent in valid_intents:
            state["intent"] = intent
            return state
    state["intent"] = _keyword_intent(message)
    return state


def search_product_node(state: dict) -> dict:
    """商品搜索节点：ES 异常时返回友好提示，不抛错"""
    result = search_products(state["message"])
    hits = result.get("hits", [])
    state["search_results"] = hits
    state["search_total"] = result.get("total", 0)
    if result.get("error"):
        state["search_error"] = True
        logger.warning("ES 搜索异常: %s", result["error"])
    return state


def recommend_node(state: dict) -> dict:
    """搭配推荐节点：按类目做互补单品推荐，并附搭配理由"""
    data = recommend_outfit(state["message"])
    state["recommendations"] = data.get("products", [])
    state["recommend_category"] = data.get("main_category", "")
    state["recommend_reason"] = data.get("reason", "")
    return state


def _fetch_orders(token: str) -> dict:
    """调 Java 订单接口获取当前用户订单"""
    url = f"{settings.backend_base_url}/user/agent/order/list"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        resp = httpx.get(url, headers=headers, timeout=5)
        if resp.status_code == 200:
            body = resp.json()
            # Java Result 包装: {code, data, msg}
            if isinstance(body, dict) and body.get("data") is not None:
                return {"orders": body["data"]}
            return {"orders": []}
        return {"error": f"查询失败(status={resp.status_code})"}
    except Exception as e:
        logger.warning("订单接口调用失败: %s", e)
        return {"error": "订单服务暂时不可用"}


def _fetch_tracking(order_id, token: str) -> dict:
    """调 Java 物流接口获取物流信息"""
    url = f"{settings.backend_base_url}/user/agent/tracking/{order_id}"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        resp = httpx.get(url, headers=headers, timeout=5)
        if resp.status_code == 200:
            body = resp.json()
            if isinstance(body, dict) and body.get("data") is not None:
                return {"tracking": body["data"]}
            return {"error": body.get("msg", "暂无物流信息")}
        return {"error": f"查询失败(status={resp.status_code})"}
    except Exception as e:
        logger.warning("物流接口调用失败: %s", e)
        return {"error": "物流服务暂时不可用"}


def order_node(state: dict) -> dict:
    """订单查询节点：拉取订单列表，若最近订单在途则补充物流信息"""
    token = state.get("token", "")
    data = _fetch_orders(token)
    if data.get("error"):
        state["order_info"] = {"error": data["error"]}
        return state

    orders = data.get("orders", [])
    state["order_info"] = {"orders": orders, "count": len(orders)}

    # 为最近的在途订单补充物流信息
    for order in orders:
        if order.get("status") in (2, 3) and order.get("id"):
            tracking = _fetch_tracking(order["id"], token)
            if tracking.get("tracking"):
                order["tracking"] = tracking["tracking"]
            break
    return state


def size_node(state: dict) -> dict:
    """尺码推荐节点：提取身高体重 -> 算尺码 -> 附相关商品"""
    message = state["message"]
    slots = state.setdefault("slots", {})
    info = get_physical_info(message)
    if info["height"] is not None:
        slots["height"] = info["height"]
    if info["weight"] is not None:
        slots["weight"] = info["weight"]

    garment_type = detect_garment_type(message)
    height = slots.get("height")
    weight = slots.get("weight")

    if height is None or weight is None:
        state["size_waiting"] = True
        missing = []
        if height is None:
            missing.append("身高")
        if weight is None:
            missing.append("体重")
        state["reply"] = f"想给你更准的尺码建议，先告诉我你的{('和'.join(missing))}吧，比如「身高170，体重65公斤」～"
        return state

    state["size_waiting"] = False
    state["size_recommend"] = recommend_size(height, weight, garment_type)
    state["size_garment"] = garment_type
    state["reply"] = (
        f"根据你的身高{height}cm、体重{weight}kg，{garment_type}建议选 {state['size_recommend']} 码。"
    )
    # 关联推荐商品
    result = search_products(f"{garment_type} {state['size_recommend']}", size=3)
    state["recommendations"] = result.get("hits", [])
    return state


def generate_reply(state: dict) -> dict:
    """回复生成节点：意图数据 + LLM 生成，LLM 不可用时走规则兜底"""
    # 槽位未收集齐（如缺身高/体重）时，保留 size_node 的追问话术
    if state.get("size_waiting"):
        state["reply"] = "想给你更准的尺码建议，先告诉我你的身高和体重吧，比如「身高170，体重65公斤」～"
        return state

    intent = state["intent"]
    data = {}

    if state.get("search_results"):
        data["products"] = _format_products_for_llm(state["search_results"])
        data["search_total"] = state.get("search_total", 0)
    if state.get("recommendations"):
        data["recommendations"] = _format_products_for_llm(state["recommendations"])
        if state.get("recommend_reason"):
            data["reason"] = state["recommend_reason"]
    if state.get("order_info"):
        orders = state["order_info"].get("orders", [])
        data["order"] = format_orders(orders)
        data["order_count"] = state["order_info"].get("count", 0)
        # 附在途订单物流摘要
        for o in orders:
            if o.get("tracking"):
                data["tracking"] = format_order_summary(o)
                break
    if state.get("size_recommend"):
        data["size"] = f"{state['size_garment']}建议穿{state['size_recommend']}码"

    reply = _chat_completion(
        messages=_build_chat_messages(state, REPLY_PROMPT.format(intent=intent, data=str(data)),
                                      max_turns=8),
        max_tokens=200,
    )
    if reply:
        state["reply"] = reply
        return state

    # ===== LLM 不可用：规则兜底 =====
    if intent == "order":
        state["reply"] = rule_based_reply("order", order_text=data.get("order", ""))
    elif intent == "size":
        if state.get("size_waiting"):
            state["reply"] = "请告诉我你的身高和体重，我来帮你推荐合适的尺码，比如「身高170，体重65公斤」。"
        else:
            size_text = f"{state.get('size_garment', '上装')}建议选 {state.get('size_recommend', 'M')} 码，点击下方卡片看看相关商品～"
            state["reply"] = rule_based_reply("size", size_text=size_text,
                                              product_names=[p.get("name", "") for p in state.get("recommendations", [])])
    elif intent == "recommend":
        names = [p.get("name", "") for p in state.get("recommendations", [])]
        state["reply"] = rule_based_reply(
            "recommend", product_names=names,
            reason=state.get("recommend_reason", "") or f"搭配{('、'.join(names[:2]))}更完整。")
    elif intent == "search":
        names = [p.get("name", "") for p in state.get("search_results", [])]
        if state.get("search_error"):
            state["reply"] = "服务繁忙，请稍后再试。"
        else:
            state["reply"] = rule_based_reply("search", search_total=state.get("search_total", 0),
                                              product_names=names)
    else:
        state["reply"] = rule_based_reply("chat")
    return state
