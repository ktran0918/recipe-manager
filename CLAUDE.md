# CLAUDE.md — Recipe Manager

## Purpose

A learning and portfolio project. Primary goals:

1. **Learn by building** — practice backend engineering patterns (event sourcing, async job queues, CQRS) and AI/ML engineering patterns (RAG from scratch, vector search, LLM-powered pipelines, agent design) on a real, non-trivial problem.
2. **Showcase on resume and GitHub** — the repo should reflect production-quality thinking: ADRs, clean architecture, a working Docker Compose setup, and a published demo.

When suggesting approaches, prefer implementations that teach the underlying pattern before reaching for an abstraction library. See `docs/phases.md` — Phase 6 builds RAG from scratch before introducing LlamaIndex intentionally.

---

## Project Status

Currently in the **planning phase** — all docs are written, no service code exists yet. The `services/` directory is planned but empty. Implementation begins with Phase 1 (core backend).

---

## Architecture

Split-language microservices, all containerized via Docker Compose:

| Service | Language | Port | Role |
|---|---|---|---|
| `services/api/` | Java 21 + Spring Boot 3 | 8000 | REST + GraphQL entry point |
| `services/scraper/` | Python 3.12 | — | RabbitMQ consumer — URL fetch + LLM parse + embed |
| `services/ai/` | Python 3.12 + FastAPI | 8001 | RAG pipeline, semantic search, NL meal planner agent |
| `services/worker/` | Python 3.12 | — | Scheduled background jobs (cost refresh, cleanup) |

The split is intentional — Java for OOP/DI discipline, Python for AI tooling. See `docs/adr/006-split-language-stack.md`.

Infrastructure: PostgreSQL 16, Redis 7, RabbitMQ 3, Elasticsearch 8.13, Qdrant (latest), Ollama (runs on host, not in Docker).

Full diagrams and data flows: `docs/architecture.md`
Schema: `docs/schema.md`
API reference: `docs/api.md`
Implementation plan: `docs/phases.md`
Design decisions: `docs/adr/`

---

## Running the Project

```bash
cp .env.example .env
# Set ANTHROPIC_API_KEY, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET at minimum

docker-compose up
```

- API: `http://localhost:8000`
- GraphQL playground: `http://localhost:8000/graphql`
- RabbitMQ management UI: `http://localhost:15672` (guest/guest)

Ollama runs on the host machine, not in Docker. The scraper and AI services reach it via `host.docker.internal:11434`.

---

## Development Commands

Once service code exists (Phase 1+):

```bash
# --- API service (Java + Spring Boot) ---
cd services/api
./mvnw verify              # lint (Checkstyle) + compile + test
./mvnw spring-boot:run     # run locally (outside Docker)

# Flyway migrations run automatically on startup.
# To generate a new migration: add a file to src/main/resources/db/migration/
# Naming: V{version}__{description}.sql

# --- Python services (scraper, ai, worker) ---
ruff check services/scraper services/ai services/worker   # lint
mypy services/scraper services/ai services/worker         # type check
pytest services/scraper services/ai services/worker       # tests
```

CI runs per service: Java uses `./mvnw verify`; Python services use ruff + mypy + pytest.

---

## Key Architectural Invariants

**Pantry event sourcing:** `pantry_events` is the source of truth — immutable, append-only. `pantry_items` is a projection derived from events. Never update pantry quantities in place. All writes (add, deduct, cook_deduction, adjust, expire) go through `pantry_events` first, then update the projection, always in a single PostgreSQL transaction. See `docs/adr/002-pantry-event-sourcing.md`.

**Recipe parsing:** Claude API with tool-calling and a strict JSON schema, not CSS selectors or rule-based parsers. Trafilatura pre-processes raw HTML to article text before the LLM call. Guardrails AI validates output before writing to PostgreSQL. See `docs/adr/004-llm-recipe-parsing.md`.

**Embedding:** Ollama (`nomic-embed-text`) for all embedding generation — not the Anthropic or OpenAI embeddings API. Volume at household scale makes local embedding the right call on cost.

**Vector DB:** Qdrant in production, Chroma locally (zero-config). Both use the same Python API surface. See `docs/adr/005-vector-database.md`.

---

## Teaching Style

When implementing stories, act as a **syntax instructor**:
- Implement all code for patterns the developer has already seen and practiced.
- Leave only **genuinely new** Java/Spring patterns as `// TODO:` blocks — new annotations, new API methods, or new test assertions the developer hasn't written before.
- Include C# parallels in comments wherever a Java/Spring concept maps to a familiar .NET pattern.
- TODOs should be meaningful — new relationship declarations, new query patterns, new test assertions — not trivial fill-in-the-blank.
- Accompany each story with updates to `docs/java-for-dotnet-devs.md` covering any new patterns introduced.
- When reviewing completed work or preparing to commit, always check `docs/stories.md` and tick off any ACs that are now satisfied.

---

## Developer Preferences

- **Diagrams:** Always use mermaid. Never use ASCII art or plain-text flowcharts. No `\n` characters inside mermaid node label strings — use ` — ` or a space as a separator instead.
- **ADRs:** Major technology choices go in `docs/adr/` with context, rationale, and alternatives rejected.
- **Comments:** Only when the WHY is non-obvious. No docstrings restating what the function name already says.
- **Testing:** Integration tests hit a real (test) database — no mocking the DB layer. Pytest fixtures use transaction rollback per test for isolation.
- **Claude API costs:** The Anthropic API is billed separately from Claude Pro. Recipe parse cost is ~$0.02–$0.03 per URL at Sonnet pricing. Haiku is viable for structured extraction with a tight schema.
- **Git commit attribution:** End every AI-assisted commit with the trailer `AI-assistant: Claude Sonnet 4.6` (no email). `Co-Authored-By` is reserved for human developers only.
