import asyncio
import socket
import sys
import time
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import main
from app import redis_memory
from app.config import settings


SESSION_ID = "abcdefghijklmnopqrstuv"
PRIMARY_TOKEN = "a" * 32
SECONDARY_TOKEN = "b" * 32


def _valid_body(**overrides):
    body = {
        "userId": 9223372036854770000,
        "sessionId": SESSION_ID,
        "message": "hello",
        "userAuthorization": "Bearer delegated-user-jwt",
    }
    body.update(overrides)
    return body


def _healthy_result():
    return {
        "reply": "ok",
        "sessionId": SESSION_ID,
        "search_results": [],
        "recommendations": [],
        "slots": {},
        "degradationReasons": [],
    }


def _configure_business_mocks(monkeypatch):
    monkeypatch.setattr(main, "get_history", AsyncMock(return_value=([], False)))
    monkeypatch.setattr(main, "get_slots", AsyncMock(return_value=({}, False)))
    monkeypatch.setattr(main, "save_message", AsyncMock())
    monkeypatch.setattr(main, "save_slots", AsyncMock())
    graph = AsyncMock(return_value=_healthy_result())
    monkeypatch.setattr(main, "agent_graph", SimpleNamespace(ainvoke=graph))
    return graph


def test_health_is_public_and_does_not_disclose_configuration(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", "")
    response = TestClient(main.app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_chat_rejects_missing_or_wrong_internal_token_before_business(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    graph = _configure_business_mocks(monkeypatch)
    client = TestClient(main.app)

    assert client.post("/chat", json=_valid_body()).status_code == 401
    assert client.post(
        "/chat",
        headers={"X-FSM-Agent-Token": "wrong-token-that-must-never-work"},
        json=_valid_body(),
    ).status_code == 401

    graph.assert_not_awaited()
    main.get_history.assert_not_awaited()


def test_wrong_auth_wins_over_invalid_body_and_missing_config_is_fail_closed(monkeypatch):
    graph = _configure_business_mocks(monkeypatch)
    client = TestClient(main.app)
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    response = client.post(
        "/chat",
        headers={"X-FSM-Agent-Token": "wrong-token-that-must-never-work"},
        json={"message": "missing trusted identity"},
    )
    assert response.status_code == 401

    monkeypatch.setattr(settings, "agent_internal_tokens", "")
    response = client.post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )
    assert response.status_code == 503
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN + ",")
    response = client.post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )
    assert response.status_code == 503
    graph.assert_not_awaited()


def test_chat_accepts_both_rotation_tokens(monkeypatch):
    monkeypatch.setattr(
        settings,
        "agent_internal_tokens",
        f"{PRIMARY_TOKEN},{SECONDARY_TOKEN}",
    )
    graph = _configure_business_mocks(monkeypatch)
    client = TestClient(main.app)

    for token in (PRIMARY_TOKEN, SECONDARY_TOKEN):
        response = client.post(
            "/chat",
            headers={"X-FSM-Agent-Token": token},
            json=_valid_body(),
        )
        assert response.status_code == 200
        assert response.json() == {
            "reply": "ok",
            "sessionId": SESSION_ID,
            "products": [],
            "degraded": False,
            "degradationReasons": [],
        }

    assert graph.await_count == 2


