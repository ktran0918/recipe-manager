CREATE TABLE scrape_jobs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID        NOT NULL REFERENCES households(id),
    created_by   UUID        NOT NULL REFERENCES users(id),
    source_url   TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'pending'
                             CHECK (status IN ('pending', 'scraping', 'parsing', 'embedding', 'complete', 'failed')),
    recipe_id    UUID        REFERENCES recipes(id),
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);