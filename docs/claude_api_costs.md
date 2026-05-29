# Claude API Cost Estimates

API usage is billed separately from Claude Pro. Verify current rates at console.anthropic.com.

## Model Routing

| Task | Model | Reason |
|---|---|---|
| Recipe parsing, meal planner agent, RAG generation | Sonnet 4.6 | Complex reasoning, structured output |
| Substitutions, shopping list, simple queries | Haiku 4.5 | Simple tasks — ~5x cheaper |

## Cost Per Operation

| Operation | Model | ~Input tokens | ~Output tokens | ~Cost |
|---|---|---|---|---|
| Recipe parsing (URL → structured JSON) | Sonnet | 4,000 | 800 | $0.024 |
| Meal planner agent (full run, ~4 steps) | Sonnet | 10,000 | 2,000 | $0.060 |
| "What can I make?" RAG query | Haiku | 1,500 | 400 | $0.003 |
| Ingredient substitution | Haiku | 400 | 150 | $0.001 |
| Shopping list optimization | Haiku | 800 | 400 | $0.002 |

## Monthly Estimates

### Development (active coding + testing)

| Activity | ~Frequency | ~Monthly |
|---|---|---|
| Recipe parsing (pipeline testing) | 20/day | $14 |
| Meal planner agent testing | 10 runs/day | $18 |
| Queries, substitutions, misc | 50/day | $5 |
| **Subtotal** | | **~$37** |
| With prompt caching on system prompts | | **~$25** |

### Production (household, 2–4 users)

| Activity | ~Frequency | ~Monthly |
|---|---|---|
| Recipe parsing | 5/day | $3.50 |
| Meal planner agent | 3/week | $2.50 |
| Daily queries + substitutions | 10/day | $1.00 |
| **Total** | | **~$7–10** |

## Cost Reduction

**1. Prompt caching** — cache the recipe extraction schema and agent system prompt; reduces input cost ~80% on repeated calls after the first.

**2. Local 13B for high-frequency tasks** — route substitutions, simple pantry queries, and anything called >20x/day to Ollama instead of Haiku.

**3. Redis dedup** — 7-day cache on scraped URLs prevents re-parsing the same recipe.

## Budget

$30–40/month covers active development comfortably. Production cost for a household is ~$7–10/month.
