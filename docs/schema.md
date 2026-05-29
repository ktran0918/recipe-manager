# Database Schema

## PostgreSQL

### Users & Households

```sql
-- Users authenticated via OAuth (Google)
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    avatar_url      TEXT,
    oauth_provider  TEXT NOT NULL,           -- 'google'
    oauth_id        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (oauth_provider, oauth_id)
);

-- A household is a group that shares a pantry, meal plans, and shopping lists
CREATE TABLE households (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE,        -- short code for joining
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE household_members (
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT NOT NULL DEFAULT 'member',   -- 'owner' | 'member'
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id)
);
```

---

### Recipes

```sql
-- One recipe per scraped URL (or manually entered)
CREATE TABLE recipes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id        UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    created_by          UUID NOT NULL REFERENCES users(id),
    title               TEXT NOT NULL,
    description         TEXT,
    source_url          TEXT,
    image_url           TEXT,
    servings            NUMERIC(6,2) NOT NULL DEFAULT 1,
    cook_time_minutes   INT,
    prep_time_minutes   INT,
    complexity          TEXT CHECK (complexity IN ('easy', 'medium', 'hard')),
    occasions           TEXT[],              -- e.g. ['weeknight', 'holiday', 'date_night']
    cuisine             TEXT,
    diet_tags           TEXT[],              -- e.g. ['vegetarian', 'gluten-free']
    parsed_at           TIMESTAMPTZ,         -- null if manually entered
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Global ingredient catalog (shared across all households)
CREATE TABLE ingredients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    normalized_name TEXT NOT NULL UNIQUE,    -- lowercase, singular, for dedup
    category        TEXT,                    -- 'produce', 'dairy', 'meat', etc.
    barcode         TEXT                     -- UPC for barcode scanning
);

-- Ingredients in a recipe with quantities
-- Quantities here are for the recipe's default serving size
CREATE TABLE recipe_ingredients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id       UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    quantity        NUMERIC(10,3),           -- null if 'to taste'
    unit            TEXT,                    -- 'g', 'ml', 'cup', 'tbsp', 'piece', etc.
    preparation     TEXT,                    -- 'chopped', 'minced', etc.
    optional        BOOLEAN NOT NULL DEFAULT false,
    sort_order      INT NOT NULL DEFAULT 0,
    raw_text        TEXT NOT NULL            -- original text from recipe, e.g. "2 cloves garlic, minced"
);

CREATE TABLE recipe_steps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id   UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    instruction TEXT NOT NULL,
    PRIMARY KEY (recipe_id, step_number)  -- handled as constraint
);
```

---

### Pantry

```sql
-- Current pantry state — a projection derived from pantry_events
-- Updated after every event for fast reads
CREATE TABLE pantry_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    quantity        NUMERIC(10,3) NOT NULL DEFAULT 0,
    unit            TEXT NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (household_id, ingredient_id, unit)
);

-- Event sourcing: every pantry change is an immutable event
-- pantry_items is rebuilt from these events
CREATE TABLE pantry_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    event_type      TEXT NOT NULL CHECK (event_type IN (
                        'add',              -- user added to pantry
                        'deduct',           -- user manually reduced
                        'cook_deduction',   -- auto-deducted when recipe marked as cooked
                        'adjust',           -- user corrected quantity
                        'expire'            -- marked as used up / expired
                    )),
    delta_quantity  NUMERIC(10,3) NOT NULL,  -- positive = add, negative = deduct
    unit            TEXT NOT NULL,
    recipe_id       UUID REFERENCES recipes(id),     -- set on cook_deduction
    serving_size    NUMERIC(6,2),                    -- serving size used during cook
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes           TEXT
);

CREATE INDEX pantry_events_household_ingredient
    ON pantry_events (household_id, ingredient_id, created_at DESC);
```

---

### Ingredient Costs

```sql
-- Cost per unit for each ingredient (for shopping list cost estimates)
-- Global baseline from external API, overridable per household
CREATE TABLE ingredient_costs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    household_id    UUID REFERENCES households(id),  -- null = global baseline
    cost_per_unit   NUMERIC(10,4) NOT NULL,
    unit            TEXT NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'USD',
    source          TEXT,                   -- 'kroger_api', 'manual', etc.
    overridden_by   UUID REFERENCES users(id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ingredient_id, household_id, unit)  -- null household = global
);
```

