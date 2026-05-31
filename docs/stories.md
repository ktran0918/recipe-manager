# Stories

## How to read this

- **Depends on** — cannot start until listed stories are merged to main.
- **Parallel with** — genuinely independent; no shared files, no blocking interface.
- Each story ships as one PR. At senior pace: roughly 1–2 days per story.
- Tests are part of every story, not a follow-up task.
- Assignment and pairing decisions are made by the team at sprint/session time — stories are not pre-assigned.
- Where two stories share a contract (message format, API shape, entity structure), that contract must be agreed and written down before parallel work begins. These are called out explicitly.

---

## Phase 1 — Core Backend
**Stack:** Java 21 + Spring Boot 3 (`services/api/`)

---

### EP1-01: Docker Compose and environment scaffold
**Depends on:** nothing | **Parallel with:** EP1-02

Stand up all infrastructure containers. Verify everything is reachable before application code begins.

**AC:**
- [x] `docker-compose.yml` defines: PostgreSQL 16, Redis 7, RabbitMQ 3 (+ management UI), Elasticsearch 8.13, Qdrant latest
- [x] All containers have `healthcheck` entries; dependent services use `condition: service_healthy`
- [x] `.env.example` documents every env var with an inline comment; no secrets committed
- [x] `docker-compose up` reaches a stable healthy state with no restarts
- [x] All services accessible on their documented ports (5432, 6379, 5672/15672, 9200, 6333)

---

### EP1-02: Spring Boot API service scaffold and CI
**Depends on:** nothing | **Parallel with:** EP1-01

Create the `services/api/` Spring Boot project. Establish the layer structure, error handling, and CI pipeline that all subsequent stories build on.

**AC:**
- [ ] Maven project with Java 21, Spring Boot 3.x; dependencies: Spring Web, Spring Data JPA, Spring Security, Spring AMQP, Spring Data Redis, springdoc-openapi, jjwt, Testcontainers (PostgreSQL, Redis)
- [ ] Package structure establishes layers: `controller`, `service` (interfaces + `impl`), `repository`, `domain` (entities), `dto`, `config`, `exception`
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] `@RestControllerAdvice` returns `{"error": "<message>", "status": <code>}` for all unhandled exceptions
- [ ] `application.yml` reads every config value from env vars with sensible defaults for local dev
- [ ] GitHub Actions workflow: `./mvnw verify` runs Checkstyle + compile + tests on every PR; fails the build on violations
- [ ] Checkstyle config committed at `services/api/checkstyle.xml`

---

### EP1-03: Flyway migrations — Phase 1 schemas
**Depends on:** EP1-01, EP1-02 | **Parallel with:** nothing

**Contract session:** Both devs review `docs/schema.md` and sign off on every column, type, and constraint before this story begins. Downstream stories depend on this schema being stable.

**AC:**
- [ ] Flyway configured (`spring.flyway.*`) to run automatically on startup
- [ ] `V1__users_and_households.sql`: `users`, `households`, `household_members`
- [ ] `V2__recipes.sql`: `recipes`, `ingredients`, `recipe_ingredients`, `recipe_steps`
- [ ] All constraints, indexes, and check constraints match `docs/schema.md` exactly
- [ ] `docker-compose up` + API start applies all migrations cleanly on a blank database
- [ ] Re-running migrations on an already-migrated database is a no-op (Flyway checksum passes)

---

### EP1-04: JPA entities and repositories
**Depends on:** EP1-03 | **Parallel with:** EP1-05

Map every Phase 1 table to a JPA entity and Spring Data repository. No service or controller logic yet.

**AC:**
- [ ] Entities: `User`, `Household`, `HouseholdMember` (composite PK), `Recipe`, `Ingredient`, `RecipeIngredient`, `RecipeStep`
- [ ] Relationships mapped correctly (`@OneToMany(mappedBy=...)`, `@ManyToOne`, `@EmbeddedId` for `HouseholdMember`)
- [ ] `FetchType.LAZY` on all collections; no accidental N+1 loading
- [ ] Spring Data JPA repositories: one interface per entity, typed correctly (e.g., `RecipeRepository extends JpaRepository<Recipe, UUID>`)
- [ ] Custom finder examples: `RecipeRepository.findByHouseholdId(UUID)`, `HouseholdMemberRepository.findByUserId(UUID)`
- [ ] `@DataJpaTest` tests: save and retrieve each entity via its repository; verify relationships load correctly

---

### EP1-05: Spring Security config and JWT filter
**Depends on:** EP1-02 | **Parallel with:** EP1-04

**Contract:** Define the `UserPrincipal` record (fields: `userId`, `householdId`, `role`) before starting. EP1-07 depends on this shape.

