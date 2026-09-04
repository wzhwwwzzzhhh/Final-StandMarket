import asyncio
import json
import os
import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import redis_memory


SESSION_ID = "abcdefghijklmnopqrstuv"


def test_redis_client_has_finite_connect_and_read_timeouts():
    options = redis_memory.redis_client.connection_pool.connection_kwargs
    assert 0 < options["socket_connect_timeout"] <= 2
    assert 0 < options["socket_timeout"] <= 3


def test_history_key_is_namespaced_by_long_user_and_session():
    assert redis_memory.history_key(9223372036854770000, SESSION_ID) == (
        f"agent:user:9223372036854770000:session:{SESSION_ID}:history"
    )
    assert redis_memory.history_key(1, SESSION_ID) != redis_memory.history_key(2, SESSION_ID)


def test_slots_key_is_namespaced_and_never_uses_legacy_key():
    key = redis_memory.slots_key(7, SESSION_ID)
    assert key == f"agent:user:7:session:{SESSION_ID}:slots"
    assert key != f"agent:slots:{SESSION_ID}"


def test_save_message_uses_one_atomic_eval_with_ttl():
    client = AsyncMock()
    with patch.object(redis_memory, "redis_client", client):
        asyncio.run(redis_memory.save_message(7, SESSION_ID, "user", "hello"))

    client.eval.assert_awaited_once()
    args = client.eval.await_args.args
    assert args[1] == 1
    assert args[2] == redis_memory.history_key(7, SESSION_ID)
    assert args[4] == redis_memory.settings.agent_session_ttl_seconds
    client.expire.assert_not_called()


def test_save_slots_uses_single_set_ex():
    client = AsyncMock()
    with patch.object(redis_memory, "redis_client", client):
        asyncio.run(redis_memory.save_slots(7, SESSION_ID, {"height": 170}))

    client.set.assert_awaited_once_with(
        redis_memory.slots_key(7, SESSION_ID),
        json.dumps({"height": 170}, ensure_ascii=False),
        ex=redis_memory.settings.agent_session_ttl_seconds,
    )
    client.expire.assert_not_called()


def test_history_drops_bad_json_and_reports_degradation():
    client = AsyncMock()
    client.lrange.return_value = [
        json.dumps({"role": "assistant", "content": "ok"}),
        "not-json-secret",
        json.dumps({"role": "user", "content": "hello"}),
    ]
    with patch.object(redis_memory, "redis_client", client):
        history, degraded = asyncio.run(redis_memory.get_history(7, SESSION_ID))

    assert [item["content"] for item in history] == ["hello", "ok"]
    assert degraded is True


def test_clear_only_deletes_exact_user_session_keys():
    client = AsyncMock()
    with patch.object(redis_memory, "redis_client", client):
        asyncio.run(redis_memory.clear_history(7, SESSION_ID))
        asyncio.run(redis_memory.clear_slots(7, SESSION_ID))

    assert client.delete.await_args_list[0].args == (redis_memory.history_key(7, SESSION_ID),)
    assert client.delete.await_args_list[1].args == (redis_memory.slots_key(7, SESSION_ID),)


@pytest.mark.skipif(
    not os.getenv("B9_REAL_REDIS_URL"),
    reason="B9_REAL_REDIS_URL is required for the separately mandated Redis 7 evidence run",
)
def test_real_redis_user_isolation_ttl_and_legacy_sentinel(monkeypatch):
    import redis.asyncio as aioredis

    async def scenario():
        client = aioredis.from_url(os.environ["B9_REAL_REDIS_URL"], decode_responses=True)
        monkeypatch.setattr(redis_memory, "redis_client", client)
        suffix = os.urandom(8).hex()
        session_id = f"b9_{suffix}_abcdefghijkl"
        legacy_key = f"agent:session:{session_id}"
        server_info = await client.info("server")
        assert server_info["redis_version"].split(".", 1)[0] == "7"
        await client.set(legacy_key, "legacy-sentinel", ex=60)
        try:
            await redis_memory.save_message(101, session_id, "user", "user-a")
            await redis_memory.save_message(202, session_id, "user", "user-b")
            await redis_memory.save_slots(101, session_id, {"height": 170})

            history_a, _ = await redis_memory.get_history(101, session_id)
            history_b, _ = await redis_memory.get_history(202, session_id)
            assert [item["content"] for item in history_a] == ["user-a"]
            assert [item["content"] for item in history_b] == ["user-b"]
            assert await client.ttl(redis_memory.history_key(101, session_id)) > 0
            assert await client.ttl(redis_memory.slots_key(101, session_id)) > 0
            assert await client.get(legacy_key) == "legacy-sentinel"

            await redis_memory.clear_history(101, session_id)
            assert await client.exists(redis_memory.history_key(101, session_id)) == 0
            assert await client.exists(redis_memory.history_key(202, session_id)) == 1
        finally:
            await client.delete(
                legacy_key,
                redis_memory.history_key(101, session_id),
                redis_memory.history_key(202, session_id),
                redis_memory.slots_key(101, session_id),
            )
            await client.aclose()

    asyncio.run(scenario())
