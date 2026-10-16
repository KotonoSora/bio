CREATE TABLE IF NOT EXISTS hourly_rollup (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    hour TIMESTAMPTZ NOT NULL,
    views BIGINT NOT NULL DEFAULT 0,
    uniques BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (username, hour)
);
CREATE INDEX IF NOT EXISTS idx_hourly_rollup_user_id ON hourly_rollup (user_id);
ALTER TABLE hourly_rollup ENABLE ROW LEVEL SECURITY;
