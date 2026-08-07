"""LLM 降级兜底与节点行为单元测试"""
import sys
from pathlib import Path
from unittest.mock import patch, AsyncMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.agent.nodes import (
    recognize_intent,
    generate_reply,
    _chat_completion,
    _format_products_for_llm,
)


# ===================== LLM 调用超时兜底 =====================

def test_chat_completion_returns_none_on_error():
    """LLM 调用异常时必须返回 None，不能抛出"""
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("timeout")
        result = _chat_completion("你好", max_tokens=10)
        assert result is None


def test_chat_completion_returns_content_on_success():
    """LLM 正常时返回 content 字符串"""
    fake_resp = type("R", (), {"choices": [type("C", (), {"message": type("M", (), {"content": "search"})()})()]})()
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.return_value = fake_resp
        result = _chat_completion("分析意图", max_tokens=10)
        assert result == "search"


# ===================== 意图识别降级 =====================

def test_recognize_intent_falls_back_when_llm_down():
    """LLM 不可用时走关键词规则识别意图"""
    state = {"message": "我想买件T恤"}
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("down")
        result = recognize_intent(state)
    assert result["intent"] == "search"


def test_recognize_intent_keyword_order():
    state = {"message": "我的订单到哪了"}
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("down")
        result = recognize_intent(state)
    assert result["intent"] == "order"


def test_recognize_intent_keyword_size():
    state = {"message": "身高175该穿什么尺码"}
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("down")
        result = recognize_intent(state)
    assert result["intent"] == "size"


def test_recognize_intent_unknown_falls_back_chat():
    state = {"message": "今天天气不错"}
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("down")
        result = recognize_intent(state)
    assert result["intent"] == "chat"


# ===================== 回复生成降级 =====================

def test_generate_reply_falls_back_when_llm_down():
    """LLM 不可用时必须产出基于规则的回复，绝不能 500"""
    state = {
        "intent": "search",
        "message": "T恤",
        "search_results": [],
        "search_total": 0,
        "recommendations": [],
        "order_info": {},
        "history": [],
        "slots": {},
    }
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.side_effect = Exception("down")
        result = generate_reply(state)
    assert result["reply"]
    assert "抱歉" in result["reply"] or "找" in result["reply"]


# ===================== 商品数据格式化 =====================

def test_format_products_handles_missing_keys():
    products = [{"id": 1, "name": "T恤"}, {"id": 2, "name": "牛仔裤", "price": 99.0}]
    text = _format_products_for_llm(products)
    assert "T恤" in text
    assert "99" in text


# ===================== 多轮槽位（D4） =====================

def test_size_waiting_keeps_question_when_llm_up():
    """缺身高/体重时，generate_reply 必须保留追问话术，不被 LLM 回复覆盖"""
    state = {
        "intent": "size",
        "message": "我想买T恤",
        "size_waiting": True,
        "history": [],
        "slots": {},
        "search_results": [],
        "recommendations": [],
        "order_info": {},
    }
    with patch("app.agent.nodes.llm_client") as mock:
        mock.chat.completions.create.return_value = type("R", (), {
            "choices": [type("C", (), {"message": type("M", (), {"content": "随便回一句"})()})()]})()
        result = generate_reply(state)
    assert "身高" in result["reply"] and "体重" in result["reply"]


def test_size_node_collects_slots_then_recommends():
    """先收身高体重（缺则追问），补齐后给出尺码"""
    from app.agent.nodes import size_node

    waiting = size_node({"message": "我身高175", "slots": {}, "intent": "size"})
    assert waiting["size_waiting"] is True
    assert waiting["slots"]["height"] == 175
    assert "体重" in waiting["reply"]

    done = size_node({"message": "体重70公斤", "slots": {"height": 175}, "intent": "size"})
    assert done["size_waiting"] is not True
    assert done["size_recommend"] in ("S", "M", "L", "XL")
    assert "175" in done["reply"]
