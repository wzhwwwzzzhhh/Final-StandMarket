import json

import redis.asyncio as aioredis

from app.config import settings

redis_client = aioredis.from_url(settings.redis_url, decode_responses=True)


async def get_history(session_id: str, max_turns: int = 10) -> list[dict]:
    key = f"agent:session:{session_id}"
    raw = await redis_client.lrange(key, 0, max_turns * 2 - 1)
    return [json.loads(item) for item in raw]


async def save_message(session_id: str, role: str, content: str):
    key = f"agent:session:{session_id}"
    msg = {"role": role, "content": content}
    await redis_client.lpush(key, json.dumps(msg, ensure_ascii=False))
    await redis_client.ltrim(key, 0, 19)


async def clear_history(session_id: str):
    await redis_client.delete(f"agent:session:{session_id}")
