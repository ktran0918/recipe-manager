from unittest.mock import AsyncMock
from uuid import uuid4

from ollama._types import EmbedResponse

from scraper.indexing import (
    RecipeIndexDocument,
    embed_recipe,
    ensure_elasticsearch_index,
    ensure_qdrant_collection,
    index_elasticsearch_document,
    upsert_qdrant_point,
)


def _fixture_doc() -> RecipeIndexDocument:
    return RecipeIndexDocument(
        recipe_id=uuid4(),
        household_id=uuid4(),
        title="Garlic Butter Pasta",
        description="A quick weeknight pasta.",
        complexity="easy",
        cook_time_minutes=20,
        occasions=["weeknight"],
        cuisine="Italian",
        diet_tags=["vegetarian"],
        created_at="2026-01-15T10:00:00Z",
        ingredient_names=["spaghetti", "garlic clove"],
    )


async def test_embed_recipe_builds_text_from_title_description_ingredients_occasions(monkeypatch):
    doc = _fixture_doc()
    captured: dict[str, object] = {}

    class FakeOllamaClient:
        def __init__(self, host: str) -> None:
            pass

        async def embed(self, model: str, input: str) -> EmbedResponse:
            captured["model"] = model
            captured["input"] = input
            return EmbedResponse(embeddings=[[0.1, 0.2, 0.3]])

    monkeypatch.setattr("scraper.indexing.OllamaAsyncClient", FakeOllamaClient)

    vector = await embed_recipe(doc)

    assert vector == [0.1, 0.2, 0.3]
    assert captured["model"] == "nomic-embed-text"
    text = captured["input"]
    assert "Garlic Butter Pasta" in text
    assert "quick weeknight pasta" in text
    assert "spaghetti" in text
    assert "garlic clove" in text
    assert "weeknight" in text


async def test_upsert_qdrant_point_constructs_correct_payload():
    doc = _fixture_doc()
    client = AsyncMock()

    await upsert_qdrant_point(client, doc, [0.1, 0.2, 0.3])

    client.upsert.assert_awaited_once()
    _, kwargs = client.upsert.call_args
    assert kwargs["collection_name"] == "recipes"
    point = kwargs["points"][0]
    assert point.id == str(doc.recipe_id)
    assert point.vector == [0.1, 0.2, 0.3]
    assert point.payload["title"] == "Garlic Butter Pasta"
    assert point.payload["household_id"] == str(doc.household_id)
    assert point.payload["complexity"] == "easy"
    assert point.payload["cook_time_minutes"] == 20
    assert point.payload["occasions"] == ["weeknight"]
    assert point.payload["cuisine"] == "Italian"
    assert point.payload["diet_tags"] == ["vegetarian"]
    # Ingredient names feed the embedding text, not the Qdrant payload (which matches
    # docs/schema.md's smaller filtered-search field set) — confirm they're excluded.
    assert "ingredient_names" not in point.payload


async def test_index_elasticsearch_document_constructs_correct_body():
    doc = _fixture_doc()
    client = AsyncMock()

    await index_elasticsearch_document(client, doc)

    client.index.assert_awaited_once()
    _, kwargs = client.index.call_args
    assert kwargs["index"] == "recipes"
    assert kwargs["id"] == str(doc.recipe_id)
    document = kwargs["document"]
    assert document["title"] == "Garlic Butter Pasta"
    assert document["ingredient_names"] == "spaghetti, garlic clove"
    assert document["occasions"] == ["weeknight"]
    assert document["cook_time_minutes"] == 20


async def test_ensure_qdrant_collection_creates_when_missing():
    client = AsyncMock()
    client.collection_exists.return_value = False

    await ensure_qdrant_collection(client)

    client.create_collection.assert_awaited_once()


async def test_ensure_qdrant_collection_skips_when_present():
    client = AsyncMock()
    client.collection_exists.return_value = True

    await ensure_qdrant_collection(client)

    client.create_collection.assert_not_called()


async def test_ensure_elasticsearch_index_creates_when_missing():
    client = AsyncMock()
    client.indices.exists.return_value = False

    await ensure_elasticsearch_index(client)

    client.indices.create.assert_awaited_once()


async def test_ensure_elasticsearch_index_skips_when_present():
    client = AsyncMock()
    client.indices.exists.return_value = True

    await ensure_elasticsearch_index(client)

    client.indices.create.assert_not_called()
