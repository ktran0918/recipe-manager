# Architecture

## System Overview

```mermaid
flowchart TD
    Client["Client Layer — Web App / Mobile App / API consumers"]

    Client -->|REST / GraphQL| API
    Client -->|WebSocket - job status| API

    subgraph API["API Service — Spring Boot (REST + GraphQL)"]
        direction LR
        Auth & Recipes & Pantry & MealPlans & Shopping & AIRoutes
    end

    API -->|publish job| RabbitMQ[(RabbitMQ — job queue)]
    RabbitMQ -->|consume| Scraper

    subgraph Scraper["Scraper Service — Python"]
        direction TB
        S1["1. Fetch URL"]
        S2["2. Claude API"]
        S3["3. Validate"]
        S4["4. Store"]
        S5["5. Embed"]
        S1 --> S2 --> S3 --> S4 --> S5
    end

    API -->|read / write| PG[("PostgreSQL — users · households · recipes · pantry_items · pantry_events · meal_plans · shopping_lists")]
    Scraper -->|write| PG

    API -->|cache| Redis[(Redis — session · cost · dedup)]
    API -->|search| ES[(Elasticsearch — full-text + faceted search)]
    API -->|embeddings| Qdrant[(Qdrant — recipe + ingredient vectors)]
    API -->|AI queries| AIService

    subgraph AIService["AI Service — Python"]
        direction LR
        Ollama["Ollama (local) — embeddings — RAG responses — substitutions"]
        Claude["Claude API (cloud) — recipe parsing — NL meal planner agent"]
    end

    Scraper -->|embed| Qdrant
```

## Services

### API Service (`services/api/`) — Java 21 + Spring Boot 3
The central entry point. Handles all HTTP traffic — REST endpoints and GraphQL.

Responsibilities:
- Authentication (OAuth via Google, JWT issuance + refresh)
- Recipe CRUD and query
- Pantry state reads and writes (publishes pantry events)
- Meal plan management
- Shopping list generation and export
- Delegating scrape jobs to RabbitMQ
- Delegating AI queries to the AI service
- WebSocket connections for scrape job status

### Scraper Service (`services/scraper/`) — Python 3.12
A RabbitMQ consumer that processes recipe parsing jobs.

Pipeline per job:
1. Fetch raw HTML from URL (Playwright for JS-rendered pages, httpx for static)
2. Extract main content (trafilatura for article extraction)
3. Send extracted text to Claude API with a strict JSON schema → structured recipe
4. Validate output with Guardrails AI (required fields, sane values)
5. Write recipe + ingredients to PostgreSQL
6. Generate embeddings via Ollama → write to Qdrant
7. Update job status → notify API via Redis pub/sub → WebSocket push to client

### AI Service (`services/ai/`) — Python 3.12
Handles all LLM-powered features that require retrieval or multi-step reasoning.

Features:
- **Semantic search** (local): embed query via Ollama `nomic-embed-text` → Qdrant similarity search → return ranked recipes. No LLM generation step — results are returned directly.
- **RAG pipeline** (local): retrieve top-k recipes from Qdrant → inject context into Ollama `qwen2.5:14b` → generate natural language response. Used for "what can I make tonight?" and open-ended queries.
- **NL meal planner agent** (cloud): LangChain ReAct agent backed by `claude-haiku-4-5-20251001` with tools: `search_recipes`, `query_pantry`, `check_schedule`, `write_meal_plan`. Cloud required for reliable multi-step tool-calling.
- **Ingredient substitution** (local): few-shot prompt to Ollama `qwen2.5:14b`. Context is small and bounded; local quality is sufficient.
- **Shopping list consolidation** (algorithmic): unit conversion + quantity aggregation across recipes. No LLM required.

### Worker Service (`services/worker/`) — Python 3.12
Handles scheduled and periodic background jobs (separate from scrape jobs).

Jobs:
- Ingredient cost refresh (poll external grocery price API, update PostgreSQL)
- Expired meal plan cleanup
- Embedding re-indexing if schema changes

