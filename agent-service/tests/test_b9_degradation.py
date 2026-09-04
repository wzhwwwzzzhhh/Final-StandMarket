import logging
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.agent import nodes
from app.agent.graph import create_initial_state
from app.tools import search_product


def test_initial_state_has_stable_empty_degradation_reasons():
    state = create_initial_state("hello", 1, "abcdefghijklmnopqrstuv")
    assert state["degradationReasons"] == []


def test_llm_failure_is_marked_once_across_intent_and_reply():
    state = create_initial_state("hello", 1, "abcdefghijklmnopqrstuv")
    with patch.object(nodes.llm_client.chat.completions, "create", side_effect=TimeoutError("secret")):
        state = nodes.recognize_intent(state)
        state = nodes.generate_reply(state)

    assert state["reply"]
    assert state["degradationReasons"] == ["LLM_UNAVAILABLE"]


def test_intent_llm_failure_forces_rule_reply_without_second_llm_attempt():
    state = create_initial_state("你好", 1, "abcdefghijklmnopqrstuv")
    with patch.object(nodes, "_chat_completion", side_effect=[None, "must-not-be-used"]) as completion:
        state = nodes.recognize_intent(state)
        state = nodes.generate_reply(state)

    assert state["reply"] != "must-not-be-used"
    assert state["degradationReasons"] == ["LLM_UNAVAILABLE"]
    assert completion.call_count == 1


def test_search_and_recommend_failures_mark_elasticsearch():
    search_state = create_initial_state("T恤", 1, "abcdefghijklmnopqrstuv")
    with patch.object(nodes, "search_products", return_value={"hits": [], "total": 0, "error": True}):
        result = nodes.search_product_node(search_state)
    assert result["degradationReasons"] == ["ELASTICSEARCH_UNAVAILABLE"]


def test_elasticsearch_malformed_success_payload_is_classified_as_es_failure():
    with patch.object(search_product.es, "search", return_value={"unexpected": "schema"}):
        result = search_product.search_products("T恤")

    assert result["error"] is True
    assert result["hits"] == []
    assert result["total"] == 0


def test_search_and_recommend_drop_only_bad_hits_and_keep_valid_products():
    valid_source = {"id": 1, "name": "T恤", "price": 99.0, "image": "ok.jpg"}
    mixed_response = {
        "hits": {
            "total": {"value": 2},
            "hits": [
                {"bad": "hit"},
                {"_source": valid_source, "_score": 1.0},
            ],
        }
    }
    with patch.object(search_product.es, "search", return_value=mixed_response):
        search_result = search_product.search_products("T恤")
    with patch.object(nodes.recommend_outfit.__globals__["es"], "search", return_value=mixed_response):
        recommend_result = nodes.recommend_outfit("穿搭")

    assert [item["id"] for item in search_result["hits"]] == [1]
    assert search_result.get("error") is not True
    assert [item["id"] for item in recommend_result["products"]] == [1]
    assert recommend_result["error"] is False


def test_search_and_recommend_failures_force_safe_reply_without_llm():
    for intent, node, dependency, payload in [
        (
            "search",
            nodes.search_product_node,
            "search_products",
            {"hits": [], "total": 0, "error": True},
        ),
        (
            "recommend",
            nodes.recommend_node,
            "recommend_outfit",
            {"products": [], "main_category": "", "reason": "", "error": True},
        ),
    ]:
        state = create_initial_state("商品", 1, "abcdefghijklmnopqrstuv")
        state["intent"] = intent
        with patch.object(nodes, dependency, return_value=payload), patch.object(
            nodes, "_chat_completion", return_value="must-not-be-used"
        ) as completion:
            state = node(state)
            state = nodes.generate_reply(state)

        assert state["reply"] == "商品检索暂不可用，请稍后再试。"
        completion.assert_not_called()

    recommend_state = create_initial_state("穿搭", 1, "abcdefghijklmnopqrstuv")
    with patch.object(
        nodes,
        "recommend_outfit",
        return_value={"products": [], "main_category": "上装", "reason": "", "error": True},
    ):
        result = nodes.recommend_node(recommend_state)
    assert result["degradationReasons"] == ["ELASTICSEARCH_UNAVAILABLE"]


def test_java_tool_failure_is_marked_and_uses_raw_delegated_bearer():
    state = create_initial_state(
        "订单",
        1,
        "abcdefghijklmnopqrstuv",
        user_authorization="Bearer delegated-user-jwt",
    )
    with patch.object(nodes, "_fetch_orders", return_value={"error": True}) as fetch:
        result = nodes.order_node(state)

    fetch.assert_called_once_with("Bearer delegated-user-jwt")
    assert result["degradationReasons"] == ["JAVA_TOOL_UNAVAILABLE"]


def test_order_and_tracking_failures_force_safe_reply_without_llm():
    failure_scenarios = [
        ({"error": True}, None),
        ({"orders": [{"id": 9, "status": 2}]}, {"error": True}),
    ]
    for orders_result, tracking_result in failure_scenarios:
        state = create_initial_state(
            "订单物流",
            1,
            "abcdefghijklmnopqrstuv",
            user_authorization="Bearer delegated-user-jwt",
        )
        state["intent"] = "order"
        with patch.object(nodes, "_fetch_orders", return_value=orders_result), patch.object(
            nodes, "_fetch_tracking", return_value=tracking_result
        ), patch.object(nodes, "_chat_completion", return_value="must-not-be-used") as completion:
            state = nodes.order_node(state)
            state = nodes.generate_reply(state)

        assert state["reply"] == "订单服务暂不可用，请稍后再试。"
        assert state["degradationReasons"] == ["JAVA_TOOL_UNAVAILABLE"]
        completion.assert_not_called()


def test_java_tool_rejects_non_success_or_malformed_result_payloads():
    with patch.object(nodes.httpx, "get") as get:
        get.return_value.status_code = 200
        get.return_value.json.return_value = {"code": 0, "data": [{"id": 1}]}
        assert nodes._fetch_orders("Bearer delegated-user-jwt") == {"error": True}

        get.return_value.json.return_value = {"code": 1, "data": {"not": "a list"}}
        assert nodes._fetch_orders("Bearer delegated-user-jwt") == {"error": True}

        get.return_value.json.return_value = {"code": 1, "data": ["not-an-order"]}
        assert nodes._fetch_orders("Bearer delegated-user-jwt") == {"error": True}


def test_dependency_logs_do_not_include_exception_or_message_secrets(caplog):
    caplog.set_level(logging.WARNING)
    with patch.object(
        nodes.llm_client.chat.completions,
        "create",
        side_effect=RuntimeError("message-body user-jwt internal-token"),
    ):
        assert nodes._chat_completion([{"role": "user", "content": "message-body"}]) is None

    logs = caplog.text
    assert "RuntimeError" in logs
    assert "message-body" not in logs
    assert "user-jwt" not in logs
    assert "internal-token" not in logs
