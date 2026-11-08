from dataclasses import dataclass
from uuid import UUID

import asyncpg
from elasticsearch import AsyncElasticsearch
from ollama import AsyncClient as OllamaAsyncClient
from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

from scraper.config import settings
from scraper.db import fetch_recipe_ingredient_names, fetch_recipe_row

QDRANT_COLLECTION = "recipes"
ELASTICSEARCH_INDEX = "recipes"

# Matches docs/schema.md's Elasticsearch mapping for the recipes index.
_ES_MAPPINGS = {
    "properties": {
        "household_id": {"type": "keyword"},
        "title": {"type": "text", "analyzer": "english"},
        "description": {"type": "text", "analyzer": "english"},
        "ingredient_names": {"type": "text", "analyzer": "english"},
        "occasions": {"type": "keyword"},
        "complexity": {"type": "keyword"},
        "cuisine": {"type": "keyword"},
        "diet_tags": {"type": "keyword"},
        "cook_time_minutes": {"type": "integer"},
        "created_at": {"type": "date"},
    }
}


@dataclass
class RecipeIndexDocument:
    recipe_id: UUID
    household_id: UUID
    title: str
    description: str | None
    complexity: str | None
    cook_time_minutes: int | None
    occasions: list[str] | None
    cuisine: str | None
    diet_tags: list[str] | None
    created_at: object
    ingredient_names: list[str]


async def load_recipe_for_indexing(pool: asyncpg.Pool, recipe_id: UUID) -> RecipeIndexDocument:
    row = await fetch_recipe_row(pool, recipe_id)
    ingredient_names = await fetch_recipe_ingredient_names(pool, recipe_id)
    return RecipeIndexDocument(
        recipe_id=recipe_id,
        household_id=row["household_id"],
        title=row["title"],
        description=row["description"],
        complexity=row["complexity"],
        cook_time_minutes=row["cook_time_minutes"],
        occasions=list(row["occasions"]) if row["occasions"] else None,
        cuisine=row["cuisine"],
        diet_tags=list(row["diet_tags"]) if row["diet_tags"] else None,
        created_at=row["created_at"],
        ingredient_names=ingredient_names,
    )


def _embedding_text(doc: RecipeIndexDocument) -> str:
    parts = [doc.title, doc.description or "", ", ".join(doc.ingredient_names), ", ".join(doc.occasions or [])]
    return " ".join(part for part in parts if part)


async def embed_recipe(doc: RecipeIndexDocument) -> list[float]:
    client = OllamaAsyncClient(host=settings.ollama_url)
    response = await client.embed(model=settings.ollama_embed_model, input=_embedding_text(doc))
    return list(response.embeddings[0])


async def ensure_qdrant_collection(client: AsyncQdrantClient) -> None:
    if not await client.collection_exists(QDRANT_COLLECTION):
        await client.create_collection(
            QDRANT_COLLECTION,
            vectors_config=VectorParams(size=768, distance=Distance.COSINE),
        )


async def upsert_qdrant_point(client: AsyncQdrantClient, doc: RecipeIndexDocument, vector: list[float]) -> None:
    await client.upsert(
        collection_name=QDRANT_COLLECTION,
        points=[
            PointStruct(
                id=str(doc.recipe_id),
                vector=vector,
                payload={
                    "household_id": str(doc.household_id),
                    "title": doc.title,
                    "complexity": doc.complexity,
                    "cook_time_minutes": doc.cook_time_minutes,
                    "occasions": doc.occasions,
                    "cuisine": doc.cuisine,
                    "diet_tags": doc.diet_tags,
                },
            )
        ],
    )


async def ensure_elasticsearch_index(client: AsyncElasticsearch) -> None:
    if not await client.indices.exists(index=ELASTICSEARCH_INDEX):
        await client.indices.create(index=ELASTICSEARCH_INDEX, mappings=_ES_MAPPINGS)


async def index_elasticsearch_document(client: AsyncElasticsearch, doc: RecipeIndexDocument) -> None:
    await client.index(
        index=ELASTICSEARCH_INDEX,
        id=str(doc.recipe_id),
        document={
            "household_id": str(doc.household_id),
            "title": doc.title,
            "description": doc.description,
            "ingredient_names": ", ".join(doc.ingredient_names),
            "occasions": doc.occasions,
            "complexity": doc.complexity,
            "cuisine": doc.cuisine,
            "diet_tags": doc.diet_tags,
            "cook_time_minutes": doc.cook_time_minutes,
            "created_at": doc.created_at.isoformat() if hasattr(doc.created_at, "isoformat") else doc.created_at,
        },
    )
