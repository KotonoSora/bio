-- billing-service baseline (E0): extensions, roles note, audit log.
-- Every business table (later features): id TEXT PK, user_id TEXT NOT NULL + index,
-- created_at/updated_at timestamptz, RLS enabled, deny-by-default.
-- Migrator role owns DDL; app role is DML-under-RLS (wired in ops runbooks, not here).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "citext";

CREATE TABLE IF NOT EXISTS audit_log (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT NOT NULL,
    result TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log (user_id);
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
