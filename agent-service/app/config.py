from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    openai_api_key: str = "your-api-key"
    openai_base_url: str = "https://api.openai.com/v1"
    model_name: str = "gpt-3.5-turbo"
    redis_url: str = "redis://localhost:6379/0"
    redis_connect_timeout_seconds: float = Field(default=1.0, gt=0, le=10)
    redis_socket_timeout_seconds: float = Field(default=2.0, gt=0, le=10)
    es_host: str = "http://localhost:9200"
    backend_base_url: str = "http://localhost:8080"
    agent_internal_tokens: str = ""
    agent_session_ttl_seconds: int = Field(default=7 * 24 * 3600, gt=0, le=365 * 24 * 3600)
    host: str = "0.0.0.0"
    port: int = 8000

    model_config = {"protected_namespaces": ("settings_",), "env_file": ".env"}

    def active_agent_tokens(self) -> tuple[str, ...]:
        raw_tokens = self.agent_internal_tokens.split(",")
        if any(not item or item != item.strip() for item in raw_tokens):
            return ()
        tokens = tuple(raw_tokens)
        if len(tokens) > 2 or any(len(item) < 32 or any(ch.isspace() for ch in item) for item in tokens):
            return ()
        if len(set(tokens)) != len(tokens):
            return ()
        return tokens


settings = Settings()
