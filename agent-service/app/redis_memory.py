import json

import redis.asyncio as aioredis

from app.config import settings

redis_client = aioredis.from_url(settings.redis_url, decode_responses=True)

SESSION_TTL_SECONDS = 7 * 24 * 3600  # 7 天


async def get_history(session_id: str, max_turns: int = 10) -> list[dict]:
    key = f"agent:session:{session_id}"
    raw = await redis_client.lrange(key, 0, max_turns * 2 - 1)
    # lpush 写入导致列表头为最新消息，此处 reverse 恢复时间正序
    return [json.loads(item) for item in reversed(raw)]


async def save_message(session_id: str, role: str, content: str):
    key = f"agent:session:{session_id}"
    msg = {"role": role, "content": content}
    await redis_client.lpush(key, json.dumps(msg, ensure_ascii=False))
    await redis_client.ltrim(key, 0, 19)
    await redis_client.expire(key, SESSION_TTL_SECONDS)


async def clear_history(session_id: str):
    await redis_client.delete(f"agent:session:{session_id}")


# ===================== 会话槽位 =====================

async def get_slots(session_id: str) -> dict:
    key = f"agent:slots:{session_id}"
    raw = await redis_client.get(key)
    if not raw:
        return {}
    return json.loads(raw)


async def save_slots(session_id: str, slots: dict):
    key = f"agent:slots:{session_id}"
    await redis_client.set(key, json.dumps(slots, ensure_ascii=False))
    await redis_client.expire(key, SESSION_TTL_SECONDS)


async def clear_slots(session_id: str):
    await redis_client.delete(f"agent:slots:{session_id}")
