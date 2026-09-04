"""Redis 对话记忆单元测试：顺序、TTL、结构化消息"""
import json
import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import redis_memory

# ===================== 顺序 =====================

def test_history_is_chronological():
    """lpush 写入后，get_history 必须返回时间正序（最早在前）"""
    import asyncio
    key = "agent:session:test-seq"
    msgs = [
        {"role": "user", "content": "u1"},
        {"role": "assistant", "content": "a1"},
        {"role": "user", "content": "u2"},
    ]
    # lpush 会把新消息放在头部，模拟 redis 返回：最新在前
    raw = [json.dumps(m, ensure_ascii=False) for m in reversed(msgs)]

    with patch.object(redis_memory.redis_client, "lrange", AsyncMock(return_value=raw)):
        history, degraded = asyncio.run(redis_memory.get_history(1, key))
    assert [m["content"] for m in history] == ["u1", "a1", "u2"]
    assert degraded is False


def test_get_history_calls_reverse():
    """get_history 必须对 lrange 结果做 reverse，保证时间正序"""
    key = "agent:session:test-rev"
    raw = [
        '{"role": "assistant", "content": "a"}',
        '{"role": "user", "content": "u"}',
    ]
    with patch.object(redis_memory.redis_client, "lrange", AsyncMock(return_value=raw)):
        import asyncio
        got, degraded = asyncio.run(redis_memory.get_history(1, key))
    assert got[0]["role"] == "user"
    assert got[1]["role"] == "assistant"
    assert degraded is False


# ===================== 结构化消息 =====================

def test_save_message_uses_structured_dict():
    with patch.object(redis_memory.redis_client, "eval", AsyncMock()) as ev:
        import asyncio
        asyncio.run(redis_memory.save_message(1, "s1", "user", "你好"))
        args, _ = ev.call_args
        stored = json.loads(args[3])
        assert stored["role"] == "user"
        assert stored["content"] == "你好"
        assert args[4] == redis_memory.settings.agent_session_ttl_seconds


# ===================== 槽位 / 物理信息提取（与 D2/D4 联动） =====================

def test_physical_info_extraction_in_message():
    from app.tools.size_recommend import get_physical_info
    info = get_physical_info("我身高170，体重65公斤")
    assert info["height"] == 170
    assert info["weight"] == 65
