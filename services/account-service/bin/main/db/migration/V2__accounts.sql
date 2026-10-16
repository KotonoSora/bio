CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    email CITEXT NOT NULL,
    display_name TEXT,
    entitlement_tier TEXT NOT NULL DEFAULT 'free',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts (user_id);
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