Configure the `SecurityFilterChain`. Implement the filter that validates JWTs on every request and populates the `SecurityContext`. No OAuth flow yet — stubs are fine.

**AC:**
- [ ] `SecurityFilterChain` bean: `/auth/**` and `/actuator/health` are public; all other routes require a valid JWT
- [ ] `JwtAuthFilter extends OncePerRequestFilter`: extracts `Authorization: Bearer <token>`, validates signature + expiry with jjwt, sets `SecurityContextHolder` with a `UserPrincipal`
- [ ] Invalid or expired token returns 401 with the standard error JSON format
- [ ] `UserPrincipal` record is the `@AuthenticationPrincipal` type used across all controllers
- [ ] Unit tests: valid token → principal populated correctly; expired token → 401; tampered signature → 401; missing header → 401

---

### EP1-06: Google OAuth2 login and JWT issuance
**Depends on:** EP1-04, EP1-05 | **Parallel with:** EP1-07

Implement the full auth flow: Google redirect → callback → upsert user → issue JWT pair.

**AC:**
- [ ] `GET /auth/google` redirects to Google's OAuth2 consent screen
- [ ] `GET /auth/google/callback` exchanges code for Google profile, upserts `users` row (insert or update on `oauth_id`), issues access JWT (15min) + refresh token (UUID stored in Redis with 7d TTL)
- [ ] JWT payload: `sub` (user UUID), `household_id` (null if no household yet), `role`, `exp`
- [ ] `POST /auth/refresh`: validates refresh token in Redis → issues new access JWT + rotates refresh token; old token deleted
- [ ] `POST /auth/logout`: deletes refresh token from Redis
- [ ] `GET /auth/me`: returns current user profile (requires valid JWT)
- [ ] Unit tests: JWT encode/decode round-trip, expiry, tampered payload; mock Google token exchange
- [ ] Integration test (Testcontainers): mock Google callback → user upserted in DB → JWT returned → `/auth/me` returns correct profile

---

### EP1-07: Household endpoints
**Depends on:** EP1-04, EP1-05 | **Parallel with:** EP1-06

Service + controller layer for household management. Uses `@AuthenticationPrincipal UserPrincipal` from EP1-05; integration tests use `@WithMockUser` until EP1-06 is merged.

**AC:**
- [ ] `POST /households`: creates household, assigns caller as `owner`, generates a random 8-char invite code; returns 409 if user already in a household
- [ ] `GET /households/me`: returns household + member list for the caller's household
- [ ] `PATCH /households/me`: updates `name`; 403 if caller is not owner
- [ ] `POST /households/me/invite`: regenerates invite code (owner only); old code immediately invalid
- [ ] `POST /households/join`: joins by invite code; 404 on invalid code, 409 if already a member
- [ ] `GET /households/me/members`: lists members with roles
- [ ] `DELETE /households/me/members/:userId`: removes member (owner only; 400 if attempting to remove self)
- [ ] `HouseholdService` interface + `HouseholdServiceImpl`; controller depends on interface
- [ ] MockMvc integration tests: every endpoint — happy path + primary error cases

---

### EP1-08: Recipe CRUD endpoints
**Depends on:** EP1-04, EP1-07 | **Parallel with:** EP1-06 (in progress)

Service + controller for manual recipe management. Recipes scoped to `household_id`.

**AC:**
- [ ] `POST /recipes`: creates recipe with nested ingredients + steps in one transaction; returns 201 with full recipe representation
- [ ] `GET /recipes`: lists recipes for caller's household; supports `q` param (PostgreSQL ILIKE on title + description via JPQL); paginated (`page`, `per_page`)
- [ ] `GET /recipes/:id`: returns recipe with full ingredient + step detail; 404 if not in caller's household
- [ ] `PATCH /recipes/:id`: partial update of recipe metadata; household-scoped; 404 on wrong household
- [ ] `DELETE /recipes/:id`: deletes recipe + cascaded ingredients + steps; household-scoped
- [ ] `RecipeService` interface + `RecipeServiceImpl`; controller depends on interface
- [ ] `GET /recipes/:id?servings=2`: returns ingredient quantities scaled to requested servings (multiplier applied in service layer)
- [ ] MockMvc integration tests: full CRUD; cross-household access returns 404; keyword filter returns correct subset; serving size scaling is accurate

---

### EP1-09: Phase 1 end-to-end integration test
**Depends on:** EP1-06, EP1-07, EP1-08

Verify the complete Phase 1 definition of done with a single runnable test suite.

