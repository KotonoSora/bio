CREATE TABLE IF NOT EXISTS short_links (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    target_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_short_links_user_id ON short_links (user_id);
ALTER TABLE short_links ENABLE ROW LEVEL SECURITY;
