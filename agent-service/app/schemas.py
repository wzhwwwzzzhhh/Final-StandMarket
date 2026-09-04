from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    userId: int = Field(gt=0, strict=True)
    sessionId: str = Field(pattern=r"^[A-Za-z0-9_-]{22,64}$")
    message: str = Field(min_length=1, max_length=2000)
    userAuthorization: str = Field(min_length=8, max_length=4096)

    @field_validator("message")
    @classmethod
    def validate_message(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("message cannot be empty")
        return value

    @field_validator("userAuthorization")
    @classmethod
    def validate_user_authorization(cls, value: str) -> str:
        if not value.startswith("Bearer ") or not value[7:].strip():
            raise ValueError("invalid delegated authorization")
        return value


class ProductItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: int = Field(gt=0, strict=True)
    name: str = Field(min_length=1)
    price: float = Field(ge=0, allow_inf_nan=False, strict=True)
    image: str = Field(min_length=1)
    description: str = ""

    @field_validator("name", "image")
    @classmethod
    def validate_non_blank_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("product text cannot be blank")
        return value


class DegradationReason(str, Enum):
    REDIS_UNAVAILABLE = "REDIS_UNAVAILABLE"
    ELASTICSEARCH_UNAVAILABLE = "ELASTICSEARCH_UNAVAILABLE"
    LLM_UNAVAILABLE = "LLM_UNAVAILABLE"
    JAVA_TOOL_UNAVAILABLE = "JAVA_TOOL_UNAVAILABLE"
    PYTHON_AGENT_UNAVAILABLE = "PYTHON_AGENT_UNAVAILABLE"


class ChatResponse(BaseModel):
    reply: str
    sessionId: str = Field(pattern=r"^[A-Za-z0-9_-]{22,64}$")
    products: list[ProductItem] = Field(default_factory=list)
    degraded: bool = False
    degradationReasons: list[DegradationReason] = Field(default_factory=list)

    @field_validator("reply")
    @classmethod
    def validate_reply(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("reply cannot be empty")
        return value

    @model_validator(mode="after")
    def validate_degradation_contract(self):
        if self.degraded != bool(self.degradationReasons):
            raise ValueError("degraded flag must match degradation reasons")
        return self
