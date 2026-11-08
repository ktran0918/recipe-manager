from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"

    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "recipe_manager"
    postgres_user: str = "recipe_manager"
    postgres_password: str = "change-me"

    max_scrape_retries: int = 3
    scrape_timeout_seconds: int = 30

    queue_name: str = "scrape.jobs"
    dlx_name: str = "scrape.jobs.dlx"

    # Matches the OLLAMA_URL env var already defined in .env.example — host.docker.internal
    # in Docker, since Ollama runs on the host machine rather than in a container.
    ollama_url: str = "http://localhost:11434"
    ollama_parse_model: str = "qwen2.5:14b"

    anthropic_api_key: str = ""
    cloud_parse_model: str = "claude-haiku-4-5"

    ollama_embed_model: str = "nomic-embed-text"
    qdrant_url: str = "http://localhost:6333"
    elasticsearch_url: str = "http://localhost:9200"
    redis_url: str = "redis://localhost:6379/0"


settings = Settings()
