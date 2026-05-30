# Implementation Phases

## Overview

| Phase | Focus | Duration | Demo Milestone |
|---|---|---|---|
| 1 | Core backend — auth, recipe CRUD, DB, Docker | 3–4 weeks | API returns recipes; Swagger UI works |
| 2 | Scraping pipeline — async URL parsing via LLM | 3–4 weeks | Paste URL → structured recipe stored |
| 3 | Pantry — quantities, event sourcing, barcode scan | 3–4 weeks | Add ingredients, deduct on cook |
| 4 | Meal planning — weekly plan, one-off query, impact | 2–3 weeks | Plan a week of dinners |
| 5 | Shopping list — generation, PDF, Notes export | 1–2 weeks | Full end-to-end: plan → shop |
| 6 | AI layer — RAG from scratch, semantic search, NL agent | 3–4 weeks | "What can I make tonight?" works |
| 7 | Production hardening — CI/CD, K8s, observability | 1–2 weeks | Live deployment, GitHub published |

**Total: 16–23 weeks**

Start Chat Platform around Phase 3–4 of Recipe Manager.

---

## Phase 1 — Core Backend (3–4 weeks)

**Goal:** Solid API foundation. Auth works. Recipes can be created, read, updated, deleted. Everything runs in Docker.

**Service:** `services/api/` — Java 21 + Spring Boot 3

### Tasks

**Infrastructure**
- [ ] `docker-compose.yml` with PostgreSQL, Redis, Elasticsearch, RabbitMQ, Qdrant
- [ ] `.env.example` with all required env vars
- [ ] Spring Boot 3 project scaffold (`services/api/`): Maven, Spring Web, Spring Data JPA, Spring Security, Spring AMQP, Spring Data Redis, health endpoint (`/actuator/health`)
- [ ] Flyway migrations setup
- [ ] GitHub Actions CI: `./mvnw verify` (Checkstyle + JUnit 5) on every PR

