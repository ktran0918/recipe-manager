# ADR 007 — Local vs Cloud LLM Boundary

**Status:** Accepted (amended — recipe parsing moved local-first)

## Context

The project uses LLMs in six distinct places across the scraper and AI services. Each differs in:
- **Output contract**: strict JSON schema vs free-form natural language
- **Reasoning depth**: single extraction vs multi-step agent planning
- **Call volume**: every recipe parse vs on-demand user queries
- **Latency tolerance**: async background job vs interactive response

The two available runtimes are:
- **Claude API** (`claude-haiku-4-5`): cloud, billed per token, reliable structured output and tool-calling, ~$0.001–0.003 per parse at Haiku pricing
- **Ollama** (local, `qwen2.5:14b` + `nomic-embed-text`): on-device, zero per-call cost, 8–15 tok/s on AMD Radeon 780M iGPU or Apple Silicon, no data leaves the machine

Hardware context: the production target is a mini PC with an AMD Ryzen 9 8945HS (Radeon 780M iGPU, RDNA 3) and 32 GB DDR5 unified RAM. Ollama can use the iGPU via ROCm on Linux.

**Amendment (post-EP2-03):** the project's stated priority shifted toward maximizing local/open-weight model usage — both to minimize cost and as a deliberate learning goal (deploying and operating local models is part of what this project is for, per `CLAUDE.md`'s "learn by building" purpose). Recipe parsing was re-examined against that priority and moved local-first; see the updated rationale below and ADR-004 for the pipeline detail.

## Decision

| Task | Runtime | Model |
|---|---|---|
| Recipe parsing (URL → structured JSON) | **Local, cloud escalation** | Ollama `qwen2.5:14b` primary; `claude-haiku-4-5` on repeated Guardrails failure |
| Embedding — ingest + query | Local | Ollama `nomic-embed-text` |
| RAG response generation | Local | Ollama `qwen2.5:14b` |
| Ingredient substitution (few-shot) | Local | Ollama `qwen2.5:14b` |
| NL meal planner agent (ReAct) | Cloud | `claude-haiku-4-5` |
| Shopping list consolidation | Algorithmic | — |

Five of six tasks now run local by default; only the NL meal planner agent is cloud-only, and recipe parsing's cloud tier is an escalation path rather than its default route.

## Rationale

**Recipe parsing moves local-first** because the earlier all-cloud design conflated two separate concerns: output accuracy (which does determine core product value — a degraded parse corrupts pantry deduction, shopping lists, and meal planning) and which runtime produces that accuracy. Guardrails AI was already validating every parse before it reached PostgreSQL; that validation gate makes it safe to let a local model attempt the extraction first, retry once locally on failure, and escalate to Haiku only after two consecutive failures. `qwen2.5:14b` is the same model already resident for RAG and substitution — Ollama loads models on demand rather than keeping several resident simultaneously, so routing a third task through it costs no additional memory budget beyond what this ADR already accounts for. The async queue means latency is not user-facing at either tier, and the escalation path means a bad parse still can't reach PostgreSQL without surviving Guardrails twice.

**The NL meal planner agent stays cloud** because local 7–14B models fail predictably at multi-step ReAct tool-calling. They hallucinate tool call schemas, drop constraints mid-chain ("no seafood" forgotten by step 3), and produce invalid JSON for `write_meal_plan`. Haiku handles this reliably. The agent runs at most a handful of times per week per household, so the call cost is trivial.

**RAG response generation and ingredient substitution move to local** because the context is well-bounded and the output is a natural language paragraph — not a structured schema. After Qdrant returns top-k recipe snippets, summarising "here are three pasta dishes under 30 minutes" is within `qwen2.5:14b`'s capability. 8–15 tok/s produces a response in 5–15 seconds, which is acceptable for an interactive query where the retrieval step already surfaced the candidates.

**Embeddings are local** per ADR rationale already established: volume makes API pricing untenable, and `nomic-embed-text` quality is competitive with hosted alternatives for recipe-domain text.

**Shopping list consolidation requires no LLM** — it is pure arithmetic: `sum(quantity_g for all pasta entries)`. Language judgment is not needed.

## Trade-offs

- **Accuracy ceiling on local tasks**: `qwen2.5:14b` may produce lower quality substitution suggestions, RAG responses, or recipe parses than Sonnet or Opus on ambiguous cases. For substitution and RAG, accept this: the user can always ignore a bad suggestion. For recipe parsing specifically, the ceiling is covered rather than merely accepted — Guardrails' validate-retry-escalate path (ADR-004) means a local miss gets a second local attempt and then a cloud attempt before it can reach PostgreSQL.
- **Inference speed on iGPU**: ROCm for RDNA 3 integrated graphics (gfx1103) is supported but less mature than NVIDIA CUDA paths. If ROCm setup proves unreliable, Ollama's CPU fallback runs at ~4–8 tok/s — slower but still functional for async use cases. Verify ROCm on first server setup.
- **Model residency**: `qwen2.5:14b` at Q4 quantisation occupies ~9 GB of the 32 GB shared pool. This is accounted for in the server memory budget alongside the full Docker Compose stack. Routing recipe parsing through the same model adds no additional residency cost — it's the same weights already budgeted, loaded on demand.
- **Escalation cost is no longer hypothetical for recipe parsing**: unlike RAG/substitution (where cloud fallback is a config change held in reserve), recipe parsing's cloud tier runs routinely — whenever local extraction fails Guardrails twice. Monitor the escalation rate; a consistently high rate signals `qwen2.5:14b` is miscalibrated for this task and the boundary should be revisited.
- **Cloud fallback**: if local model quality proves insufficient for RAG responses, the swap to `claude-haiku-4-5` is a single config change in the AI service.

## Alternatives Rejected

**All local (including the NL meal planner agent)**: Local models at 7–14B scale reliably fail the agent's multi-step tool-calling requirement. Testing with `qwen2.5:32b` might close this gap, but that model occupies ~20 GB — leaving only 12 GB for all other services, which makes Elasticsearch and the Spring Boot stack tight. Not worth the operational risk for a background-queued task that calls the API at most a few times per week. This is the one task that stays cloud-only even under the project's local-first priority, specifically because the failure mode (hallucinated tool schemas, dropped constraints) isn't one a validation gate can catch the way Guardrails catches a bad recipe parse.

**All cloud**: Puts every query — including simple "what can I substitute for sour cream?" requests and every recipe parse — through the billing meter. More critically, it introduces per-call latency for interactive features and a hard dependency on Anthropic API availability for both the RAG search path and recipe ingestion.

**Ollama `qwen2.5:32b` for the agent**: Better local tool-calling quality than 14B, but ~20 GB model weight leaves the server memory budget uncomfortably tight. Haiku is more reliable and cheaper to operate than a larger local model at the scale this project targets.

**Use `claude-sonnet-4-6` instead of Haiku for the agent**: Haiku handles ReAct tool-calling reliably. Sonnet would improve output quality marginally at 5–10× the token cost. Revisit if Haiku proves insufficient.

**Keep recipe parsing cloud-first (previous decision)**: Superseded — see ADR-004's amendment. The original reasoning (accuracy determines product value) remains true, but conflated accuracy with runtime; the Guardrails validation gate was already strong enough to make a local-first attempt safe.