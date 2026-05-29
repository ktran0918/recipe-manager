# ADR 003 — Message Broker: RabbitMQ

**Status:** Accepted

## Context

The recipe scraping pipeline is async — a URL submission triggers a job that may take 5–30 seconds (fetch + LLM parse + embed). We need a queue to dispatch jobs to the scraper service and decouple the API from the scraping work.

## Decision

Use **RabbitMQ**.

## Rationale

- Scraping jobs are discrete tasks with clear completion semantics — RabbitMQ's work queue model (competing consumers, acknowledgements, dead-letter exchange for failures) maps directly to this
- Simple to operate at this scale: one exchange, two queues (`scrape_jobs`, `scrape_jobs.dlx`)
- Built-in dead-letter routing: failed jobs (after N retries) land in a DLX queue for inspection without custom code
- RabbitMQ management UI gives visibility into queue depth and consumer health out of the box

## Alternatives Rejected

**Kafka:** Designed for high-throughput event streaming where consumers need to replay history. Scrape jobs don't need replay — once a job is processed, it's done. Kafka's operational overhead (ZooKeeper or KRaft, partition management, consumer group offsets) is unjustified for a job queue at this scale. Kafka is used in the Chat Platform where its strengths (durable event log, fan-out, replay) are actually needed.

**PostgreSQL-backed queue (pg-boss / River):** A legitimate option at small scale. Rejected because RabbitMQ gives us dedicated message broker experience on the resume, and the separation of concerns (DB for data, broker for jobs) is architecturally cleaner.
