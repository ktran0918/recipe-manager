import json
from unittest.mock import AsyncMock
from uuid import uuid4

from scraper.consumer import RETRY_HEADER, handle_message


class _NullContext:
    async def __aenter__(self) -> None:
        return None

    async def __aexit__(self, *exc: object) -> bool:
        return False


class FakeMessage:
    def __init__(self, body: bytes, headers: dict[str, object] | None = None):
        self.body = body
        self.headers = headers or {}
        self.routing_key = "scrape.jobs"
        self.content_type = "application/json"
        self.ack = AsyncMock()
        self.nack = AsyncMock()

    def process(self, ignore_processed: bool = True) -> _NullContext:
        return _NullContext()


def _fixture_body(job_id: object, household_id: object, url: str = "https://example.com/garlic-pasta") -> bytes:
    return json.dumps({"job_id": str(job_id), "url": url, "household_id": str(household_id)}).encode()


async def test_handle_message_marks_scraping_and_acks(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id))
    pool = AsyncMock()
    channel = AsyncMock()

    mark_scraping = AsyncMock()
    monkeypatch.setattr("scraper.consumer.mark_scraping", mark_scraping)

    await handle_message(channel, message, pool)

    mark_scraping.assert_awaited_once_with(pool, job_id)
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()


async def test_handle_message_malformed_body_deadletters_without_retry():
    message = FakeMessage(b"not valid json")
    pool = AsyncMock()
    channel = AsyncMock()

    await handle_message(channel, message, pool)

    message.nack.assert_awaited_once_with(requeue=False)
    message.ack.assert_not_called()


async def test_handle_message_db_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock(side_effect=RuntimeError("db down")))

    await handle_message(channel, message, pool)

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()  # original removed after republish, not requeued in place
    message.nack.assert_not_called()


async def test_handle_message_exhausted_retries_marks_failed_and_deadletters(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 3})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock(side_effect=RuntimeError("db down")))
    mark_failed = AsyncMock()
    monkeypatch.setattr("scraper.consumer.mark_failed", mark_failed)

    await handle_message(channel, message, pool)

    mark_failed.assert_awaited_once_with(pool, job_id, "db down")
    message.nack.assert_awaited_once_with(requeue=False)
    channel.default_exchange.publish.assert_not_called()
