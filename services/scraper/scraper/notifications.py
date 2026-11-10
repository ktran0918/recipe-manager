import json
from uuid import UUID

import redis.asyncio as redis

from scraper.config import settings


async def publish_job_complete(job_id: UUID, recipe_id: UUID) -> None:
    client = redis.Redis.from_url(settings.redis_url)
    try:
        await client.publish(
            f"job.status.{job_id}",
            json.dumps({"status": "complete", "recipe_id": str(recipe_id)}),
        )
    finally:
        await client.aclose()


async def publish_job_failed(job_id: UUID, error: str) -> None:
    client = redis.Redis.from_url(settings.redis_url)
    try:
        await client.publish(
            f"job.status.{job_id}",
            json.dumps({"status": "failed", "error": error}),
        )
    finally:
        await client.aclose()
