# Claude API Cost Estimates

API usage is billed separately from Claude Pro. Verify current rates at console.anthropic.com.

## Model Routing

Only the NL meal planner agent runs on the Claude API as its primary route. Recipe parsing runs locally by default and only touches the Claude API on escalation — everything else runs on Ollama with no billing at all.

| Task | Runtime | Model | Why |
|---|---|---|---|
| Recipe parsing (URL → structured JSON) | **Local, cloud escalation** | Ollama `qwen2.5:14b` primary; `claude-haiku-4-5` only if Guardrails rejects the local parse twice | Guardrails already gates every parse before it reaches PostgreSQL, which makes a local-first attempt safe — see ADR-004. |
| NL meal planner agent (ReAct) | Cloud | `claude-haiku-4-5` | Multi-step tool-calling; local models drop constraints mid-chain. |
| RAG response generation | Local | Ollama `qwen2.5:14b` | Bounded summarisation task; no billing. |
| Ingredient substitution | Local | Ollama `qwen2.5:14b` | Small, fixed context; few-shot quality is sufficient. |
| Embedding (ingest + query) | Local | Ollama `nomic-embed-text` | High volume; API pricing not justified. |
| Shopping list consolidation | Algorithmic | — | Pure unit math; no LLM needed. |

See `docs/adr/004-llm-recipe-parsing.md` and `docs/adr/007-local-vs-cloud-llm-boundary.md` for full reasoning.

---

## Cost Per Operation

Haiku 4.5 pricing (~$0.80/MTok input, ~$4.00/MTok output). Verify at console.anthropic.com.

| Operation | Model | ~Input tokens | ~Output tokens | ~Cost |
|---|---|---|---|---|
| Recipe parsing — local parse (the common case) | Local | — | — | $0 |
| Recipe parsing — cloud escalation (Guardrails rejects local parse twice) | Haiku | 4,000 | 800 | ~$0.006 |
| Meal planner agent (full run, ~4 steps) | Haiku | 10,000 | 2,000 | ~$0.016 |
| RAG response ("what can I make?") | Local | — | — | $0 |
| Ingredient substitution | Local | — | — | $0 |

The escalation rate — what fraction of parses fail Guardrails twice locally — isn't measured yet; the monthly estimates below assume **~15%** as a starting planning number. Once EP2-04 ships, replace this with the observed rate.

---

## Monthly Estimates

### Development (active coding + testing)

| Activity | ~Frequency | ~Escalated | ~Monthly |
|---|---|---|---|
| Recipe parsing (pipeline testing) | 20/day | ~15% → 3/day | ~$0.54 |
| Meal planner agent testing | 10 runs/day | — | ~$4.80 |
| RAG queries, substitutions, local parses | 50+/day | — | $0 (local) |
| **Subtotal** | | | **~$5.34** |
| With prompt caching on system prompts (~60% reduction) | | | **~$2–3** |

### Production (household, 2–4 users)

| Activity | ~Frequency | ~Escalated | ~Monthly |
|---|---|---|---|
| Recipe parsing | 5/day | ~15% → <1/day | ~$0.18 |
| Meal planner agent | 3/week | — | ~$0.20 |
| Daily queries + substitutions + local parses | 10+/day | — | $0 (local) |
| **Total** | | | **~$0.40** |

---

## Cost Reduction

**1. Prompt caching** — cache the agent system prompt (and the escalation-tier recipe extraction schema, for the minority of parses that reach it); reduces input token cost ~60–80% on repeated calls.

**2. Redis dedup** — 7-day cache on scraped URLs prevents re-parsing the same recipe.

**3. The local-first design is itself the primary cost reduction** — recipe parsing's default cost is $0; the Haiku estimate above only applies to the fraction of parses the local model can't get through Guardrails. Driving that escalation rate down (better prompts, a larger local model if memory allows) further reduces cost without touching the NL agent, which is the one task that stays cloud-only by design.

---

## Budget

Production cost for a household is **well under $1/month** even before accounting for prompt caching — the local-first design makes recipe parsing's cloud cost a rounding error rather than a line item. Development cost stays in the same **$5–10/month** range as agent testing (the one task that was always cloud-only) continues to dominate the bill.