**Auth**
- [ ] Google OAuth2 Login via Spring Security OAuth2 Client
- [ ] JWT issuance via jjwt (access token 15min, refresh token 7d in Redis)
- [ ] Spring Security filter chain: `OncePerRequestFilter` extracts + validates JWT, sets `SecurityContextHolder`
- [ ] `GET /auth/google`, `GET /auth/google/callback`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`

**Households**
- [ ] Flyway migration: `users`, `households`, `household_members`
- [ ] JPA entities + Spring Data JPA repositories (program to interfaces)
- [ ] Service + controller layers
- [ ] Create household (auto-generates invite code)
- [ ] Join by invite code
- [ ] Member management endpoints

**Recipes (manual entry)**
- [ ] Flyway migrations: `recipes`, `ingredients`, `recipe_ingredients`, `recipe_steps`
- [ ] JPA entities, repositories, service interfaces + implementations, REST controllers
- [ ] Full CRUD for recipes
- [ ] `GET /recipes` with basic keyword filter (PostgreSQL ILIKE via JPQL; Elasticsearch in Phase 2)
- [ ] `GET /recipes/:id` with full ingredient + step detail

**Cook Mode**
- [ ] Flyway migration: `recipe_nutrition` table
- [ ] `GET /recipes/:id/cook-mode?servings=N` — condensed view: cook time, scaled ingredients, original directions, serving size, nutrition (null until Phase 2), `source_url`
- [ ] `POST /recipes/:id/select` — store selection in Redis (`cook:selection:{user_id}`, 24h TTL); returns cook-mode view
- [ ] `DELETE /recipes/selection` — clear selection; idempotent
- [ ] `GET /recipes/selection` — return current selection or null

**Testing**
- [ ] Testcontainers: PostgreSQL + Redis containers; `@Transactional` rollback per test
- [ ] MockMvc integration tests: full auth flow, recipe CRUD
- [ ] Unit tests: JWT encode/decode, quantity math (plain JUnit 5, no Spring context)

**Definition of done:** `docker-compose up` → hit Swagger UI (springdoc-openapi) → create a household, authenticate, create a recipe manually, retrieve it.

---

## Phase 2 — Scraping Pipeline (3–4 weeks)

**Goal:** Submit any recipe URL → background job → structured recipe stored in DB + Elasticsearch + Qdrant.

**Services:** `services/scraper/` (Python 3.12) + `services/api/` additions (Java + Spring Boot)

### Tasks

**Scraper Service**
- [ ] RabbitMQ consumer (aio-pika)
- [ ] HTTP fetcher: httpx for static pages; Playwright for JS-rendered pages
- [ ] Content extractor: trafilatura (strips nav, ads, boilerplate)
- [ ] Claude API integration: structured output with recipe schema including optional `substitutions[]` array (extracted from recipe text)
  ```python
  # Claude call with strict JSON schema
  response = anthropic.messages.create(
      model="claude-opus-4-6",
      system=RECIPE_PARSER_SYSTEM_PROMPT,
      messages=[{"role": "user", "content": raw_text}],
      tools=[recipe_extraction_tool],  # tool with JSON schema
  )
  ```
- [ ] Guardrails AI: validate required fields, sane quantity ranges, non-empty steps
- [ ] Write `recipe_ingredient_substitutions` rows (`source='recipe'`) for any substitutions extracted from the recipe text
- [ ] Ingredient normalization: match to existing `ingredients` table or create new

**Embedding Pipeline**
- [ ] Ollama integration (local embedding model: `nomic-embed-text`)
- [ ] Embed recipe: concatenate title + description + ingredient names + occasions
- [ ] Upsert to Qdrant `recipes` collection

**Elasticsearch Indexing**
- [ ] Index recipe document on scrape completion
- [ ] Update `GET /recipes` to use Elasticsearch for keyword search

**API**
- [ ] `POST /recipes/parse` — validates URL, creates `scrape_jobs` record, publishes to RabbitMQ
- [ ] `GET /recipes/jobs/:job_id` — poll job status
- [ ] WebSocket endpoint: real-time status push via Redis pub/sub
- [ ] Dedup: check Redis `recipe:scraped:{url_hash}` before scraping

**Nutrition Extraction**
- [ ] Extend Claude extraction schema with optional `nutrition` object
- [ ] Write `recipe_nutrition` row if any nutrition field returned; skip if none
- [ ] `GET /recipes/:id/cook-mode` now returns populated nutrition for scraped recipes

**Cost Fetching**
- [ ] Worker job: on new ingredient, attempt cost lookup from external source
- [ ] Cache in Redis with 6h TTL; persist baseline to `ingredient_costs`

**Testing**
- [ ] Unit test: Claude structured output parsing with fixture responses
- [ ] Integration test: mock Claude API, run full scrape pipeline end-to-end
- [ ] Test: dedup prevents re-scraping same URL within 7 days

**Definition of done:** Submit `seriouseats.com` pizza recipe URL → poll job → recipe appears with structured ingredients, estimated costs, and nutrition data (if present on source page).

---

## Phase 3 — Pantry (3–4 weeks)

**Goal:** Full pantry management with event sourcing, serving size math, and barcode scanning.

**Service:** `services/api/` — Java + Spring Boot

### Tasks

**Pantry API**
- [ ] Schema migrations: `pantry_items`, `pantry_events`
- [ ] `GET /pantry` — current state (read from `pantry_items`)
- [ ] `POST /pantry/items` — add ingredient; write `add` event; update projection
- [ ] `PATCH /pantry/items/:id` — adjust quantity; write `adjust` event
- [ ] `DELETE /pantry/items/:id` — write `expire` event; zero out projection
- [ ] `GET /pantry/events` — full change log for household

**Cook Deduction**
- [ ] `POST /pantry/cook`:
  - Load `recipe_ingredients` for recipe
  - Apply serving size multiplier: `quantity * (requested_servings / recipe.servings)`
  - Unit-normalize to match pantry units (e.g., convert cups → ml)
  - Check all `pantry_items` quantities sufficient → 422 with shortage list if not
  - Write `cook_deduction` events for each ingredient (atomic, same transaction)
  - Update `pantry_items` projections
  - Return updated pantry state

**Serving Size Logic**
- [ ] Unit conversion table (e.g., `1 cup flour = 120g`, `1 tbsp = 15ml`)
- [ ] Apply multiplier to all ingredient quantities for display when serving size changes
- [ ] API: `GET /recipes/:id?servings=2` returns adjusted ingredient quantities

**Barcode Scanning**
- [ ] `POST /pantry/scan` — lookup barcode in `ingredients.barcode`
- [ ] If not found: call Open Food Facts API (free, no key required) → parse product → create/match ingredient

**Recipe Availability Query**
- [ ] `GET /recipes?can_make=true` — for each recipe, check if all non-optional ingredients have sufficient quantity in pantry
- [ ] Optimize: batch check (load pantry once, evaluate all recipes in memory for small household sizes)

**Testing**
- [ ] Unit tests: serving size multiplier, unit conversion
- [ ] Unit test: shortage detection logic
- [ ] Integration test: cook deduction creates correct events and updates projections
- [ ] Integration test: event replay reconstructs correct pantry state

**Definition of done:** Add ingredients to pantry manually + by barcode. Mark a recipe as cooked → quantities deducted. Query which recipes you can make right now.

---

## Phase 4 — Meal Planning (2–3 weeks)

**Goal:** Plan meals for the week. See what the plan will consume from your pantry. Understand what you need to buy.

**Service:** `services/api/` — Java + Spring Boot

### Tasks

- [ ] Schema migrations: `meal_plans`, `meal_plan_entries`
- [ ] `POST /meal-plans` — create plan for a week
- [ ] `POST /meal-plans/:id/entries` — add recipe to a day + meal type + serving size
- [ ] `GET /meal-plans/:id` — full plan with recipe details
- [ ] `GET /meal-plans/current` — this week's plan (derived from current date)
- [ ] `GET /meal-plans/:id/pantry-impact`:
  - Aggregate all ingredient quantities across all entries (scaled by servings)
  - Compare to current pantry state
  - Return: fully covered / partially covered (shortage) / not in pantry
- [ ] `DELETE /meal-plans/:id/entries/:entry_id`
- [ ] GraphQL query: `mealPlan(weekOf: ...)` with nested recipe + pantry impact

**Testing**
- [ ] Integration test: create plan with 3 recipes → pantry impact correctly aggregates quantities

**Definition of done:** Plan a week of dinners. See exactly which ingredients you have and which you're short on.

---

## Phase 5 — Shopping List (1–2 weeks)

**Goal:** From a meal plan (or ad-hoc recipe list), generate a shopping list that accounts for what you already have. Export to PDF and Apple Notes format.

**Service:** `services/api/` — Java + Spring Boot

### Tasks

- [ ] Schema migrations: `shopping_lists`, `shopping_list_items`
- [ ] `POST /shopping-lists` from meal plan — compute `quantity_to_buy` = needed - in_pantry per ingredient
- [ ] `POST /shopping-lists` from recipe IDs + servings map
- [ ] Consolidate duplicate ingredients across recipes
- [ ] Group items by store section (from `ingredients.category`)
- [ ] Attach `estimated_cost` from `ingredient_costs` per item; compute total
- [ ] `PATCH /shopping-lists/:id/items/:item_id` — check/uncheck item
- [ ] `GET /shopping-lists/:id/export/pdf` — generate PDF (reportlab or WeasyPrint)
- [ ] `GET /shopping-lists/:id/export/notes` — plain text formatted for Apple Notes

**Definition of done:** Select a week's meal plan → generate shopping list → see total estimated cost → export as PDF.

---

## Phase 6 — AI Layer (3–4 weeks)

**Goal:** Add semantic search, natural language queries, and the meal planner agent. Build RAG pipeline from scratch first.

**Services:** `services/ai/` (Python 3.12 + FastAPI) + `services/api/` AI route additions (Java + Spring Boot)

### Week 1 — RAG from Scratch

- [ ] Implement raw embedding pipeline (no framework):
  1. Chunk recipe text (title, ingredients, steps)
  2. Generate embeddings via Ollama (`nomic-embed-text`)
  3. Store in Qdrant
  4. Query: embed input → cosine similarity search → return top-k recipe IDs
  5. Fetch recipe details from PostgreSQL
  6. Inject into Claude prompt → generate response
- [ ] `POST /ai/search` — natural language recipe search
- [ ] `POST /ai/can-make` — "what can I make tonight?" with pantry awareness

### Week 2 — LlamaIndex Integration

- [ ] Refactor RAG pipeline to use LlamaIndex:
  - `SimpleDirectoryReader` / custom document loader for recipes
  - `VectorStoreIndex` backed by Qdrant
  - `RetrieverQueryEngine` with custom prompt template
- [ ] Compare: verify LlamaIndex output matches the from-scratch implementation
- [ ] Document what LlamaIndex abstracted (in `docs/adr/006-rag-framework.md`)

### Week 3 — NL Meal Planner Agent

- [ ] Build LangChain ReAct agent with tools:
  - `search_recipes(query, filters)` → calls Qdrant + Elasticsearch
  - `query_pantry()` → reads current pantry state
  - `check_schedule(week)` → reads existing meal plan entries
  - `write_meal_plan(entries)` → creates meal plan entries
- [ ] `POST /ai/meal-plan` — NL prompt → agent → meal plan created
- [ ] Guardrails: validate agent-produced meal plan against schema before writing

### Week 4 — Remaining AI Features

- [ ] `POST /ai/substitute` — few-shot Claude prompt for ingredient substitution
- [ ] Add AI Safety: LLM Guard on user-provided `query` fields (prompt injection scan)
- [ ] Observability: log Claude API latency, token usage, agent step count per request

**Definition of done:** "Plan me 5 weeknight dinners under 45 minutes using what I have, no seafood" → agent creates a meal plan → shopping list generated → PDF exported.

---

## Phase 7 — Production Hardening (1–2 weeks)

**Goal:** CI/CD pipeline, live deployment, GitHub repo polished and published.

**Services:** All — Java API service + Python scraper / AI / worker

### Tasks

**CI/CD (GitHub Actions)**
- [ ] On PR: ruff lint, mypy type check, pytest
- [ ] On merge to main: build Docker images, push to GitHub Container Registry
- [ ] On release tag: deploy to production (Fly.io or Hetzner VPS)

**Deployment**
- [ ] `docker-compose.prod.yml` — production overrides (no dev ports, resource limits)
- [ ] Secrets management via environment variables (never in repo)
- [ ] Nginx reverse proxy with TLS (Let's Encrypt via Certbot)
- [ ] Automated daily Postgres backup (pg_dump → encrypted → remote storage)

**Observability**
- [ ] Prometheus metrics endpoint on API service
- [ ] Grafana dashboard: request rate, error rate, scrape job success rate, Claude API latency
- [ ] Structured logging (JSON) with request ID propagation across services

**GitHub Repo Polish**
- [ ] README with architecture diagram, tech stack table, design decisions, how to run
- [ ] `docs/adr/` — all ADRs written and reviewed
- [ ] GitHub Issues created for known limitations and future work
- [ ] Demo video or live deployment link in README
- [ ] MIT license

**Definition of done:** `git clone` → `docker-compose up` → app is fully functional. README explains the non-trivial design decisions a senior engineer would care about.
