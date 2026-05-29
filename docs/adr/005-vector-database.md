# ADR 005 — Vector Database: Qdrant

**Status:** Accepted

## Context

Semantic recipe search and the RAG pipeline require storing and querying high-dimensional embeddings. We need a vector database that can be self-hosted on macOS and Linux servers.

## Decision

Use **Qdrant** for production deployments, with **Chroma** for local development.

## Rationale

**Qdrant (production):**
- Rust-based: excellent performance and low memory footprint
- First-class Docker support — single container, persistent volume, works identically on macOS and Linux
- REST and gRPC APIs — fits naturally into the existing service architecture
- Payload filtering: filter by `household_id`, `complexity`, `cook_time` *at the vector search level* — combines semantic similarity with hard constraints in one query
- Production-grade: supports snapshots, collection aliases, quantization for memory efficiency
- Self-hosted with no cloud dependency — aligns with our local-first philosophy

**Chroma (local dev):**
- Zero-config, in-process Python library — no Docker container needed for development
- Same Python API surface, making it easy to swap for Qdrant in production configs

## Alternatives Rejected

**Pinecone:** Fully managed, no self-hosting option. Introduces a cloud dependency and ongoing cost. Rejected — self-hostability is a hard requirement.

**FAISS:** Meta's library, not a server. No built-in persistence, no REST API, no filtering on metadata. Good for learning the math, not suitable as the production store.

**Weaviate:** Self-hostable, but more complex to configure and operate (Go-based, more moving parts). GraphQL-only query API is less ergonomic than Qdrant's REST.
