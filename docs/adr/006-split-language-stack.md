# ADR 006 — Split Language Stack: Java + Spring Boot for API, Python for AI Services

**Status:** Accepted

## Context

The project has four services with distinct responsibilities:

- **API service** — REST + GraphQL, auth, business logic orchestration, RabbitMQ job publishing
- **Scraper service** — async RabbitMQ consumer, URL fetch, LLM recipe parsing, embedding generation
- **AI service** — RAG pipeline, semantic search, NL meal planner agent
- **Worker service** — scheduled background jobs (cost refresh, cleanup, re-indexing)

Two competing goals must both be satisfied: practicing backend engineering patterns (OOP, DI, SOLID, layered architecture) and practicing AI/ML engineering patterns (LLM pipelines, RAG, vector search, agent design). No single language serves both goals equally well today.

## Decision

Split the stack by service responsibility:

- **API service (`services/api/`)** — Java 21 + Spring Boot 3
- **Scraper service (`services/scraper/`)** — Python 3.12 + aio-pika
- **AI service (`services/ai/`)** — Python 3.12 + FastAPI
- **Worker service (`services/worker/`)** — Python 3.12

Services communicate over HTTP (API ↔ AI service) and RabbitMQ (API → Scraper). The language boundary is clean — no shared code, only shared infrastructure.

## Rationale

**Python on the AI-facing services:**
- Anthropic Python SDK is the official, primary SDK — the TypeScript SDK is the only other first-class target
- LangChain, LlamaIndex, Guardrails AI, trafilatura, and Ollama clients are all Python-first; TypeScript ports exist but lag behind
- Phase 6 (RAG from scratch → LlamaIndex refactor) and Phase 2 (LLM parsing pipeline) are where the most learning value lives — using Python means real library support rather than manual REST calls
- The scraper and worker services are I/O-bound pipelines with no web framework requirements; Python async (aio-pika, asyncio) is natural and lightweight here

**Java + Spring Boot on the API service:**
- The API service is where OOP patterns matter most: layered architecture (controller → service → repository), interface-based programming, DI-wired implementations, clean separation of concerns
- Spring Boot 3 + Java 21 gives virtual threads (Project Loom) for async-friendly blocking code, records for immutable DTOs, sealed classes for domain modelling
- Spring Data JPA, Spring Security, Spring AMQP (for publishing to RabbitMQ), Spring Data Redis, and Spring Data Elasticsearch are all mature and well-integrated
- Spring Boot is the industry benchmark for enterprise backend — the resume signal is clear
- The API service has no direct AI/LLM dependency; it delegates scraping to RabbitMQ and AI queries to the AI service over HTTP

## Trade-offs

- Two toolchains, two CI pipeline configurations, two sets of coding conventions to maintain
- No shared model types across service boundaries — each service defines its own representation of shared concepts (recipe, pantry item, etc.), serialized as JSON over HTTP/AMQP
- Developers need working knowledge of both Java and Python

## Alternatives Rejected

**Python everywhere:** FastAPI is lightweight and doesn't enforce layered architecture or interface-based programming. The OOP/DI learning goal for the API service would not be met.

**Java everywhere:** Spring AI is less mature than Python's LLM tooling. Phase 6 would require building the RAG pipeline and NL agent without LangChain or LlamaIndex, significantly increasing implementation effort in the most learning-dense phase.
