import uuid

import uvicorn
from fastapi import FastAPI, HTTPException

from app.config import settings
from app.schemas import ChatRequest, ChatResponse, ProductItem
from app.agent.graph import create_initial_state, agent_graph
from app.redis_memory import get_history, save_message

app = FastAPI(title="AI Shopping Agent", version="1.0.0")


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=422, detail="message cannot be empty")

    session_id = req.sessionId or uuid.uuid4().hex[:16]
    history = await get_history(session_id)

    initial_state = create_initial_state(
        message=req.message,
        user_id=req.userId,
        session_id=session_id,
        history=history,
    )
    result = await agent_graph.ainvoke(initial_state)

    await save_message(session_id, "user", req.message)
    await save_message(session_id, "assistant", result["reply"])

    products = result.get("search_results") or result.get("recommendations") or []
    return ChatResponse(
        reply=result["reply"],
        sessionId=session_id,
        products=[ProductItem(**p) for p in products if isinstance(p, dict)],
    )


if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
