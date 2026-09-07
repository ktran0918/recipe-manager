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


settings = Settings()
