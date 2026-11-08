import asyncio
import logging

import aio_pika
from elasticsearch import AsyncElasticsearch
from qdrant_client import AsyncQdrantClient

from scraper.config import settings
from scraper.consumer import handle_message
from scraper.db import create_pool
from scraper.indexing import ensure_elasticsearch_index, ensure_qdrant_collection
from scraper.logging_config import configure_logging

logger = logging.getLogger(__name__)


async def main() -> None:
    configure_logging()
    pool = await create_pool()
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)

    # Long-lived clients, reused across every message — unlike the Ollama/Anthropic clients
    # in recipe_parser.py (stateless HTTP calls with no connection setup worth amortizing),
    # Qdrant and Elasticsearch clients are meant to be constructed once and reused.
    qdrant_client = AsyncQdrantClient(url=settings.qdrant_url)
    es_client = AsyncElasticsearch(hosts=[settings.elasticsearch_url])
    await ensure_qdrant_collection(qdrant_client)
    await ensure_elasticsearch_index(es_client)

    async with connection:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)

        # durable + dead-letter args must match whatever declared the queue first (the API's
        # RabbitMqConfig, normally) — RabbitMQ rejects a re-declare with mismatched arguments.
        queue = await channel.declare_queue(
            settings.queue_name,
            durable=True,
            arguments={"x-dead-letter-exchange": settings.dlx_name},
        )

        logger.info("Scraper consumer listening on %s", settings.queue_name)
        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                await handle_message(channel, message, pool, qdrant_client, es_client)


if __name__ == "__main__":
    asyncio.run(main())
