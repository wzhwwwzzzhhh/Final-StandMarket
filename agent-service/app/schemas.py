from pydantic import BaseModel
from typing import Optional


class ChatRequest(BaseModel):
    userId: int
    sessionId: Optional[str] = None
    message: str
    token: Optional[str] = None


class ProductItem(BaseModel):
    id: int
    name: str
    price: float
    image: str
    description: str


class ChatResponse(BaseModel):
    reply: str
    sessionId: str
    products: list[ProductItem] = []
