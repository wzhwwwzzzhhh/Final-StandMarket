from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    openai_api_key: str = "your-api-key"
    openai_base_url: str = "https://api.openai.com/v1"
    model_name: str = "gpt-3.5-turbo"
    redis_url: str = "redis://localhost:6379/0"
    es_host: str = "http://localhost:9200"
    backend_base_url: str = "http://localhost:8080"
    host: str = "0.0.0.0"
    port: int = 8000

    model_config = {"protected_namespaces": ("settings_",)}


settings = Settings()
