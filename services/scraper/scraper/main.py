import asyncio
import logging

import aio_pika

from scraper.config import settings
from scraper.consumer import handle_message
from scraper.db import create_pool
from scraper.logging_config import configure_logging

logger = logging.getLogger(__name__)


async def main() -> None:
    configure_logging()
    pool = await create_pool()
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)

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
                await handle_message(channel, message, pool)


if __name__ == "__main__":
    asyncio.run(main())