---

### Meal Plans

```sql
CREATE TABLE meal_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    week_start_date DATE NOT NULL,          -- always a Monday
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (household_id, week_start_date)  -- one plan per week per household
);

CREATE TABLE meal_plan_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_plan_id    UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    recipe_id       UUID NOT NULL REFERENCES recipes(id),
    planned_date    DATE NOT NULL,
    meal_type       TEXT NOT NULL CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack')),
    servings        NUMERIC(6,2) NOT NULL   -- may differ from recipe default
);
```

---

### Shopping Lists

```sql
CREATE TABLE shopping_lists (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    meal_plan_id    UUID REFERENCES meal_plans(id),  -- null = ad-hoc list
    title           TEXT,
    status          TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'archived')),
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shopping_list_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shopping_list_id    UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    ingredient_id       UUID NOT NULL REFERENCES ingredients(id),
    quantity_needed     NUMERIC(10,3) NOT NULL,
    quantity_in_pantry  NUMERIC(10,3) NOT NULL DEFAULT 0,  -- snapshot at list creation
    quantity_to_buy     NUMERIC(10,3) NOT NULL,             -- needed - in_pantry
    unit                TEXT NOT NULL,
    estimated_cost      NUMERIC(10,2),
    is_checked          BOOLEAN NOT NULL DEFAULT false,
    store_section       TEXT,               -- 'produce', 'dairy', etc. for grouping
    notes               TEXT
);
```

---

### Scrape Jobs

```sql
-- Tracks async recipe parsing jobs
CREATE TABLE scrape_jobs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID NOT NULL REFERENCES households(id),
    created_by  UUID NOT NULL REFERENCES users(id),
    source_url  TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'pending' CHECK (status IN (
                    'pending', 'scraping', 'parsing', 'embedding', 'complete', 'failed'
                )),
    recipe_id   UUID REFERENCES recipes(id),    -- set on complete
    error       TEXT,                           -- set on failed
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Redis

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `session:{user_id}` | Hash | 24h | JWT refresh token store |
| `recipe:scraped:{url_hash}` | String | 7d | Dedup: block re-scraping same URL |
| `cost:{ingredient_id}:{unit}` | String | 6h | Cache ingredient cost lookups |
| `job:status:{job_id}` | Hash | 1h | Scrape job status for polling fallback |
| `pantry:snapshot:{household_id}` | Hash | 5m | Short-lived pantry state cache |

---

## Qdrant

### Collection: `recipes`

```json
{
  "name": "recipes",
  "vectors": {
    "size": 768,
    "distance": "Cosine"
  }
}
```

Each point:
```json
{
  "id": "<recipe_uuid>",
  "vector": [/* 768-dim embedding of title + description + ingredients + occasion */],
  "payload": {
    "household_id": "<uuid>",
    "title": "...",
    "complexity": "easy",
    "cook_time_minutes": 30,
    "occasions": ["weeknight"],
    "cuisine": "Italian",
    "diet_tags": ["vegetarian"]
  }
}
```

Payload fields are stored for filtered similarity search — e.g., "recipes similar to X that are also under 45 minutes and vegetarian."

---

## Elasticsearch

### Index: `recipes`

```json
{
  "mappings": {
    "properties": {
      "household_id":       { "type": "keyword" },
      "title":              { "type": "text", "analyzer": "english" },
      "description":        { "type": "text", "analyzer": "english" },
      "ingredient_names":   { "type": "text", "analyzer": "english" },
      "occasions":          { "type": "keyword" },
      "complexity":         { "type": "keyword" },
      "cuisine":            { "type": "keyword" },
      "diet_tags":          { "type": "keyword" },
      "cook_time_minutes":  { "type": "integer" },
      "created_at":         { "type": "date" }
    }
  }
}
```

Supports: keyword search, fuzzy ingredient matching, faceted filters (occasion, complexity, cuisine, diet, cook time range), sorting by relevance or recency.
