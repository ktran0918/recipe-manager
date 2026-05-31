# Project Brief — Recipe Manager & Meal Planner

**Domain:** Food / Personal Productivity  
**Estimated time:** 16–23 weeks  
**Status:** Active — Phase 1 in progress

---

## Summary

A multi-user household platform for bookmarking recipes, managing a pantry, planning meals, and generating shopping lists. The system is designed around two non-trivial engineering challenges: an LLM-powered async recipe parsing pipeline, and a RAG-based AI layer for semantic search and natural language meal planning.

Built as a learning and portfolio project. Goals: practice backend and AI/ML engineering patterns on a real problem; produce a GitHub-published demo with production-quality architecture.

---

## Features

### Recipe Management
- Scrape and parse recipes from any URL — async pipeline, no CSS selectors; Claude API + structured output
- Manual recipe entry
- Full CRUD with ingredient quantities, steps, cook/prep time, servings, complexity, cuisine, occasions, diet tags
- Serving size multiplier: scales all ingredient quantities and estimated costs dynamically
- Ingredient cost estimates from external API (per-household overridable)

### Cook Mode
- Distraction-free view when a recipe is selected to cook: only cook time, scaled ingredients, original steps, serving size, nutrition, and link to source
- Selection stored per-user in Redis (24h TTL) — persists across devices
- Deselect at any time

### Ingredient Substitutions
- Recipe-scoped substitutions: "use Greek yogurt instead of sour cream *in this dip*"
- Three sources: `manual` (user-added), `ai` (Claude-suggested on demand), `recipe` (extracted from recipe text during scraping)
- Conversion ratio per substitution (e.g. butter → coconut oil at 0.75× ratio)
- Filter substitutes by what's currently in the pantry

### Pantry
- Manual entry + barcode scanning (Open Food Facts API)
- Quantities tracked per ingredient per unit
- **Event sourcing:** `pantry_events` is the immutable append-only log; `pantry_items` is a derived projection — all writes go through events first
- On cook: deduct all ingredient quantities atomically; 422 with shortage list if insufficient
- Full change log per household
- "What can I make right now?" query

### Meal Planning
- Weekly meal planner — plan recipes per day and meal type
- Pantry impact view: shows covered, partially covered, and missing ingredients across the full week's plan

### Shopping List
- Generate from meal plan or ad-hoc recipe selection
- Accounts for what's already in the pantry (quantity_to_buy = needed − in_pantry)
- Grouped by store section; total estimated cost
- Export: PDF and Apple Notes plain-text format

### AI Layer (Phase 6)
- **Semantic recipe search:** embed query → cosine similarity search in Qdrant → Claude generates natural language response
- **"What can I make tonight?":** pantry-aware semantic search
- **NL meal planner agent:** LangChain ReAct agent with 4 tools (search_recipes, query_pantry, check_schedule, write_meal_plan) — accepts a natural language prompt, creates a meal plan
- **On-demand substitution suggestions:** few-shot Claude prompt for ingredient substitutions; saved back to `recipe_ingredient_substitutions` with `source='ai'`
- RAG pipeline built from scratch first, then refactored to LlamaIndex (intentional learning exercise)

### Nutrition
- Extracted from recipe source pages during scraping (Claude schema includes optional nutrition object)
- Displayed in cook mode view

---

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| API service | Java 21 + Spring Boot 3 | OOP/DI discipline; Spring Data JPA, Spring Security, Spring AMQP, Flyway |
| Scraper service | Python 3.12 | aio-pika, httpx, Playwright, trafilatura, Anthropic SDK, Guardrails AI |
| AI service | Python 3.12 + FastAPI | LangChain, LlamaIndex, Qdrant client |
| Worker service | Python 3.12 | APScheduler — cost refresh, cleanup jobs |
| Primary DB | PostgreSQL 16 | ACID transactions; event sourcing for pantry |
| Cache / sessions | Redis 7 | JWT refresh tokens, cook selection, cost cache, dedup, job status |
| Message queue | RabbitMQ 3 | Async scrape job queue with dead-letter exchange |
| Full-text search | Elasticsearch 8.13 | Faceted recipe search (occasion, complexity, cuisine, cook time) |
| Vector DB | Qdrant | Semantic recipe search; Chroma locally for zero-config dev |
| Embeddings | Ollama (`nomic-embed-text`) | Local — not cloud API; cost justified at household scale |
| LLM | Claude API (Anthropic) | Recipe parsing (~$0.02–0.03/URL at Sonnet pricing), NL agent, substitution suggestions |
| Containerization | Docker Compose | All infra + app services; production via docker-compose.prod.yml |

The API/Python split is intentional: Java enforces OOP and DI discipline where it matters; Python has first-class support for the AI/ML tooling (official Anthropic SDK, LangChain, LlamaIndex). Services communicate over HTTP and RabbitMQ only — the languages are fully independent.

---

## Engineering Concepts Covered

| Concept | Where |
|---|---|
| REST API + GraphQL | API service — recipes, pantry, meal plans; GraphQL for nested queries |
| OAuth2 + JWT | Google OAuth2 for identity; project-issued JWTs for sessions |
| PostgreSQL | Primary data store — all relational data |
| Redis | Sessions, cook selection, cost cache, scrape dedup, job status |
| RabbitMQ | Async scraping pipeline; dead-letter exchange for failed jobs |
| Event sourcing | Pantry — `pantry_events` log → `pantry_items` projection |
| Elasticsearch | Full-text + faceted recipe search |
| Vector DB + embeddings | Qdrant + Ollama for semantic similarity search |
| RAG pipeline | Built from scratch (Phase 6 Week 1), then refactored to LlamaIndex (Week 2) |
| LLM tool-calling | Claude API with strict JSON schema for recipe parsing + nutrition extraction |
| ReAct agent | LangChain NL meal planner with 4 tools |
| Background jobs | APScheduler worker — ingredient cost refresh, cleanup |
| Async job queue | RabbitMQ consumer pattern in Python (aio-pika) |
| CQRS (light) | Recipe ingestion pipeline (write path) vs. meal planning queries (read path) |
| External API integration | Open Food Facts (barcode scan), grocery cost APIs |
| WebSockets | Real-time scrape job status via Redis pub/sub |
| Circuit Breaker | On external URL scraping and cost API calls |
| Observability | Prometheus metrics, Grafana dashboard, structured JSON logging with request ID propagation |
| Docker Compose | Full local dev environment; production hardening in Phase 7 |

---

## Resume Blurb

> **Recipe Manager & Meal Planner** — Designed and built a multi-user household platform with an LLM-powered recipe parsing pipeline (Claude API + structured output + Guardrails AI validation), recipe-scoped ingredient substitution extraction, semantic recipe search (RAG from scratch → LlamaIndex, Qdrant vector DB, Ollama embeddings), pantry event sourcing, and a LangChain ReAct meal planner agent. Stack: Java 21 + Spring Boot 3 (API), Python 3.12 (scraper, AI, worker), PostgreSQL, Redis, RabbitMQ, Elasticsearch, Docker Compose.