**AC:**
- [ ] Testcontainers integration test: authenticate via mocked OAuth → create household → create recipe with 3 ingredients → list recipes → retrieve recipe detail → verify ingredients and steps correct
- [ ] All Testcontainers (PostgreSQL, Redis) spin up cleanly; test is not flaky
- [ ] `docker-compose up` → Swagger UI at `/swagger-ui.html` → perform the above steps manually ✓
- [ ] CI passes green end-to-end

---

### EP1-10: Recipe selection state
**Depends on:** EP1-08 | **Parallel with:** nothing

Redis-backed per-user cook selection. No DB writes — Redis only.

**AC:**
- [ ] `POST /recipes/:id/select`: validates recipe belongs to caller's household; writes `cook:selection:{user_id}` hash (`recipe_id`, `selected_at`) to Redis with 24h TTL; replaces any existing selection; returns the cook-mode view (calls EP1-11 logic internally)
- [ ] `DELETE /recipes/selection`: deletes `cook:selection:{user_id}` from Redis; 204 if nothing selected (idempotent)
- [ ] `GET /recipes/selection`: returns `{recipe_id, selected_at}` if key exists, `{recipe_id: null}` otherwise
- [ ] Unit tests: select → Redis key written; deselect → key deleted; get with no key → null returned
- [ ] Integration test: select recipe A → get selection returns A; select recipe B → selection is B (old key overwritten); deselect → null

---

### EP1-11: Cook mode view endpoint
**Depends on:** EP1-08, EP1-03 (recipe_nutrition migration must exist even if empty) | **Parallel with:** EP1-10

Returns a condensed recipe view with only essential fields. Nutrition is null until Phase 2 populates it.

**AC:**
- [ ] `GET /recipes/:id/cook-mode?servings=N`: loads recipe + ingredients + steps + nutrition (if present); scales all ingredient quantities by `requestedServings / recipe.servings`; 404 if recipe not in caller's household
- [ ] Response shape matches `docs/api.md`: `recipe_id`, `title`, `prep_time_minutes`, `cook_time_minutes`, `servings`, `ingredients[]`, `directions[]` (original step instructions in order), `nutrition` (null if no row), `source_url` (null for manual recipes)
- [ ] Non-essential fields (`description`, `image_url`, `occasions`, `cuisine`, `diet_tags`, `complexity`) are excluded from the response
- [ ] `servings` param defaults to recipe's default servings if omitted
- [ ] MockMvc integration tests: correct fields returned; excluded fields absent; ingredient quantities scaled correctly; nutrition null when no row exists; source_url present for scraped recipe, null for manual

---

## Phase 2 — Scraping Pipeline
**Stack:** `services/scraper/` (Python 3.12) + `services/api/` additions (Java + Spring Boot)

**Contract session before parallel work:** Agree on (1) RabbitMQ queue name (`scrape.jobs`) and message JSON schema `{job_id, url, household_id}`; (2) `scrape_jobs` table columns and status enum values (`pending → scraping → parsing → embedding → complete → failed`); (3) Redis pub/sub channel for status updates (`job.status.{job_id}`). Write these down in a shared doc or ADR before splitting.

---

### EP2-01: Flyway migration — scrape_jobs schema + Spring Boot publishing
**Depends on:** EP1-08 | **Parallel with:** EP2-02

Add the `scrape_jobs` table and the API endpoint that creates a job and publishes it to RabbitMQ.

**AC:**
- [ ] `V3__scrape_jobs.sql`: `scrape_jobs` table matching `docs/schema.md`
- [ ] `POST /recipes/parse`: validates URL format; checks Redis dedup key `recipe:scraped:{url_hash}` (7d TTL); creates `scrape_jobs` record with `status=pending`; publishes `{job_id, url, household_id}` to `scrape.jobs` queue; returns `{job_id, status, ws_token}`
- [ ] Spring AMQP `RabbitTemplate` configured; `scrape.jobs` queue + dead-letter exchange declared as beans
- [ ] `GET /recipes/jobs/:job_id`: reads `scrape_jobs` row; 404 if not in caller's household
- [ ] Unit test: duplicate URL within 7 days returns 200 with existing `job_id` (no new job created)
- [ ] Integration test: `POST /recipes/parse` → job row exists in DB with `status=pending` → message in RabbitMQ queue

---

### EP2-02: Scraper service scaffold and RabbitMQ consumer
**Depends on:** EP1-01 (infra) | **Parallel with:** EP2-01

Create the Python scraper service. Establish project structure, aio-pika consumer loop, and job lifecycle management.

