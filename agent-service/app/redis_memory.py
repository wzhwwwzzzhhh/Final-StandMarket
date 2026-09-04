import json
import logging

import redis.asyncio as aioredis

from app.config import settings

logger = logging.getLogger(__name__)

redis_client = aioredis.from_url(
    settings.redis_url,
    decode_responses=True,
    socket_connect_timeout=settings.redis_connect_timeout_seconds,
    socket_timeout=settings.redis_socket_timeout_seconds,
)

_SAVE_HISTORY_LUA = """
redis.call('LPUSH', KEYS[1], ARGV[1])
redis.call('LTRIM', KEYS[1], 0, 19)
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1
"""


def history_key(user_id: int, session_id: str) -> str:
    return f"agent:user:{user_id}:session:{session_id}:history"


def slots_key(user_id: int, session_id: str) -> str:
    return f"agent:user:{user_id}:session:{session_id}:slots"


async def get_history(user_id: int, session_id: str, max_turns: int = 10) -> tuple[list[dict], bool]:
    raw = await redis_client.lrange(history_key(user_id, session_id), 0, max_turns * 2 - 1)
    messages = []
    invalid_count = 0
    for item in reversed(raw):
        try:
            decoded = json.loads(item)
            if not isinstance(decoded, dict) or decoded.get("role") not in ("user", "assistant"):
                raise ValueError("invalid history item")
            messages.append(decoded)
        except (TypeError, ValueError, json.JSONDecodeError):
            invalid_count += 1
    if invalid_count:
        logger.warning("Redis history contained invalid entries count=%d", invalid_count)
    return messages, invalid_count > 0


async def save_message(user_id: int, session_id: str, role: str, content: str):
    key = history_key(user_id, session_id)
    message = json.dumps({"role": role, "content": content}, ensure_ascii=False)
    await redis_client.eval(
        _SAVE_HISTORY_LUA,
        1,
        key,
        message,
        settings.agent_session_ttl_seconds,
    )


async def clear_history(user_id: int, session_id: str):
    await redis_client.delete(history_key(user_id, session_id))


async def get_slots(user_id: int, session_id: str) -> tuple[dict, bool]:
    raw = await redis_client.get(slots_key(user_id, session_id))
    if not raw:
        return {}, False
    try:
        decoded = json.loads(raw)
        if not isinstance(decoded, dict):
            raise ValueError("invalid slots")
        return decoded, False
    except (TypeError, ValueError, json.JSONDecodeError):
        logger.warning("Redis slots contained invalid data")
        return {}, True


async def save_slots(user_id: int, session_id: str, slots: dict):
    await redis_client.set(
        slots_key(user_id, session_id),
        json.dumps(slots, ensure_ascii=False),
        ex=settings.agent_session_ttl_seconds,
    )


async def clear_slots(user_id: int, session_id: str):
    await redis_client.delete(slots_key(user_id, session_id))
