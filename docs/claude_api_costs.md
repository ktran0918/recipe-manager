# Claude API Cost Estimates

API usage is billed separately from Claude Pro. Verify current rates at console.anthropic.com.

## Model Routing

Only two tasks use the Claude API. Everything else runs locally on Ollama.

| Task | Runtime | Model | Why |
|---|---|---|---|
| Recipe parsing (URL → structured JSON) | Cloud | `claude-haiku-4-5-20251001` | Reliable tool-calling with strict JSON schema; accuracy affects core product value. Escalate to Sonnet only if Haiku quality proves insufficient on messy blog HTML. |
| NL meal planner agent (ReAct) | Cloud | `claude-haiku-4-5-20251001` | Multi-step tool-calling; local models drop constraints mid-chain. |
| RAG response generation | Local | Ollama `qwen2.5:14b` | Bounded summarisation task; no billing. |
| Ingredient substitution | Local | Ollama `qwen2.5:14b` | Small, fixed context; few-shot quality is sufficient. |
| Embedding (ingest + query) | Local | Ollama `nomic-embed-text` | High volume; API pricing not justified. |
| Shopping list consolidation | Algorithmic | — | Pure unit math; no LLM needed. |

See `docs/adr/007-local-vs-cloud-llm-boundary.md` for full reasoning.

---

## Cost Per Operation

Haiku 4.5 pricing (~$0.80/MTok input, ~$4.00/MTok output). Verify at console.anthropic.com.

| Operation | Model | ~Input tokens | ~Output tokens | ~Cost |
|---|---|---|---|---|
| Recipe parsing (URL → structured JSON) | Haiku | 4,000 | 800 | ~$0.006 |
| Meal planner agent (full run, ~4 steps) | Haiku | 10,000 | 2,000 | ~$0.016 |
| RAG response ("what can I make?") | Local | — | — | $0 |
| Ingredient substitution | Local | — | — | $0 |

For reference: the same recipe parsing call at Sonnet pricing would cost ~$0.024 — approximately 4× higher.

---

## Monthly Estimates

### Development (active coding + testing)

| Activity | ~Frequency | ~Monthly |
|---|---|---|
| Recipe parsing (pipeline testing) | 20/day | ~$3.60 |
| Meal planner agent testing | 10 runs/day | ~$4.80 |
| RAG queries, substitutions | 50/day | $0 (local) |
| **Subtotal** | | **~$8–9** |
| With prompt caching on system prompts (~60% reduction) | | **~$3–4** |

### Production (household, 2–4 users)

| Activity | ~Frequency | ~Monthly |
|---|---|---|
| Recipe parsing | 5/day | ~$0.90 |
| Meal planner agent | 3/week | ~$0.20 |
| Daily queries + substitutions | 10/day | $0 (local) |
| **Total** | | **~$1–2** |

---

## Cost Reduction

**1. Prompt caching** — cache the recipe extraction schema and agent system prompt; reduces input token cost ~60–80% on repeated calls.

**2. Redis dedup** — 7-day cache on scraped URLs prevents re-parsing the same recipe.

**3. Escalation safety valve** — Haiku handles the happy path. If a parse fails Guardrails validation twice, the scraper can optionally retry with Sonnet rather than surfacing an error to the user. This adds ~$0.024 per escalated URL but keeps failure rates low.

---

## Budget

$10–15/month covers active development comfortably, including heavy agent testing. Production cost for a household is **~$1–2/month** — roughly the cost of a cup of coffee.