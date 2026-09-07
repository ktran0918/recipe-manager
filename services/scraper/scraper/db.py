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


async def mark_failed(pool: asyncpg.Pool, job_id: UUID, error: str) -> None:
    await pool.execute(
        "UPDATE scrape_jobs SET status = 'failed', error = $2, updated_at = now() WHERE id = $1",
        job_id,
        error,
    )
