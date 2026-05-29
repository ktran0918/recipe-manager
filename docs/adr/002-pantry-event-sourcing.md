# ADR 002 — Pantry Tracking: Event Sourcing

**Status:** Accepted

## Context

Pantry quantities change frequently: users add ingredients, deduct manually, the system deducts on cook, and users correct mistakes. We need to decide between simple CRUD (UPDATE quantity in place) and event sourcing (append events, derive state).

## Decision

Use **event sourcing** for the pantry via `pantry_events`. The `pantry_items` table is a projection — a denormalized read model kept in sync after each event.

## Rationale

- **Audit trail:** Users want to know why their flour went from 1kg to 200g — was it a cook deduction, a manual adjustment, or a mistake? CRUD destroys this history.
- **Undo / correction:** A misfire on `POST /pantry/cook` (wrong serving size) can be corrected by writing a compensating event, not by trying to figure out what the DB looked like before.
- **Replay:** If a bug produces incorrect `pantry_items` state, we can truncate the projection and replay events to reconstruct correct state.
- **AI features:** The event log is a rich data source for usage pattern analysis and anomaly detection later.
- **Learning value:** Implementing event sourcing on a concrete, small domain (pantry) builds the pattern fluency needed for CQRS in the Chat Platform.

## Trade-offs

- Slightly more complex writes (write event + update projection in one transaction)
- `pantry_items` can theoretically drift from `pantry_events` if a bug causes partial writes — mitigated by wrapping both in a single PostgreSQL transaction

## Alternatives Rejected

**Simple CRUD (UPDATE pantry_items.quantity):** Simpler, but loses history. A senior engineer who only knows CRUD pantry management is leaving value on the table — the event log is genuinely useful for the product and for learning.
