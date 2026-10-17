-- account-service core schema (feature 01; docs/02-architecture-techlead/01-account-and-access/02-database-schema-design.md).
-- Roles: account_migrator owns DDL; account_app is RLS-limited (no DDL). TEXT ids; timestamptz;
-- no money columns; no cross-service FKs. Fresh-apply contract: this one migration creates
-- everything; feature 02 owns V2 (deletion_requests).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
  id             TEXT PRIMARY KEY,
  email          TEXT,                      -- IdP-verified sync target
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  email_conflict BOOLEAN NOT NULL DEFAULT FALSE,
  contact_email  TEXT,
  contact_verified_at TIMESTAMPTZ,
  name           TEXT,
  image_key      TEXT,                      -- avatar prefill target (feature 09 rules)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE linked_accounts (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider_id   TEXT NOT NULL,
  provider_sub  TEXT NOT NULL,
  email_at_link TEXT,
  linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT linked_unique UNIQUE (provider_id, provider_sub)
);

CREATE TABLE sessions (
  sid                 TEXT PRIMARY KEY,     -- opaque, >= 128 bit
  user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at          TIMESTAMPTZ NOT NULL,
  last_active_at      TIMESTAMPTZ NOT NULL,
  auth_time           TIMESTAMPTZ NOT NULL, -- step-up freshness source
  idle_expires_at     TIMESTAMPTZ NOT NULL, -- 7 d sliding
  absolute_expires_at TIMESTAMPTZ NOT NULL, -- 90 d hard stop
  device_label        TEXT,                 -- coarse UA family only
  ip_class            TEXT,                 -- hashed /24 (v4) or /48 (v6)
  revoked_at          TIMESTAMPTZ
);

CREATE TABLE audit_log (                    -- append-only; no UPDATE/DELETE grant
  id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id         TEXT NOT NULL,            -- actor
  subject_user_id TEXT,                     -- subject when different
  action          TEXT NOT NULL,            -- login|link|unlink|step_up|recovery|session_revoke|contact_email|role
  target          TEXT,
  at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_class        TEXT
);

CREATE INDEX idx_linked_accounts_user ON linked_accounts (user_id);
CREATE INDEX idx_sessions_user_revoked ON sessions (user_id, revoked_at);
CREATE INDEX idx_sessions_idle_expires ON sessions (idle_expires_at);
CREATE INDEX idx_users_lower_email ON users (lower(email));               -- collision checks; non-unique by design (BR-02)
CREATE UNIQUE INDEX idx_users_contact_email_verified
  ON users (lower(contact_email)) WHERE contact_verified_at IS NOT NULL;  -- BR-03
CREATE INDEX idx_audit_log_user ON audit_log (user_id);
CREATE INDEX idx_audit_log_at ON audit_log (at);

-- RLS and access paths (TL 02). Deny-by-default: unset context returns zero rows.
-- Two server-set service contexts, never client-influenced:
--   app.context = 'svc' - internal handlers (introspect by sid, entitlement by userId,
--                         purge by userId; each handler applies its own equality predicate).
--   app.context = 'ops' - the audited recovery-link route (existence + delivery target only).
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY users_owner ON users FOR ALL TO account_app
  USING (id = current_setting('app.user_id', true))
  WITH CHECK (id = current_setting('app.user_id', true));
CREATE POLICY users_service ON users FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
CREATE POLICY users_ops_read ON users FOR SELECT TO account_app
  USING (current_setting('app.context', true) = 'ops');

ALTER TABLE linked_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE linked_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY links_owner ON linked_accounts FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY links_service ON linked_accounts FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY sessions_owner ON sessions FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY sessions_service ON sessions FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_insert ON audit_log FOR INSERT TO account_app
  WITH CHECK (user_id = current_setting('app.user_id', true)
             OR current_setting('app.context', true) IN ('svc', 'ops'));
CREATE POLICY audit_read_own ON audit_log FOR SELECT TO account_app
  USING (user_id = current_setting('app.user_id', true)
         OR current_setting('app.context', true) IN ('svc', 'ops'));
-- No UPDATE/DELETE policy: audit rows are append-only. The 24-month retention purge runs on
-- the migrator role and may only delete rows past retention.
CREATE POLICY audit_retention ON audit_log FOR DELETE TO account_migrator
  USING (at < now() - INTERVAL '24 months');
