import logging
from typing import Any
from uuid import UUID

import aio_pika
from aio_pika.abc import AbstractChannel, AbstractIncomingMessage
from pydantic import ValidationError

from scraper.config import settings
from scraper.db import mark_failed, mark_scraping
from scraper.logging_config import set_job_id
from scraper.models import ScrapeJobMessage

logger = logging.getLogger(__name__)

# RabbitMQ has no built-in delivery-attempt counter for a plain NACK+requeue — the broker just
# redelivers the same message unchanged. We track attempts ourselves via a header and republish.
RETRY_HEADER = "x-retry-count"


async def handle_message(channel: AbstractChannel, message: AbstractIncomingMessage, pool: Any) -> None:
    async with message.process(ignore_processed=True):
        try:
            job = ScrapeJobMessage.model_validate_json(message.body)
        except ValidationError:
            logger.exception("Malformed scrape job message; dead-lettering without retry")
            await message.nack(requeue=False)
            return

        set_job_id(str(job.job_id))
        try:
            logger.info("Picked up scrape job for %s", job.url)
            await mark_scraping(pool, job.job_id)
            await message.ack()
            logger.info("Marked job as scraping")
        except Exception as exc:  # noqa: BLE001 — any failure here must route to retry/dead-letter
            await _handle_failure(channel, message, pool, job.job_id, exc)
        finally:
            set_job_id(None)


async def _handle_failure(
    channel: AbstractChannel,
    message: AbstractIncomingMessage,
    pool: Any,
    job_id: UUID,
    exc: Exception,
) -> None:
    # AMQP header values are typed as a broad union (int | str | Decimal | ...); we only ever
    # write ints ourselves, so anything else means the header was set by someone/something
    # else — treat it as a first attempt rather than trusting an unexpected type.
    raw_retry_count = message.headers.get(RETRY_HEADER, 0) if message.headers else 0
    retry_count = raw_retry_count if isinstance(raw_retry_count, int) else 0
    if retry_count < settings.max_scrape_retries:
        logger.warning(
            "Scrape job failed (attempt %s/%s): %s", retry_count + 1, settings.max_scrape_retries, exc
        )
        await _requeue_with_retry(channel, message, retry_count + 1)
    else:
        logger.error("Scrape job failed after %s attempts, dead-lettering: %s", settings.max_scrape_retries, exc)
        await mark_failed(pool, job_id, str(exc))
        await message.nack(requeue=False)


async def _requeue_with_retry(channel: AbstractChannel, message: AbstractIncomingMessage, retry_count: int) -> None:
    headers = dict(message.headers or {})
    headers[RETRY_HEADER] = retry_count
    await channel.default_exchange.publish(
        aio_pika.Message(
            body=message.body,
            headers=headers,
            content_type=message.content_type,
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        ),
        # routing_key is typed str | None on an incoming message; every message this consumer
        # sees came from settings.queue_name, so that's the correct fallback, not a guess.
        routing_key=message.routing_key or settings.queue_name,
    )
    # ACK the original — it's been replaced by the republished copy above, not retried in place.
    await message.ack()
