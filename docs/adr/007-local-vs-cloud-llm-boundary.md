# ADR 007 — Local vs Cloud LLM Boundary

**Status:** Accepted

## Context

The project uses LLMs in six distinct places across the scraper and AI services. Each differs in:
- **Output contract**: strict JSON schema vs free-form natural language
- **Reasoning depth**: single extraction vs multi-step agent planning
- **Call volume**: every recipe parse vs on-demand user queries
- **Latency tolerance**: async background job vs interactive response

The two available runtimes are:
- **Claude API** (`claude-haiku-4-5-20251001`): cloud, billed per token, reliable structured output and tool-calling, ~$0.001–0.003 per parse at Haiku pricing
- **Ollama** (local, `qwen2.5:14b` + `nomic-embed-text`): on-device, zero per-call cost, 8–15 tok/s on AMD Radeon 780M iGPU or Apple Silicon, no data leaves the machine

Hardware context: the production target is a mini PC with an AMD Ryzen 9 8945HS (Radeon 780M iGPU, RDNA 3) and 32 GB DDR5 unified RAM. Ollama can use the iGPU via ROCm on Linux.

## Decision

| Task | Runtime | Model |
|---|---|---|
| Recipe parsing (URL → structured JSON) | Cloud | `claude-haiku-4-5-20251001` |
| Embedding — ingest + query | Local | Ollama `nomic-embed-text` |
| RAG response generation | Local | Ollama `qwen2.5:14b` |
| Ingredient substitution (few-shot) | Local | Ollama `qwen2.5:14b` |
| NL meal planner agent (ReAct) | Cloud | `claude-haiku-4-5-20251001` |
| Shopping list consolidation | Algorithmic | — |

## Rationale

**Recipe parsing stays cloud** because output accuracy directly determines core product value. Parsing "2 cloves garlic, finely minced" into `{"quantity": 2, "unit": "clove", "preparation": "finely minced"}` is exactly the structured extraction task Haiku was optimised for. A degraded parse (wrong quantity, merged ingredients) corrupts every downstream feature — pantry deduction, shopping list, meal planning. The async queue means latency is not user-facing. Haiku at ~$0.001–0.003 per URL is negligible at household scale.

**The NL meal planner agent stays cloud** because local 7–14B models fail predictably at multi-step ReAct tool-calling. They hallucinate tool call schemas, drop constraints mid-chain ("no seafood" forgotten by step 3), and produce invalid JSON for `write_meal_plan`. Haiku handles this reliably. The agent runs at most a handful of times per week per household, so the call cost is trivial.

**RAG response generation and ingredient substitution move to local** because the context is well-bounded and the output is a natural language paragraph — not a structured schema. After Qdrant returns top-k recipe snippets, summarising "here are three pasta dishes under 30 minutes" is within `qwen2.5:14b`'s capability. 8–15 tok/s produces a response in 5–15 seconds, which is acceptable for an interactive query where the retrieval step already surfaced the candidates.

**Embeddings are local** per ADR rationale already established: volume makes API pricing untenable, and `nomic-embed-text` quality is competitive with hosted alternatives for recipe-domain text.

**Shopping list consolidation requires no LLM** — it is pure arithmetic: `sum(quantity_g for all pasta entries)`. Language judgment is not needed.

## Trade-offs

- **Accuracy ceiling on local tasks**: `qwen2.5:14b` may produce lower quality substitution suggestions than Sonnet or Opus on ambiguous cases. Accept this: the user can always ignore a bad suggestion or manually enter a substitution. The cost to be wrong is low.
- **Inference speed on iGPU**: ROCm for RDNA 3 integrated graphics (gfx1103) is supported but less mature than NVIDIA CUDA paths. If ROCm setup proves unreliable, Ollama's CPU fallback runs at ~4–8 tok/s — slower but still functional for async use cases. Verify ROCm on first server setup.
- **Model residency**: `qwen2.5:14b` at Q4 quantisation occupies ~9 GB of the 32 GB shared pool. This is accounted for in the server memory budget alongside the full Docker Compose stack.
- **Cloud fallback**: if local model quality proves insufficient for RAG responses, the swap to `claude-haiku-4-5-20251001` is a single config change in the AI service.

## Alternatives Rejected

**All local**: Local models at 7–14B scale reliably fail the NL meal planner agent's multi-step tool-calling requirement. Testing with `qwen2.5:32b` might close this gap, but that model occupies ~20 GB — leaving only 12 GB for all other services, which makes Elasticsearch and the Spring Boot stack tight. Not worth the operational risk for a background-queued task that calls the API at most a few times per week.

**All cloud**: Puts every query — including simple "what can I substitute for sour cream?" requests — through the billing meter. More critically, it introduces per-call latency for interactive features and a hard dependency on Anthropic API availability for the RAG search path.

**Ollama `qwen2.5:32b` for the agent**: Better local tool-calling quality than 14B, but ~20 GB model weight leaves the server memory budget uncomfortably tight. Haiku is more reliable and cheaper to operate than a larger local model at the scale this project targets.

**Use `claude-sonnet-4-6` instead of Haiku**: Haiku handles both use cases (structured extraction with a tight schema, ReAct tool-calling). Sonnet would improve output quality marginally at 5–10× the token cost. Revisit if Haiku proves insufficient on either task.