**AC:**
- [ ] `services/scraper/` project: `pyproject.toml`, ruff + mypy config, pytest setup
- [ ] `aio-pika` consumer: connects to RabbitMQ, pulls from `scrape.jobs`, ACKs on success, NACKs (no requeue) after `MAX_SCRAPE_RETRIES` → job lands in dead-letter queue
- [ ] Job lifecycle: on pickup → update `scrape_jobs.status=scraping` in PostgreSQL via asyncpg; on failure → update `status=failed` + `error` column
- [ ] Structured logging (JSON) with `job_id` on every log line
- [ ] Unit test: consumer processes a fixture message; DB update is called with correct args (mocked DB)
- [ ] Dockerfile added; `docker-compose.yml` updated with `scraper` service

---

### EP2-03: URL fetcher
**Depends on:** EP2-02 | **Parallel with:** EP2-01 (in progress)

Fetch raw HTML from any URL. Handle JS-rendered pages.

**AC:**
- [ ] `httpx` for static pages; `playwright` (async) for JS-rendered pages (detected by attempting static fetch first; fall back if content is too short or obviously JS-gated)
- [ ] Timeout: `SCRAPE_TIMEOUT_SECONDS` (from env)
- [ ] Extracts article text with `trafilatura`; raises if extracted content is below a minimum length threshold
- [ ] Updates `scrape_jobs.status=parsing` after successful fetch
- [ ] Unit tests: static page fixture → correct text extracted; short/empty extraction raises `FetchError`
- [ ] Integration test: fetch a real static recipe URL (test with a known-good fixture HTML file, not live network)

---

### EP2-04: Claude API recipe parser
**Depends on:** EP2-03 | **Parallel with:** EP2-01 (in progress)

Send extracted article text to Claude with a strict JSON schema tool. Validate output with Guardrails AI.

**AC:**
- [ ] `anthropic.messages.create` call with `tools=[recipe_extraction_tool]` (JSON schema matching `docs/schema.md` recipe structure)
- [ ] Extraction schema includes an optional `substitutions` array: each entry has `original_ingredient_name`, `substitute_ingredient_name`, `conversion_ratio` (default 1.0), `notes` — Claude extracts these from recipe text (e.g. "you can use Greek yogurt instead of sour cream")
- [ ] System prompt committed as a constant (not inline string)
- [ ] Guardrails AI validator: required fields present, `servings > 0`, `cook_time_minutes >= 0`, at least one ingredient, at least one step, all ingredient quantities parseable as numbers
- [ ] On validation failure: retry Claude call once with a correction prompt; if still failing, raise `ParseError`
- [ ] Updates `scrape_jobs.status=embedding` on success
- [ ] Unit tests: fixture Claude tool response → parsed correctly into domain object; fixture with substitutions → substitution objects present; missing required field → `ParseError`; guard rails rejection on `servings=0`

---

### EP2-05: PostgreSQL write + ingredient normalisation
**Depends on:** EP2-04 | **Parallel with:** EP2-01 (in progress)

Persist the parsed recipe to PostgreSQL. Normalise ingredients against the shared catalog.

**AC:**
- [ ] Ingredient normalisation: lowercase + singularise `name` → look up `ingredients.normalized_name`; insert if not found
- [ ] Write `recipes`, `recipe_ingredients`, `recipe_steps` rows in a single transaction via asyncpg
- [ ] If parsed substitutions present: normalise original + substitute ingredient names against the catalog; write `recipe_ingredient_substitutions` rows with `source='recipe'` in the same transaction; skip any substitution where either ingredient cannot be resolved
- [ ] Sets `recipes.parsed_at` to now
- [ ] Updates `scrape_jobs.status=embedding`, `scrape_jobs.recipe_id` on commit
- [ ] Integration test (Testcontainers PostgreSQL): parsed fixture recipe → correct rows in all three tables; re-running with same ingredient names reuses existing ingredient rows

---

### EP2-06: Embedding pipeline and Elasticsearch indexing
**Depends on:** EP2-05 | **Parallel with:** nothing in this phase

Generate embeddings for the new recipe and index it for full-text search. Notify the API when complete.

**AC:**
- [ ] Ollama client: `POST /api/embeddings` with `model=nomic-embed-text`; embed concatenation of `title + description + ingredient_names + occasions`
- [ ] Upsert to Qdrant `recipes` collection with payload fields matching `docs/schema.md` (Qdrant section)
- [ ] Index recipe document to Elasticsearch `recipes` index (mappings matching `docs/schema.md`)
- [ ] Update `GET /recipes` in the Spring Boot API to query Elasticsearch instead of PostgreSQL ILIKE (requires this story to be merged first)
- [ ] Publish completion to Redis pub/sub channel `job.status.{job_id}` with `{status: "complete", recipe_id: "..."}`
- [ ] Updates `scrape_jobs.status=complete` in PostgreSQL
- [ ] Unit test: Ollama response fixture → correct Qdrant upsert payload constructed

