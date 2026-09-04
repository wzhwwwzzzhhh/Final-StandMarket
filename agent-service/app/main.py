import hmac
import logging

import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.agent.graph import agent_graph, create_initial_state
from app.config import settings
from app.degradation import REASON_ORDER, normalize_reasons
from app.redis_memory import get_history, get_slots, save_message, save_slots
from app.schemas import ChatRequest, ChatResponse, ProductItem

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Shopping Agent", version="1.0.0")

_PYTHON_FALLBACK_REPLY = "智能导购暂时不可用，请稍后再试。"


@app.exception_handler(RequestValidationError)
async def handle_request_validation_error(_request: Request, _exc: RequestValidationError):
    return JSONResponse(status_code=422, content={"detail": "INVALID_REQUEST"})


@app.get("/health")
async def health():
    return {"status": "ok"}


async def require_internal_auth(
    x_fsm_agent_token: str | None = Header(default=None, alias="X-FSM-Agent-Token"),
):
    active_tokens = settings.active_agent_tokens()
    if not active_tokens:
        logger.error("Agent internal authentication is not configured")
        raise HTTPException(status_code=503, detail="service unavailable")
    supplied = x_fsm_agent_token or ""
    authenticated = False
    for active_token in active_tokens:
        authenticated |= hmac.compare_digest(supplied, active_token)
    if not authenticated:
        raise HTTPException(status_code=401, detail="unauthorized")


def _safe_products(raw_products) -> list[ProductItem]:
    products = []
    invalid_count = 0
    for item in raw_products or []:
        try:
            products.append(ProductItem.model_validate(item))
        except (ValidationError, TypeError, ValueError):
            invalid_count += 1
    if invalid_count:
        logger.warning("Agent response dropped invalid products count=%d", invalid_count)
    return products


async def _read_memory(req: ChatRequest, reasons: list[str]):
    history = []
    slots = {}
    try:
        history, history_degraded = await get_history(req.userId, req.sessionId)
        if history_degraded:
            reasons.append("REDIS_UNAVAILABLE")
    except Exception as exc:
        logger.warning("Redis history read failed exceptionType=%s", type(exc).__name__)
        reasons.append("REDIS_UNAVAILABLE")
    try:
        slots, slots_degraded = await get_slots(req.userId, req.sessionId)
        if slots_degraded:
            reasons.append("REDIS_UNAVAILABLE")
    except Exception as exc:
        logger.warning("Redis slots read failed exceptionType=%s", type(exc).__name__)
        reasons.append("REDIS_UNAVAILABLE")
    return history, slots


async def _save_memory(req: ChatRequest, result: dict, reasons: list[str]):
    write_factories = [
        lambda: save_message(req.userId, req.sessionId, "user", req.message),
        lambda: save_message(req.userId, req.sessionId, "assistant", result["reply"]),
    ]
    if result.get("slots"):
        write_factories.append(lambda: save_slots(req.userId, req.sessionId, result["slots"]))
    for create_write in write_factories:
        try:
            await create_write()
        except Exception as exc:
            logger.warning("Redis memory write failed exceptionType=%s", type(exc).__name__)
            reasons.append("REDIS_UNAVAILABLE")


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, _auth=Depends(require_internal_auth)):
    reasons: list[str] = []
    history, slots = await _read_memory(req, reasons)

    initial_state = create_initial_state(
        message=req.message,
        user_id=req.userId,
        session_id=req.sessionId,
        history=history,
        user_authorization=req.userAuthorization,
        slots=slots,
        degradation_reasons=normalize_reasons(reasons),
    )
    try:
        result = await agent_graph.ainvoke(initial_state)
        if not isinstance(result, dict):
            raise ValueError("invalid graph response")
        reply = result.get("reply")
        if not isinstance(reply, str) or not reply.strip():
            raise ValueError("invalid graph reply")
        graph_reasons = result.get("degradationReasons") or []
        if not isinstance(graph_reasons, list) or any(reason not in REASON_ORDER for reason in graph_reasons):
            raise ValueError("invalid graph degradation reasons")
        reasons.extend(graph_reasons)
        slots_result = result.get("slots") or {}
        if not isinstance(slots_result, dict):
            raise ValueError("invalid graph slots")
        raw_products = result.get("search_results") or result.get("recommendations") or []
        if not isinstance(raw_products, list):
            raise ValueError("invalid graph products")
        products = _safe_products(raw_products)
        await _save_memory(req, result, reasons)
        reasons = normalize_reasons(reasons)
        return ChatResponse(
            reply=reply,
            sessionId=req.sessionId,
            products=products,
            degraded=bool(reasons),
            degradationReasons=reasons,
        )
    except Exception as exc:
        logger.warning("Agent processing failed exceptionType=%s sessionId=%s", type(exc).__name__, req.sessionId)
        fallback_reasons = normalize_reasons([*reasons, "PYTHON_AGENT_UNAVAILABLE"])
        return ChatResponse(
            reply=_PYTHON_FALLBACK_REPLY,
            sessionId=req.sessionId,
            products=[],
            degraded=True,
            degradationReasons=fallback_reasons,
        )


if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