## Key Design Decisions

See `docs/adr/` for full reasoning. Summary:

| Decision | Choice | Alternative Rejected |
|---|---|---|
| Primary database | PostgreSQL | MongoDB — structured schema benefits outweigh flexibility |
| Pantry tracking | Event sourcing | Simple CRUD — audit trail and replay are valuable |
| Message broker | RabbitMQ | Kafka — simpler ops; Kafka overkill at this scale |
| Recipe parsing | Claude API (LLM) | HTML scraper — too brittle across different site formats |
| Embedding model | Ollama (local) | OpenAI Embeddings API — volume makes API cost add up |
| LLM boundary | Cloud (Haiku) for recipe parsing + NL agent; local (qwen2.5:14b) for RAG responses + substitutions | All cloud — cost and latency; all local — unreliable tool-calling for the agent |
| Vector DB | Qdrant | Pinecone — self-hostable; no cloud dependency |
| Search | Elasticsearch | PostgreSQL FTS — better faceting, fuzzy match, relevance tuning |
| RAG framework | From scratch first, then LlamaIndex | LlamaIndex only — need to understand the pipeline before abstracting it |
| Language stack | Split — Java + Spring Boot (API), Python (scraper / AI / worker) | Single language — no one language serves both OOP/DI and AI tooling goals equally well |

## Data Flow: Recipe Ingestion

```mermaid
flowchart TD
    A["User submits URL"] --> B["API validates URL format"]
    B --> C["Create job record in PostgreSQL (status=pending)"]
    C --> D["Publish job_id to RabbitMQ queue"]
    D --> E["Return job_id + WebSocket token to client"]
    E -->|async| F["Scraper picks up job"]
    F --> G["Fetch URL (Playwright if JS, httpx if static)"]
    G --> H["Extract main content via trafilatura"]
    H --> I["Claude API — extract structured recipe (title, ingredients, steps, metadata)"]
    I --> J["Guardrails AI — validate schema (required fields, quantity sanity)"]
    J --> K["Write to PostgreSQL (recipe + recipe_ingredients + recipe_steps)"]
    K --> L["Generate embeddings via Ollama — upsert to Qdrant"]
    L --> M["Update job status = complete"]
    M --> N["Publish completion event to Redis pub/sub"]
    N --> O["API pushes WebSocket event to client"]
    O --> P["Client navigates to new recipe"]
```

## Data Flow: Pantry Deduction on Cook

```mermaid
flowchart TD
    A["User selects recipe + serving size — POST /pantry/cook"] --> B["API loads recipe_ingredients"]
    B --> C["Apply serving size multiplier to all quantities"]
    C --> D["Unit-normalize quantities to match pantry units"]
    D --> E{"All ingredients sufficient?"}
    E -->|No| F["Return 400 with shortage list"]
    E -->|Yes| G["Write pantry_events (cook_deduction per ingredient)"]
    G --> H["Update pantry_items quantities (projection from events)"]
    H --> I["Return updated pantry state"]
```

## Data Flow: Semantic Recipe Search

```mermaid
flowchart TD
    A["User natural language query"] --> B["AI Service — embed query via Ollama"]
    B --> C["Similarity search in Qdrant (top-k by cosine similarity)"]
    C --> D["Retrieve recipe metadata from PostgreSQL for top-k IDs"]
    D --> E["Apply hard filters (pantry availability if requested)"]
    E --> F["Return ranked results with match scores"]
```

## Scalability Notes

At household/personal scale, a single Docker Compose deployment on a MacBook or small VPS handles all load comfortably. The architecture is designed so each service can be independently scaled if ever needed:

- API Service: stateless → horizontal scale behind a load balancer
- Scraper Service: add more consumers → RabbitMQ distributes jobs
- AI Service: stateless → horizontal scale
- PostgreSQL: read replicas for query-heavy load
- Redis: single instance sufficient at this scale
- Elasticsearch: single-node sufficient; can add shards later
- Qdrant: single-node sufficient; supports distributed mode later