---

### EP2-07: WebSocket job status push
**Depends on:** EP2-01 | **Parallel with:** EP2-02 through EP2-06

Spring Boot WebSocket endpoint. Subscribes to Redis pub/sub and pushes status events to the waiting client.

**AC:**
- [ ] `WS /ws/jobs/:job_id`: client connects with `ws_token` (validated against Redis); server subscribes to `job.status.{job_id}` Redis channel
- [ ] Every Redis pub/sub message is forwarded to the WebSocket client as JSON
- [ ] Connection closed by server on `complete` or `failed` status
- [ ] `ws_token` expires after 1 hour (Redis TTL)
- [ ] Fallback: `GET /recipes/jobs/:job_id` polling still works independently
- [ ] Integration test: mock Redis pub/sub publish → client receives correct WebSocket message

---

### EP2-08: Worker service — ingredient cost fetching
**Depends on:** EP2-05 | **Parallel with:** EP2-07

Python worker service. Scheduled job fetches cost for newly added ingredients.

**AC:**
- [ ] `services/worker/` Python project scaffold (pyproject.toml, ruff, mypy, pytest, Dockerfile)
- [ ] Scheduled job (APScheduler or simple polling loop): find `ingredients` with no `ingredient_costs` row → call cost API (stub if no API key; log warning and skip)
- [ ] Cache result in Redis `cost:{ingredient_id}:{unit}` with 6h TTL; persist to `ingredient_costs` table
- [ ] `docker-compose.yml` updated with `worker` service
- [ ] Unit test: cost API fixture response → correct Redis key written + DB row inserted

---

### EP2-09: Nutrition extraction during scraping
**Depends on:** EP2-04 (Claude parser) | **Parallel with:** EP2-05

Extend the Claude extraction schema and scraper pipeline to capture nutrition data when the source page includes it.

**AC:**
- [ ] `recipe_nutrition` table added via Flyway migration (`V_nutrition__recipe_nutrition.sql`)
- [ ] Claude extraction tool schema extended with an optional `nutrition` object (`calories`, `protein_g`, `carbs_g`, `fat_g`, `fiber_g`, `sodium_mg`, `serving_size_label`); all fields optional — many recipe pages omit some or all
- [ ] Scraper writes a `recipe_nutrition` row if any nutrition field is non-null; skips insert (no row) if Claude returns no nutrition data
- [ ] `GET /recipes/:id/cook-mode` now returns the nutrition object if a row exists
- [ ] Unit test: Claude fixture response with full nutrition → all fields persisted; fixture with no nutrition → no row written, cook-mode returns `nutrition: null`

---

## Phase 3 — Pantry
**Stack:** `services/api/` — Java + Spring Boot

**Contract session:** Both devs agree on the `UnitConverter` interface and the unit conversion table (which conversions are supported) before splitting. Both EP3-03 and EP3-04 use it.

---

### EP3-01: Flyway migrations — pantry schema
**Depends on:** EP2-05 | **Parallel with:** nothing

**AC:**
- [ ] `V4__pantry.sql`: `pantry_items`, `pantry_events` matching `docs/schema.md`
- [ ] Index on `pantry_events(household_id, ingredient_id, created_at DESC)` created
- [ ] Migration applied cleanly; existing data unaffected

---

### EP3-02: Pantry read and write endpoints
**Depends on:** EP3-01 | **Parallel with:** EP3-03

Basic pantry CRUD. Each write emits an event and updates the projection.

**AC:**
- [ ] `GET /pantry`: returns all `pantry_items` for caller's household
- [ ] `POST /pantry/items`: writes `pantry_events` row (`type=add`, `delta_quantity > 0`) + updates `pantry_items`; both in one transaction
- [ ] `PATCH /pantry/items/:id`: writes `adjust` event + updates projection
- [ ] `DELETE /pantry/items/:id`: writes `expire` event; sets `pantry_items.quantity = 0`
- [ ] `GET /pantry/events`: returns full event log for household, ordered by `created_at DESC`
- [ ] `PantryService` interface + `PantryServiceImpl`
- [ ] Integration tests: add item → projection updated; delete item → expire event written + quantity zeroed; event log has correct entries

---

### EP3-03: Unit conversion utility
**Depends on:** EP3-01 | **Parallel with:** EP3-02

Shared utility used by cook deduction and serving size display.

