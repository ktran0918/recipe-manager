-- Global ingredient catalog (shared across all households)
CREATE TABLE ingredients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    normalized_name TEXT NOT NULL UNIQUE,
    category        TEXT,
    barcode         TEXT
);

-- One recipe per scraped URL or manually entered
CREATE TABLE recipes (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id      UUID        NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    created_by        UUID        NOT NULL REFERENCES users(id),
    title             TEXT        NOT NULL,
    description       TEXT,
    source_url        TEXT,
    image_url         TEXT,
    servings          NUMERIC(6,2)  NOT NULL DEFAULT 1,
    cook_time_minutes INT,
    prep_time_minutes INT,
    complexity        TEXT        CHECK (complexity IN ('easy', 'medium', 'hard')),
    occasions         TEXT[],
    cuisine           TEXT,
    diet_tags         TEXT[],
    parsed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ingredients in a recipe with quantities (for the recipe's default serving size)
CREATE TABLE recipe_ingredients (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id     UUID          NOT NULL REFERENCES recipes(id)     ON DELETE CASCADE,
    ingredient_id UUID          NOT NULL REFERENCES ingredients(id),
    quantity      NUMERIC(10,3),
    unit          TEXT,
    preparation   TEXT,
    optional      BOOLEAN       NOT NULL DEFAULT false,
    sort_order    INT           NOT NULL DEFAULT 0,
    raw_text      TEXT          NOT NULL
);

-- recipe_steps: 'id' is the single PK; (recipe_id, step_number) is a unique constraint.
-- See RecipeStep.java for the rationale — schema.md listed two PKs which is not valid SQL.
CREATE TABLE recipe_steps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id   UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    step_number INT  NOT NULL,
    instruction TEXT NOT NULL,
    UNIQUE (recipe_id, step_number)
);

-- Ingredient substitutions scoped to a recipe
-- conversion_ratio: substitute_quantity = original_quantity × ratio
CREATE TABLE recipe_ingredient_substitutions (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id                UUID          NOT NULL REFERENCES recipes(id)     ON DELETE CASCADE,
    original_ingredient_id   UUID          NOT NULL REFERENCES ingredients(id),
    substitute_ingredient_id UUID          NOT NULL REFERENCES ingredients(id),
    conversion_ratio         NUMERIC(6,3)  NOT NULL DEFAULT 1.000,
    notes                    TEXT,
    source                   TEXT          NOT NULL DEFAULT 'manual'
                                           CHECK (source IN ('manual', 'ai', 'recipe')),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (recipe_id, original_ingredient_id, substitute_ingredient_id),
    CHECK (original_ingredient_id != substitute_ingredient_id)
);

-- Per-recipe nutrition data (one row per recipe, per serving)
-- Populated during scraping; null columns when source page omits nutrition info
CREATE TABLE recipe_nutrition (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id          UUID          NOT NULL UNIQUE REFERENCES recipes(id) ON DELETE CASCADE,
    serving_size_label TEXT,
    calories           NUMERIC(8,1),
    protein_g          NUMERIC(8,2),
    carbs_g            NUMERIC(8,2),
    fat_g              NUMERIC(8,2),
    fiber_g            NUMERIC(8,2),
    sodium_mg          NUMERIC(8,1),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);