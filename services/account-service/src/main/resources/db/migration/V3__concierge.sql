-- account-service concierge extension (feature 05;
-- docs/02-architecture-techlead/05-concierge-managed-service/02-database-schema-design.md).
-- Feature 01 owns V1, feature 02 owns V2; this file is V3. Concierge is an
-- extension of account-service (TL 01 D-1): no new service, no cross-service FKs.
--
-- DEVIATION FLAGGED (schema qualification only, owner decision 2026-10-17): the
-- TL DDL writes `account.`-qualified names; the account_db baseline uses the
-- default schema (V1/V2) - identical content, unqualified. Every business table
-- carries `user_id TEXT NOT NULL` + index + RLS (platform guardrail).
--
-- Additive extensions beyond the TL DDL (each marked ADDITIVE below; BA 03/04/05
-- require them and no TL code/DTO name changes):
--   contracts.sow_version / handoff   - SOW version noted (BA 04); exit handoff
--                                       inventory with per-item timestamps (BA 03).
--   tasks.notes / operator_id / missed_response_at / missed_completion_at
--                                     - client notes (BA 04), operator attribution
--                                       (BA 04 names), durable SLA miss records
--                                       (BR-06: never silent remediation).
--   approvals.contract_id + nullable task_id - setup-plan approval happens at
--                                       draft, before any task exists (BA 03 step 2).
--   staff.name                        - operator display name shown to the client (BA 04).

-- --- staffing register + SOW templates (BR-08 presence check) ----------------

CREATE TABLE concierge_sow_templates (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  menu       JSONB NOT NULL DEFAULT '[]'::jsonb,   -- picked item kinds
  version    INT NOT NULL DEFAULT 1,
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE concierge_staff (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL UNIQUE,                 -- operator/agency-owner account
  role       TEXT NOT NULL CHECK (role IN ('operator', 'owner')),
  name       TEXT,                                 -- ADDITIVE: display name (BA 04)
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  removed_at TIMESTAMPTZ
);

-- --- contracts ---------------------------------------------------------------

CREATE TABLE concierge_contracts (
  id                 TEXT PRIMARY KEY,
  user_id            TEXT NOT NULL,                -- client account
  status             TEXT NOT NULL CHECK (status IN ('draft', 'active', 'paused', 'exited')),
  sla_tier           TEXT NOT NULL,
  sow                JSONB NOT NULL DEFAULT '[]'::jsonb,
  sow_version        INT NOT NULL DEFAULT 1,       -- ADDITIVE (BA 04 "version noted")
  pending_transition TEXT CHECK (pending_transition IN ('pause', 'exit')),
  pending_by         TEXT CHECK (pending_by IN ('client', 'agency')), -- ADDITIVE: dual-confirm initiator (BR-10)
  handoff            JSONB,                        -- ADDITIVE: exit handoff inventory (BA 03)
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at       TIMESTAMPTZ,
  paused_at          TIMESTAMPTZ,
  exited_at          TIMESTAMPTZ,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live contract per client (re-contracting after exit is allowed).
CREATE UNIQUE INDEX idx_concierge_contracts_live
  ON concierge_contracts (user_id) WHERE status <> 'exited';
CREATE INDEX idx_concierge_contracts_user ON concierge_contracts (user_id);

-- --- task board --------------------------------------------------------------

CREATE TABLE concierge_tasks (
  id                   TEXT PRIMARY KEY,
  contract_id          TEXT NOT NULL REFERENCES concierge_contracts(id) ON DELETE CASCADE,
  user_id              TEXT NOT NULL,
  kind                 TEXT NOT NULL,
  status               TEXT NOT NULL CHECK (status IN
                         ('requested', 'approved', 'in_progress', 'done', 'refused', 'cancelled')),
  origin               TEXT NOT NULL CHECK (origin IN ('client', 'scheduled')),
  notes                TEXT,                       -- ADDITIVE: client request notes (BA 04)
  operator_id          TEXT,                       -- ADDITIVE: attribution (BA 04 names)
  work_refs            JSONB NOT NULL DEFAULT '[]'::jsonb,
  refusal_reason       TEXT,                       -- off_menu|over_volume|boundary_*|client_cancel
  referral             TEXT,
  missed_response_at   TIMESTAMPTZ,                -- ADDITIVE: durable miss records (BR-06)
  missed_completion_at TIMESTAMPTZ,
  requested_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at          TIMESTAMPTZ,
  started_at           TIMESTAMPTZ,
  completed_at         TIMESTAMPTZ,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_concierge_tasks_contract ON concierge_tasks (contract_id, requested_at);
CREATE INDEX idx_concierge_tasks_user ON concierge_tasks (user_id);
CREATE INDEX idx_concierge_tasks_kind ON concierge_tasks (contract_id, kind);

-- --- approvals (append-only ledger; BA 05: edited never, superseded only) ----

CREATE TABLE concierge_approvals (
  id          TEXT PRIMARY KEY,
  contract_id TEXT NOT NULL REFERENCES concierge_contracts(id) ON DELETE CASCADE, -- ADDITIVE
  task_id     TEXT REFERENCES concierge_tasks(id) ON DELETE CASCADE,               -- NULL = setup plan
  user_id     TEXT NOT NULL,
  scope       TEXT NOT NULL,
  channel     TEXT NOT NULL CHECK (channel IN ('dashboard', 'signed_email')),
  approved_by TEXT NOT NULL,                       -- acting identity (client session or link subject)
  approved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  jti         TEXT                                 -- signed-link single-use marker (S-9)
);

CREATE INDEX idx_concierge_approvals_task ON concierge_approvals (task_id, approved_at);
CREATE INDEX idx_concierge_approvals_contract ON concierge_approvals (contract_id, approved_at);
CREATE INDEX idx_concierge_approvals_user ON concierge_approvals (user_id);

-- --- RLS (deny-by-default; server-set contexts only) -------------------------
--   app.user_id  owner predicate (client dashboard reads/writes)
--   app.context = 'svc'  internal handlers (staffing, templates, operator work,
--                        approvals check, dispatch gate, signed links, purge).

ALTER TABLE concierge_sow_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE concierge_sow_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY sow_templates_service ON concierge_sow_templates FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE concierge_staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE concierge_staff FORCE ROW LEVEL SECURITY;
CREATE POLICY staff_owner ON concierge_staff FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY staff_service ON concierge_staff FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE concierge_contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE concierge_contracts FORCE ROW LEVEL SECURITY;
CREATE POLICY contracts_owner ON concierge_contracts FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY contracts_service ON concierge_contracts FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE concierge_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE concierge_tasks FORCE ROW LEVEL SECURITY;
CREATE POLICY tasks_owner ON concierge_tasks FOR ALL TO account_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY tasks_service ON concierge_tasks FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

ALTER TABLE concierge_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE concierge_approvals FORCE ROW LEVEL SECURITY;
CREATE POLICY approvals_owner_insert ON concierge_approvals FOR INSERT TO account_app
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY approvals_owner_read ON concierge_approvals FOR SELECT TO account_app
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY approvals_service ON concierge_approvals FOR ALL TO account_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
-- No UPDATE/DELETE policy: approvals are append-only (BA 05). The DML grant for
-- account_app is revoked so an edited record is impossible at the database too.
REVOKE UPDATE, DELETE ON concierge_approvals FROM account_app;
