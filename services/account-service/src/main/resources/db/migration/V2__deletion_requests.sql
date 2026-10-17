-- account-service deletion records (feature 02;
-- docs/02-architecture-techlead/02-account-deletion/02-database-schema-design.md).
-- The deletion record outlives the account: ids, timestamps, and counts references
-- only - no email, no name (NFR-10). Feature 01 owns V1; this file is V2.

CREATE TABLE deletion_requests (
  id               TEXT PRIMARY KEY,
  user_id          TEXT NOT NULL UNIQUE,     -- logical reference to users(id); no FK (row outlives purge)
  status           TEXT NOT NULL CHECK (status IN ('grace','purged','cancelled')),
  requested_at     TIMESTAMPTZ NOT NULL,
  purge_due_at     TIMESTAMPTZ NOT NULL,     -- requested_at + 30 d
  purge_started_at TIMESTAMPTZ,              -- set on first run attempt
  purged_at        TIMESTAMPTZ,
  cancelled_at     TIMESTAMPTZ,
  reminder_sent_at TIMESTAMPTZ,              -- day 23; single send guard
  certificate_ref  TEXT,                     -- purge-certificates/{userId}/{runId}.json
  run_id           TEXT                      -- completing run
);

CREATE INDEX idx_deletion_due ON deletion_requests (purge_due_at) WHERE status = 'grace';

-- RLS (TL 02): owner predicate for read/insert/cancel; the runner uses the
-- server-set 'svc' scope (each handler still applies its own equality
-- predicate); 'ops' is the audited support read route. Unset scope = deny.
ALTER TABLE deletion_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE deletion_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY deletion_owner ON deletion_requests FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY deletion_service ON deletion_requests FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
CREATE POLICY deletion_ops_read ON deletion_requests FOR SELECT TO account_app
  USING (current_setting('app.context', true) = 'ops');
