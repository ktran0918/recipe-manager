from uuid import UUID

import asyncpg

from scraper.config import settings


async def create_pool() -> asyncpg.Pool:
    return await asyncpg.create_pool(
        host=settings.postgres_host,
        port=settings.postgres_port,
        database=settings.postgres_db,
        user=settings.postgres_user,
        password=settings.postgres_password,
        min_size=1,
        max_size=5,
    )


async def mark_scraping(pool: asyncpg.Pool, job_id: UUID) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'scraping', updated_at = now() WHERE id = $1",
        job_id,
    )


async def mark_parsing(pool: asyncpg.Pool, job_id: UUID) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'parsing', updated_at = now() WHERE id = $1",
        job_id,
    )


async def get_job_creator(pool: asyncpg.Pool, job_id: UUID) -> UUID:
    created_by: UUID = await pool.fetchval("SELECT created_by FROM scrape_jobs WHERE id = $1", job_id)
    return created_by


async def mark_embedding(pool: asyncpg.Pool, job_id: UUID, recipe_id: UUID) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'embedding', recipe_id = $2, updated_at = now() WHERE id = $1",
        job_id,
        recipe_id,
    )


async def mark_complete(pool: asyncpg.Pool, job_id: UUID) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'complete', updated_at = now() WHERE id = $1",
        job_id,
    )


async def mark_failed(pool: asyncpg.Pool, job_id: UUID, error: str) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'failed', error = $2, updated_at = now() WHERE id = $1",
        job_id,
        error,
    )


async def fetch_recipe_row(pool: asyncpg.Pool, recipe_id: UUID) -> asyncpg.Record:
    """Reads the committed recipe back from Postgres rather than reusing the in-memory
    ParsedRecipe — embedding/indexing should reflect what was actually persisted (e.g.
    ingredient names as normalized in the catalog, not the LLM's raw extraction).
    """
    row = await pool.fetchrow(
        """
        SELECT id, household_id, title, description, complexity, cook_time_minutes,
               occasions, cuisine, diet_tags, created_at
        FROM recipes
        WHERE id = $1
        """,
        recipe_id,
    )
    if row is None:
        raise ValueError(f"Recipe {recipe_id} not found")
    return row


async def fetch_recipe_ingredient_names(pool: asyncpg.Pool, recipe_id: UUID) -> list[str]:
    rows = await pool.fetch(
        """
        SELECT i.name
        FROM recipe_ingredients ri
        JOIN ingredients i ON i.id = ri.ingredient_id
        WHERE ri.recipe_id = $1
        ORDER BY ri.sort_order
        """,
        recipe_id,
    )
    return [row["name"] for row in rows]
