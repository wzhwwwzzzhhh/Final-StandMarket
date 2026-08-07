"""搭配推荐 / 订单格式化 / 回复格式化工具单元测试"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.tools.recommend import (
    detect_category,
    complementary_categories,
    build_es_category_query,
)
from app.tools.format import format_orders, format_order_summary
from app.tools.outfit_rules import rule_based_reply

# ===================== 搭配规则 =====================

def test_detect_category_t_shirt():
    assert "T恤" in detect_category("纯棉T恤")


def test_detect_category_jeans():
    assert "下装" in detect_category("蓝色牛仔裤") or "裤" in detect_category("蓝色牛仔裤")


def test_detect_category_dress():
    assert "连衣裙" in detect_category("碎花连衣裙")


def test_complementary_categories_t_shirt_has_bottoms():
    comps = complementary_categories("T恤")
    assert any(k in comps for k in ["下装", "裤", "裙", "牛仔裤"])


def test_build_es_query_contains_keywords():
    body = build_es_category_query("T恤", size=3)
    bool_q = body["query"]["bool"]
    clauses = bool_q.get("should", []) + bool_q.get("must", [])
    # 至少有一个子句是多字段匹配
    assert any("multi_match" in str(c) for c in clauses)


# ===================== 订单格式化 =====================

def test_format_orders_handles_empty():
    assert format_orders([]) == "你目前还没有订单。"


def test_format_orders_shows_fields():
    orders = [{
        "number": "SN20260101001",
        "status": 2,
        "amount": 199.0,
        "orderTime": "2026-01-01 10:00:00",
        "items": [{"name": "纯棉T恤", "number": 2}],
    }]
    text = format_orders(orders)
    assert "SN20260101001" in text
    assert "待发货" in text
    assert "199" in text
    assert "纯棉T恤" in text


def test_order_status_map():
    from app.tools.format import ORDER_STATUS
    assert ORDER_STATUS[1] == "待付款"
    assert ORDER_STATUS[3] == "已发货"
    assert ORDER_STATUS[5] == "已取消"


def test_format_order_summary_business_friendly():
    text = format_order_summary({"error": "查询失败"})
    assert "查询" in text


# ===================== 规则兜底回复 =====================

def test_rule_based_reply_order_intent():
    reply = rule_based_reply(intent="order", order_text="订单号SN123 待发货")
    assert "订单" in reply or "SN123" in reply


def test_rule_based_reply_search_without_results():
    reply = rule_based_reply(intent="search", search_total=0)
    assert reply  # 非空且友好