**AC:**
- [ ] `UnitConverter` class: converts between mass units (g, kg, oz, lb), volume units (ml, l, cup, tbsp, tsp, fl oz), and count units (piece, clove, slice)
- [ ] Cross-category conversions use ingredient-specific density where needed (e.g., `cup flour = 120g`); a lookup table covers common cases
- [ ] `convert(quantity, fromUnit, toUnit)` returns `BigDecimal`; throws `UnsupportedConversionException` for undefined pairs
- [ ] Unit tests: ~20 conversion cases covering each unit family; known-good conversions (1 cup flour = 120g, 1 tbsp = 15ml, etc.)

---

### EP3-04: Cook deduction
**Depends on:** EP3-02, EP3-03 | **Parallel with:** EP3-05

The core event-sourcing write path. All-or-nothing deduction across multiple ingredients.

**AC:**
- [ ] `POST /pantry/cook`: loads `recipe_ingredients`, applies serving size multiplier (`qty * requestedServings / recipe.servings`), unit-normalises each quantity to match `pantry_items.unit` via `UnitConverter`
- [ ] If any ingredient quantity is insufficient: return 422 with `{shortages: [{ingredient, needed, available, unit}]}`; no events written
- [ ] If all sufficient: write one `cook_deduction` pantry event per ingredient + update all `pantry_items` projections — all in a single `@Transactional` block
- [ ] Returns updated pantry state on success
- [ ] Unit tests: multiplier math, shortage detection with `UnitConverter` normalisation
- [ ] Integration test: cook with sufficient ingredients → events written + projections updated atomically; cook with one shortage → 422 returned + zero events written

---

### EP3-05: Barcode scanning and recipe availability
**Depends on:** EP3-02 | **Parallel with:** EP3-04

Two read-path features that depend on the pantry being populated.

**AC:**
- [ ] `POST /pantry/scan`: looks up `ingredients.barcode`; if not found, calls Open Food Facts API (`https://world.openfoodfacts.org/api/v0/product/{barcode}.json`); parses response → creates or matches `ingredients` row; returns ingredient + current pantry quantity
- [ ] `GET /recipes?can_make=true`: loads caller's full pantry once; for each recipe checks all non-optional ingredients have `pantry_items.quantity >= recipe_ingredients.quantity` (normalised via `UnitConverter`); returns only makeable recipes
- [ ] Open Food Facts call is wrapped in a try/catch; returns 404 with a clear message if barcode not found upstream
- [ ] Unit test: barcode found locally → no external call made; barcode miss → Open Food Facts fixture response parsed correctly
- [ ] Integration test: `can_make=true` returns recipes with all ingredients in pantry and excludes those with a shortage

---

## Phase 4 — Meal Planning
**Stack:** `services/api/` — Java + Spring Boot

---

### EP4-01: Meal plan CRUD + schema
**Depends on:** EP3-04 | **Parallel with:** EP4-02

**AC:**
- [ ] `V5__meal_plans.sql`: `meal_plans`, `meal_plan_entries` matching `docs/schema.md`; `UNIQUE(household_id, week_start_date)` enforced
- [ ] `POST /meal-plans`: creates plan; `week_start_date` must be a Monday (validate, return 400 otherwise); 409 if plan for that week already exists
- [ ] `POST /meal-plans/:id/entries`: adds recipe to a day + meal type + serving size; validates `planned_date` falls within the plan's week
- [ ] `GET /meal-plans/:id`: returns plan with full recipe detail per entry
- [ ] `GET /meal-plans/current`: returns the plan whose `week_start_date` is the most recent Monday on or before today; 404 if none
- [ ] `DELETE /meal-plans/:id/entries/:entryId`: removes entry
- [ ] MockMvc integration tests: full CRUD; duplicate week returns 409; non-Monday `week_start_date` returns 400

---

### EP4-02: Pantry impact calculation
**Depends on:** EP3-04 | **Parallel with:** EP4-01

**AC:**
- [ ] `GET /meal-plans/:id/pantry-impact`: aggregates all ingredient quantities across entries (scaled by servings); compares to current pantry state; classifies each ingredient as `covered`, `partially_covered`, or `shortage`
- [ ] Uses `UnitConverter` for unit normalisation (same logic as cook deduction)
- [ ] Response matches `docs/api.md` shape: `{covered: [...], shortages: [...], partially_covered: [...]}`
- [ ] GraphQL query `mealPlan(weekOf: "YYYY-MM-DD")` returns nested recipe + pantry impact
- [ ] Unit tests: aggregation across multiple entries with different serving sizes; correct classification boundaries
- [ ] Integration test: create plan with 3 recipes + known pantry state → impact response correct

---

## Phase 5 — Shopping List
**Stack:** `services/api/` — Java + Spring Boot

---

### EP5-01: Shopping list generation and schema
**Depends on:** EP4-02 | **Parallel with:** EP5-02

