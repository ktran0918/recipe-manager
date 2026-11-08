import json
from unittest.mock import AsyncMock
from uuid import uuid4

from scraper.notifications import publish_job_complete


async def test_publish_job_complete_publishes_to_correct_channel(monkeypatch):
    job_id, recipe_id = uuid4(), uuid4()
    fake_client = AsyncMock()
    monkeypatch.setattr("scraper.notifications.redis.Redis.from_url", lambda url: fake_client)

    await publish_job_complete(job_id, recipe_id)

    fake_client.publish.assert_awaited_once()
    channel, message = fake_client.publish.call_args.args
    assert channel == f"job.status.{job_id}"
    assert json.loads(message) == {"status": "complete", "recipe_id": str(recipe_id)}
    fake_client.aclose.assert_awaited_once()
