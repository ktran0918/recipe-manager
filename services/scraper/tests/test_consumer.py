import json
from unittest.mock import AsyncMock
from uuid import uuid4

from scraper.consumer import RETRY_HEADER, handle_message
from scraper.errors import FetchError, ParseError
from scraper.indexing import RecipeIndexDocument
from scraper.recipe_schema import ParsedIngredient, ParsedRecipe, ParsedStep


def _fixture_parsed_recipe() -> ParsedRecipe:
    return ParsedRecipe(
        title="Garlic Butter Pasta",
        servings=4,
        ingredients=[ParsedIngredient(raw_text="8 oz spaghetti", name="spaghetti", quantity=8, unit="oz")],
        steps=[ParsedStep(step_number=1, instruction="Boil pasta.")],
    )


def _fixture_index_document(recipe_id: object, household_id: object) -> RecipeIndexDocument:
    return RecipeIndexDocument(
        recipe_id=recipe_id,
        household_id=household_id,
        title="Garlic Butter Pasta",
        description=None,
        complexity=None,
        cook_time_minutes=20,
        occasions=None,
        cuisine=None,
        diet_tags=None,
        created_at="2026-01-01T00:00:00Z",
        ingredient_names=["spaghetti"],
    )


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
    user_id, recipe_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id))
    pool = AsyncMock()
    channel = AsyncMock()
    qdrant_client = AsyncMock()
    es_client = AsyncMock()

    mark_scraping = AsyncMock()
    mark_parsing = AsyncMock()
    mark_embedding = AsyncMock()
    mark_complete = AsyncMock()
    monkeypatch.setattr("scraper.consumer.mark_scraping", mark_scraping)
    monkeypatch.setattr("scraper.consumer.mark_parsing", mark_parsing)
    monkeypatch.setattr("scraper.consumer.mark_embedding", mark_embedding)
    monkeypatch.setattr("scraper.consumer.mark_complete", mark_complete)
    # fetch_html/extract_article_text/parse_recipe/get_job_creator/write_recipe/
    # load_recipe_for_indexing/embed_recipe/upsert_qdrant_point/
    # index_elasticsearch_document/publish_job_complete are real network + LLM + DB calls
    # in production — stub all of them so this test exercises only the consumer's own
    # lifecycle logic.
    monkeypatch.setattr("scraper.consumer.fetch_html", AsyncMock(return_value="<html>fetched</html>"))
    monkeypatch.setattr("scraper.consumer.extract_article_text", lambda html: "extracted article text")
    monkeypatch.setattr("scraper.consumer.parse_recipe", AsyncMock(return_value=_fixture_parsed_recipe()))
    monkeypatch.setattr("scraper.consumer.get_job_creator", AsyncMock(return_value=user_id))
    monkeypatch.setattr("scraper.consumer.write_recipe", AsyncMock(return_value=recipe_id))
    monkeypatch.setattr(
        "scraper.consumer.load_recipe_for_indexing",
        AsyncMock(return_value=_fixture_index_document(recipe_id, household_id)),
    )
    monkeypatch.setattr("scraper.consumer.embed_recipe", AsyncMock(return_value=[0.1, 0.2, 0.3]))
    upsert_qdrant_point = AsyncMock()
    index_elasticsearch_document = AsyncMock()
    publish_job_complete = AsyncMock()
    monkeypatch.setattr("scraper.consumer.upsert_qdrant_point", upsert_qdrant_point)
    monkeypatch.setattr("scraper.consumer.index_elasticsearch_document", index_elasticsearch_document)
    monkeypatch.setattr("scraper.consumer.publish_job_complete", publish_job_complete)

    await handle_message(channel, message, pool, qdrant_client, es_client)

    mark_scraping.assert_awaited_once_with(pool, job_id)
    mark_parsing.assert_awaited_once_with(pool, job_id)
    mark_embedding.assert_awaited_once_with(pool, job_id, recipe_id)
    upsert_qdrant_point.assert_awaited_once()
    index_elasticsearch_document.assert_awaited_once()
    mark_complete.assert_awaited_once_with(pool, job_id)
    publish_job_complete.assert_awaited_once_with(job_id, recipe_id)
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()
    channel.default_exchange.publish.assert_not_called()