**AC:**
- [ ] `V6__shopping_lists.sql`: `shopping_lists`, `shopping_list_items` matching `docs/schema.md`
- [ ] `POST /shopping-lists` (from meal plan): derives required quantities from pantry impact; `quantity_to_buy = quantity_needed - quantity_in_pantry`; consolidates same ingredient from multiple recipes
- [ ] `POST /shopping-lists` (from recipe IDs + servings map): same calculation, no meal plan required
- [ ] Items grouped by `ingredients.category` (store section); `estimated_cost` attached from `ingredient_costs` where available
- [ ] `PATCH /shopping-lists/:id/items/:itemId`: toggle `is_checked`; update `quantity_to_buy`
- [ ] Integration test: generate list from 3-recipe plan → correct quantities, deduplication, cost totals

---

### EP5-02: Export endpoints
**Depends on:** EP5-01 | **Parallel with:** nothing in this phase

**AC:**
- [ ] `GET /shopping-lists/:id/export/pdf`: generates PDF with items grouped by store section, quantities, costs, and total; uses WeasyPrint or iText (Java); returns `Content-Type: application/pdf`
- [ ] `GET /shopping-lists/:id/export/notes`: returns plain text formatted for Apple Notes copy-paste (matching the format in `docs/api.md`); `Content-Type: text/plain`
- [ ] PDF renders correctly: section headers bold, items with checkboxes, total at bottom
- [ ] Integration test: generate list → export PDF → response is non-empty valid PDF bytes; export notes → response matches expected format exactly

---

## Phase 6 — AI Layer
**Stack:** `services/ai/` (Python 3.12 + FastAPI) + `services/api/` AI route additions (Java + Spring Boot)

**Contract session:** Agree on the HTTP contract between the Spring Boot API and the AI service — endpoint paths, request/response JSON shapes for `/internal/search`, `/internal/meal-plan`, `/internal/substitute`, `/internal/can-make` — before splitting. The API service proxies these; the AI service implements them.

---

### EP6-01: AI service scaffold
**Depends on:** EP2-06 (Qdrant + Elasticsearch populated) | **Parallel with:** EP6-02

**AC:**
- [ ] `services/ai/` FastAPI project: `pyproject.toml`, ruff, mypy, pytest, Dockerfile
- [ ] `docker-compose.yml` updated with `ai` service on port 8001
- [ ] `GET /health` returns `{"status": "ok"}`; configured in Spring Boot API as a dependency health check
- [ ] Database + Qdrant + Elasticsearch clients initialised from env vars at startup; startup fails fast if any connection is unreachable

---

### EP6-02: RAG pipeline from scratch
**Depends on:** EP6-01 | **Parallel with:** EP6-03

Build the retrieval pipeline without frameworks first. This is the learning-first implementation.

**AC:**
- [ ] `POST /internal/search`: embeds query via Ollama → cosine similarity search in Qdrant (top-k=10) → fetch recipe metadata from PostgreSQL for top-k IDs → apply optional pantry availability filter → return ranked results with scores
- [ ] `POST /internal/can-make`: same pipeline, filter hard-constrained to pantry-available recipes; inject pantry context into Claude prompt for response generation
- [ ] Embed + retrieve functions are pure, independently testable (no side effects)
- [ ] Unit tests: fixture Qdrant response → correct ranking; pantry filter correctly excludes recipes with shortages
- [ ] Integration test (Testcontainers + Qdrant): insert 3 fixture recipe vectors → query → correct top-k returned

---

### EP6-03: Spring Boot AI proxy routes
**Depends on:** EP6-01 | **Parallel with:** EP6-02

Wire the public-facing AI endpoints in the Spring Boot API. All business logic lives in the AI service.

**AC:**
- [ ] `POST /ai/search`, `POST /ai/can-make`, `POST /ai/substitute`, `POST /ai/meal-plan`: validate and authenticate request; forward to AI service via `RestClient`; return response to caller
- [ ] Timeout configured per endpoint (search: 10s, meal-plan: 30s); returns 504 on AI service timeout
- [ ] AI service URL configurable via env var `AI_SERVICE_URL`
- [ ] Unit tests: mock AI service response → proxy returns correct status and body; AI service timeout → 504 returned

---

### EP6-04: LlamaIndex refactor
**Depends on:** EP6-02 | **Parallel with:** EP6-05

Refactor the from-scratch RAG pipeline to use LlamaIndex. Verify output matches.

**AC:**
- [ ] `VectorStoreIndex` backed by Qdrant via LlamaIndex's `QdrantVectorStore`
- [ ] Custom `RecipeNodeParser` to chunk recipe documents (title, ingredients, steps as separate nodes)
- [ ] `RetrieverQueryEngine` with a custom prompt template matching the from-scratch system prompt
- [ ] Regression test: same 10 queries run against both implementations; results within acceptable similarity threshold (at least 8/10 same top-3 recipes)
- [ ] `docs/adr/007-rag-framework.md` written documenting what LlamaIndex abstracted

