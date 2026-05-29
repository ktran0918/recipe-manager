# ADR 001 — Primary Database: PostgreSQL

**Status:** Accepted

## Context

We need a primary store for users, households, recipes, ingredients, pantry state, and meal plans. The data is relational (recipes have ingredients, households have members, meal plans have entries). Pantry deductions require atomicity across multiple ingredient rows.

## Decision

Use **PostgreSQL**.

## Rationale

- Recipe and pantry data is highly structured and relational — JOINs between recipes, ingredients, and pantry items are frequent and natural
- `POST /pantry/cook` requires an atomic transaction across multiple pantry_events rows — ACID guarantees are essential
- Event sourcing for the pantry log is clean in PostgreSQL (append-only table, indexed by household + ingredient + time)
- Strong migration tooling (Alembic), mature Python ecosystem (SQLAlchemy, asyncpg)

## Alternatives Rejected

**MongoDB:** Flexible schema is not needed — recipe and pantry data has a well-defined structure that benefits from enforcement. The lack of multi-document ACID transactions (without replica sets) makes the cook deduction flow awkward.