async def test_handle_message_malformed_body_deadletters_without_retry():
    message = FakeMessage(b"not valid json")
    pool = AsyncMock()
    channel = AsyncMock()

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    message.nack.assert_awaited_once_with(requeue=False)
    message.ack.assert_not_called()


async def test_handle_message_db_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock(side_effect=RuntimeError("db down")))

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()  # original removed after republish, not requeued in place
    message.nack.assert_not_called()


async def test_handle_message_fetch_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock())
    monkeypatch.setattr("scraper.consumer.fetch_html", AsyncMock(side_effect=FetchError("site unreachable")))

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()


async def test_handle_message_parse_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock())
    monkeypatch.setattr("scraper.consumer.fetch_html", AsyncMock(return_value="<html>fetched</html>"))
    monkeypatch.setattr("scraper.consumer.extract_article_text", lambda html: "extracted article text")
    monkeypatch.setattr("scraper.consumer.mark_parsing", AsyncMock())
    monkeypatch.setattr(
        "scraper.consumer.parse_recipe",
        AsyncMock(side_effect=ParseError("failed Guardrails validation on both tiers")),
    )

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()


async def test_handle_message_write_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock())
    monkeypatch.setattr("scraper.consumer.fetch_html", AsyncMock(return_value="<html>fetched</html>"))
    monkeypatch.setattr("scraper.consumer.extract_article_text", lambda html: "extracted article text")
    monkeypatch.setattr("scraper.consumer.mark_parsing", AsyncMock())
    monkeypatch.setattr("scraper.consumer.parse_recipe", AsyncMock(return_value=_fixture_parsed_recipe()))
    monkeypatch.setattr("scraper.consumer.get_job_creator", AsyncMock(return_value=uuid4()))
    monkeypatch.setattr(
        "scraper.consumer.write_recipe", AsyncMock(side_effect=RuntimeError("unique constraint violation"))
    )

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()


async def test_handle_message_indexing_failure_requeues_with_incremented_retry_header(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    recipe_id = uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 0})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock())
    monkeypatch.setattr("scraper.consumer.fetch_html", AsyncMock(return_value="<html>fetched</html>"))
    monkeypatch.setattr("scraper.consumer.extract_article_text", lambda html: "extracted article text")
    monkeypatch.setattr("scraper.consumer.mark_parsing", AsyncMock())
    monkeypatch.setattr("scraper.consumer.parse_recipe", AsyncMock(return_value=_fixture_parsed_recipe()))
    monkeypatch.setattr("scraper.consumer.get_job_creator", AsyncMock(return_value=uuid4()))
    monkeypatch.setattr("scraper.consumer.write_recipe", AsyncMock(return_value=recipe_id))
    monkeypatch.setattr("scraper.consumer.mark_embedding", AsyncMock())
    monkeypatch.setattr(
        "scraper.consumer.load_recipe_for_indexing",
        AsyncMock(side_effect=RuntimeError("Ollama unreachable")),
    )

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    channel.default_exchange.publish.assert_awaited_once()
    published_message = channel.default_exchange.publish.call_args.args[0]
    assert published_message.headers[RETRY_HEADER] == 1
    message.ack.assert_awaited_once()
    message.nack.assert_not_called()


async def test_handle_message_exhausted_retries_marks_failed_and_deadletters(monkeypatch):
    job_id, household_id = uuid4(), uuid4()
    message = FakeMessage(_fixture_body(job_id, household_id), headers={RETRY_HEADER: 3})
    pool = AsyncMock()
    channel = AsyncMock()

    monkeypatch.setattr("scraper.consumer.mark_scraping", AsyncMock(side_effect=RuntimeError("db down")))
    mark_failed = AsyncMock()
    publish_job_failed = AsyncMock()
    monkeypatch.setattr("scraper.consumer.mark_failed", mark_failed)
    monkeypatch.setattr("scraper.consumer.publish_job_failed", publish_job_failed)

    await handle_message(channel, message, pool, AsyncMock(), AsyncMock())

    mark_failed.assert_awaited_once_with(pool, job_id, "db down")
    publish_job_failed.assert_awaited_once_with(job_id, "db down")
    message.nack.assert_awaited_once_with(requeue=False)
    channel.default_exchange.publish.assert_not_called()