---

### EP6-05: NL meal planner agent
**Depends on:** EP6-03, EP6-04 | **Parallel with:** EP6-04

LangChain ReAct agent with four tools. Writes the meal plan via the API service.

**AC:**
- [ ] `POST /internal/meal-plan` (AI service): ReAct agent with tools: `search_recipes(query, filters)`, `query_pantry()`, `check_schedule(week)`, `write_meal_plan(entries)` — `write_meal_plan` calls back to the Spring Boot API's internal meal plan endpoint
- [ ] Agent loop capped at 10 steps; returns partial result + warning if cap hit
- [ ] Guardrails validation on agent-produced entries before `write_meal_plan` executes: all `recipe_id` values exist, `planned_date` within requested week, no duplicate meal type per day
- [ ] Structured logging: every agent step (thought, action, observation) logged at DEBUG level
- [ ] Integration test: mock Claude responses for 3-step agent trace → correct meal plan entries passed to `write_meal_plan`

---

### EP6-06: Ingredient substitution and AI safety
**Depends on:** EP6-03 | **Parallel with:** EP6-05

**AC:**
- [ ] `POST /internal/substitute` (AI service): few-shot Claude prompt with 3 examples; returns up to 3 substitutes with quantity, unit, notes, and `in_pantry` flag
- [ ] LLM Guard scans `query` fields in `/internal/search` and `/internal/can-make` for prompt injection; rejects with 400 if detected
- [ ] Observability: log Claude API latency, input tokens, output tokens, and agent step count per request (structured JSON; no PII logged)
- [ ] Unit test: fixture substitution response parsed correctly; prompt injection fixture string → 400 returned

---

## Phase 7 — Production Hardening
**Stack:** All services

---

### EP7-01: CI/CD pipelines
**Depends on:** all prior phases complete | **Parallel with:** EP7-02

**AC:**
- [ ] On PR: Java `./mvnw verify` (Checkstyle + tests); Python `ruff check + mypy + pytest` per service; all must pass
- [ ] On merge to `main`: build all Docker images; push to GitHub Container Registry with `main-<sha>` tag
- [ ] On release tag (`v*`): deploy to production target (Fly.io or Hetzner VPS via SSH); smoke test `GET /actuator/health` post-deploy
- [ ] Failed deploy does not delete the previous running version

---

### EP7-02: Production Docker Compose and secrets
**Depends on:** all prior phases complete | **Parallel with:** EP7-01

**AC:**
- [ ] `docker-compose.prod.yml`: no dev-only ports exposed, resource limits on all containers, `restart: unless-stopped`
- [ ] Nginx reverse proxy: routes to API (8000) and AI service (8001); TLS via Certbot/Let's Encrypt
- [ ] All secrets injected via environment variables; `.env.prod.example` lists required vars with no values
- [ ] Automated daily PostgreSQL backup: `pg_dump` → gzip → upload to remote storage (S3-compatible); backup older than 7 days pruned
- [ ] Verified: `docker-compose -f docker-compose.yml -f docker-compose.prod.yml up` runs cleanly

---

### EP7-03: Observability
**Depends on:** EP7-02 | **Parallel with:** nothing

**AC:**
- [ ] Spring Boot API: Prometheus metrics via Spring Actuator (`/actuator/prometheus`); key metrics: request rate, error rate, p95 latency per endpoint, RabbitMQ publish count
- [ ] Python services: `prometheus_client` with at minimum: scrape job success/failure rate, Claude API call latency histogram, embedding generation latency
- [ ] Grafana provisioned in `docker-compose.prod.yml` with a dashboard covering: API request rate + error rate, scrape job success rate, Claude API latency, embedding latency
- [ ] Structured JSON logging across all services with `request_id` propagated through HTTP headers and RabbitMQ message headers

---

### EP7-04: GitHub repo polish
**Depends on:** EP7-03 | **Parallel with:** nothing

**AC:**
- [ ] README updated: architecture diagram (link to `docs/architecture.md`), full tech stack table, "Why it's non-trivial" section, getting started instructions, link to live deployment or demo video
- [ ] All ADRs reviewed for accuracy against final implementation; any gaps documented
- [ ] GitHub Issues created for known limitations and future work (at least: GraphQL subscription support, mobile client, cost API integration)
- [ ] MIT `LICENSE` file added
- [ ] `docker-compose up` → full app is functional — verified by both devs on a clean machine
