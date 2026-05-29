# Recipe Manager

A multi-user meal planning platform that scrapes and parses recipes from any URL using an LLM pipeline, manages a shared household pantry with event sourcing, and provides AI-powered meal planning and semantic recipe search.

## Why It's Non-Trivial

- **LLM-powered recipe parsing** — traditional scrapers break on every site's unique HTML; Claude API with structured output handles any format robustly
- **Event-sourced pantry** — every ingredient change is a durable event; pantry state is a projection that can be replayed and audited
- **Async scraping pipeline** — URL submission triggers a distributed job queue (RabbitMQ) → scraper → LLM → embedding generation, with real-time status updates
- **RAG from scratch** — semantic recipe search built on raw embeddings + Qdrant vector DB before introducing LlamaIndex, so the full retrieval pipeline is transparent
- **Multi-user household model** — shared pantry and meal plans across household members, with per-user action attribution

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| API service | Java 21 + Spring Boot 3 | Strong OOP/DI (Spring IoC), mature clients for every infra component, industry-standard resume signal |
| Scraper / AI / Worker | Python 3.12 + FastAPI / aio-pika | Official Anthropic SDK; LangChain, LlamaIndex, and all AI tooling is Python-first |
| GraphQL | Spring GraphQL | Code-first GraphQL integrated with Spring's type system |
| Primary DB | PostgreSQL | ACID transactions for pantry event sourcing; structured recipe schema |
| Cache | Redis | Scraped recipe deduplication; ingredient cost cache; session store |
| Search | Elasticsearch | Faceted recipe search by ingredient, occasion, complexity, cook time |
| Vector DB | Qdrant | Self-hosted; semantic recipe search and ingredient embeddings |
| Queue | RabbitMQ | Scraping job queue; simpler ops than Kafka at this scale |
| Embeddings | Ollama (local) | High-volume embedding generation without API cost |
| LLM | Claude API (Anthropic) | Recipe parsing, NL meal planning agent, ingredient substitution |
| Containerization | Docker + Docker Compose | Single-command local setup |
| CI/CD | GitHub Actions | Lint, test, build, push on every PR |

## Architecture

See [docs/architecture.md](./docs/architecture.md)

## API Reference

See [docs/api.md](./docs/api.md)

## Database Schema

See [docs/schema.md](./docs/schema.md)

## Implementation Phases

See [docs/phases.md](./docs/phases.md)

## Architecture Decision Records

See [docs/adr/](./docs/adr/)

## Getting Started

```bash
cp .env.example .env
# Fill in ANTHROPIC_API_KEY and other values

docker-compose up
```

API available at `http://localhost:8000`  
GraphQL playground at `http://localhost:8000/graphql`

## Project Structure

```
recipe_manager/
├── services/
│   ├── api/          # FastAPI — REST + GraphQL endpoints
│   ├── scraper/      # URL fetching + LLM recipe parsing
│   ├── ai/           # RAG pipeline, semantic search, NL agent
│   └── worker/       # RabbitMQ consumer for background jobs
├── docs/
│   ├── architecture.md
│   ├── schema.md
│   ├── api.md
│   ├── phases.md
│   └── adr/          # Architecture Decision Records
└── docker-compose.yml
```