def test_invalid_internal_request_stays_422_and_does_not_degrade(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    graph = _configure_business_mocks(monkeypatch)
    client = TestClient(main.app)

    invalid_bodies = [
        _valid_body(userId=0),
        _valid_body(sessionId="0123456789abcdef"),
        _valid_body(message="   "),
        _valid_body(userAuthorization="not-bearer"),
    ]
    for body in invalid_bodies:
        response = client.post(
            "/chat",
            headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
            json=body,
        )
        assert response.status_code == 422

    graph.assert_not_awaited()


def test_validation_error_does_not_echo_user_jwt_or_message(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    graph = _configure_business_mocks(monkeypatch)
    secret_jwt = "Bearer jwt-secret-marker"
    secret_message = "message-secret-marker"

    response = TestClient(main.app).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(
            message=secret_message + ("x" * 2000),
            userAuthorization="jwt-secret-marker-without-bearer",
        ),
    )

    assert response.status_code == 422
    assert response.json() == {"detail": "INVALID_REQUEST"}
    assert secret_jwt not in response.text
    assert secret_message not in response.text
    graph.assert_not_awaited()


def test_graph_failure_returns_stable_python_degradation(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    _configure_business_mocks(monkeypatch)
    main.agent_graph.ainvoke.side_effect = RuntimeError("secret external body")

    response = TestClient(main.app).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )

    assert response.status_code == 200
    assert response.json() == {
        "reply": "智能导购暂时不可用，请稍后再试。",
        "sessionId": SESSION_ID,
        "products": [],
        "degraded": True,
        "degradationReasons": ["PYTHON_AGENT_UNAVAILABLE"],
    }


def test_graph_failure_keeps_an_already_observed_redis_failure(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    _configure_business_mocks(monkeypatch)
    main.get_history.side_effect = ConnectionError("redis-secret")
    main.agent_graph.ainvoke.side_effect = RuntimeError("graph-secret")

    response = TestClient(main.app).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )

    assert response.status_code == 200
    assert response.json()["degradationReasons"] == [
        "REDIS_UNAVAILABLE",
        "PYTHON_AGENT_UNAVAILABLE",
    ]


def test_redis_failures_preserve_reply_and_add_one_reason(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    graph = _configure_business_mocks(monkeypatch)
    monkeypatch.setattr(
        main,
        "get_history",
        AsyncMock(side_effect=ConnectionError("redis-secret")),
    )
    main.save_message.side_effect = ConnectionError("redis-secret")

    response = TestClient(main.app).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )

    assert response.status_code == 200
    assert response.json()["reply"] == "ok"
    assert response.json()["degradationReasons"] == ["REDIS_UNAVAILABLE"]
    assert response.json()["degraded"] is True
    graph.assert_awaited_once()


def test_real_redis_connection_refusal_degrades_within_timeout_budget(monkeypatch):
    import redis.asyncio as aioredis

    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    unused_port = probe.getsockname()[1]
    probe.close()
    client = aioredis.from_url(
        f"redis://127.0.0.1:{unused_port}/15",
        decode_responses=True,
        socket_connect_timeout=0.2,
        socket_timeout=0.2,
    )
    monkeypatch.setattr(redis_memory, "redis_client", client)
    monkeypatch.setattr(main, "get_history", redis_memory.get_history)
    monkeypatch.setattr(main, "get_slots", redis_memory.get_slots)
    monkeypatch.setattr(main, "save_message", redis_memory.save_message)
    monkeypatch.setattr(main, "save_slots", redis_memory.save_slots)
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    graph = AsyncMock(return_value=_healthy_result())
    monkeypatch.setattr(main, "agent_graph", SimpleNamespace(ainvoke=graph))

    started = time.monotonic()
    try:
        response = TestClient(main.app).post(
            "/chat",
            headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
            json=_valid_body(),
        )
    finally:
        asyncio.run(client.aclose())

    assert time.monotonic() - started < 3
    assert response.status_code == 200
    assert response.json()["reply"] == "ok"
    assert response.json()["degradationReasons"] == ["REDIS_UNAVAILABLE"]


def test_bad_products_are_dropped_without_losing_valid_products(monkeypatch):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    _configure_business_mocks(monkeypatch)
    main.agent_graph.ainvoke.return_value = {
        **_healthy_result(),
        "search_results": [
            {"id": 1, "name": "T恤", "price": 99.0, "image": "ok.jpg"},
            {"id": "bad", "name": "坏商品", "price": 1, "image": "bad.jpg"},
            {"id": 3, "name": "缺图", "price": 5},
            {"id": 4, "name": "字符串价格", "price": "5", "image": "bad.jpg"},
            {"id": 5, "name": "   ", "price": 5.0, "image": "   "},
        ],
    }

    response = TestClient(main.app).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )

    assert response.status_code == 200
    assert response.json()["products"] == [
        {"id": 1, "name": "T恤", "price": 99.0, "image": "ok.jpg", "description": ""}
    ]


@pytest.mark.parametrize(
    "broken_fields",
    [
        {"reply": 123},
        {"degradationReasons": 123},
        {"search_results": 123},
    ],
)
def test_malformed_graph_post_processing_is_stable_degradation(monkeypatch, broken_fields):
    monkeypatch.setattr(settings, "agent_internal_tokens", PRIMARY_TOKEN)
    _configure_business_mocks(monkeypatch)
    main.agent_graph.ainvoke.return_value = {**_healthy_result(), **broken_fields}

    response = TestClient(main.app, raise_server_exceptions=False).post(
        "/chat",
        headers={"X-FSM-Agent-Token": PRIMARY_TOKEN},
        json=_valid_body(),
    )

    assert response.status_code == 200
    assert response.json() == {
        "reply": "智能导购暂时不可用，请稍后再试。",
        "sessionId": SESSION_ID,
        "products": [],
        "degraded": True,
        "degradationReasons": ["PYTHON_AGENT_UNAVAILABLE"],
    }
