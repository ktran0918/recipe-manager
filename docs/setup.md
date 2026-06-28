# Setup Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | latest | Includes Docker Compose v2 |
| Git | any | |
| Java | 21 (LTS) | For `services/api/` — use [SDKMAN](https://sdkman.io) or Homebrew |
| Python | 3.12 | For scraper, AI, worker services |
| Ollama | latest | Runs **on the host**, not in Docker — [ollama.com](https://ollama.com) |

---

## 1. Clone and configure environment

```bash
git clone https://github.com/ktran0918/recipe-manager.git
cd recipe-manager

cp .env.example .env
```

Open `.env` and fill in the required values:

| Variable | Required | Notes |
|---|---|---|
| `ANTHROPIC_API_KEY` | Yes | Recipe parsing + AI features (Phase 2+) |
| `GOOGLE_CLIENT_ID` | Yes | OAuth login |
| `GOOGLE_CLIENT_SECRET` | Yes | OAuth login |
| `JWT_SECRET` | Yes | Generate with `openssl rand -hex 32` |
| `POSTGRES_PASSWORD` | Yes | Pick any strong password |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | No | Defaults to `guest`/`guest` |

All other variables have working defaults for local development.

---

## 2. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, RabbitMQ, Elasticsearch, and Qdrant. Application services (API, scraper, AI, worker) are excluded until their code is built in Phase 1+.

Wait ~60 seconds for Elasticsearch to initialize, then verify all five containers are healthy:

```bash
docker compose ps
```

All five should show `(healthy)`. If any show `(starting)`, wait a few more seconds and re-run.

### Port reference

| Service | Port | Notes |
|---|---|---|
| PostgreSQL | `5432` | Connect with any Postgres client |
| Redis | `6379` | |
| RabbitMQ | `5672` | AMQP |
| RabbitMQ management UI | `15672` | `guest`/`guest` (or your `.env` values) |
| Elasticsearch | `9200` | REST API |
| Qdrant | `6333` | REST API |
| Qdrant gRPC | `6334` | |
| API service | `8000` | Available after Phase 1 |
| AI service | `8001` | Available after Phase 6 |

### Quick connectivity checks

```bash
# PostgreSQL
docker compose exec postgres pg_isready -U recipe_manager -d recipe_manager

# Redis
docker compose exec redis redis-cli ping          # → PONG

# RabbitMQ
curl -sf http://localhost:15672 -o /dev/null && echo "RabbitMQ UI up"

# Elasticsearch
curl -s http://localhost:9200/_cluster/health | python3 -m json.tool

# Qdrant
curl -s http://localhost:6333/readyz              # → {"status":"ok"}
```

---

## 3. Pull Ollama models

Ollama runs on the host machine and is required by the scraper and AI services (Phase 2+). Skip this step if you're only working on Phase 1.

```bash
ollama serve                    # if not already running as a background service
ollama pull nomic-embed-text    # embedding model — required from Phase 2
ollama pull qwen2.5:14b         # local reasoning model — required from Phase 6
```

`nomic-embed-text` is ~274 MB. `qwen2.5:14b` is ~9 GB (Q4 quantised) — allow time for the download.

`qwen2.5:14b` handles RAG response generation and ingredient substitution locally. It uses the iGPU on Linux via ROCm if available, falling back to CPU otherwise. See `docs/adr/007-local-vs-cloud-llm-boundary.md`.

Verify: `curl http://localhost:11434/api/tags` should list both models.

---

## 4. Start application services (Phase 1+)

Once service code exists, start everything together:

```bash
docker compose --profile app up -d
```

The API service waits for all infrastructure containers to be healthy before starting, so ordering is handled automatically.

---

## 5. Stop and clean up

```bash
# Stop containers, keep volumes (data survives)
docker compose down

# Stop and delete all data (full reset)
docker compose down -v
```

---

## Development workflow

### API service (Java + Spring Boot)

```bash
cd services/api
./mvnw verify              # Checkstyle + compile + tests
./mvnw spring-boot:run     # Run locally against Docker infra
```

Flyway migrations run automatically on startup. Add new migrations to `src/main/resources/db/migration/` following the naming convention `V{version}__{description}.sql`.

### Python services (scraper, AI, worker)

```bash
# From repo root
ruff check services/scraper services/ai services/worker
mypy services/scraper services/ai services/worker
pytest services/scraper services/ai services/worker
```

---

## Troubleshooting

**Elasticsearch stuck in `(starting)`**  
It needs up to 60s on first launch. If it never becomes healthy, check its logs:
```bash
docker compose logs elasticsearch --tail=50
```
The most common cause is insufficient memory. Ensure Docker Desktop has at least 4GB RAM allocated (Settings → Resources).

**Port already in use**  
One of the ports (5432, 6379, etc.) is bound by a local process. Find and stop it, or change the host port in `docker-compose.yml` (left side of `host:container`).

**Ollama unreachable from containers**  
The scraper and AI services reach Ollama via `host.docker.internal:11434`. On Linux, `host.docker.internal` is not set automatically — add it to your `docker-compose.yml` under the relevant service:
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```
