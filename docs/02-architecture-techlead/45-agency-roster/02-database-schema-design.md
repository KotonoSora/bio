# Agency Roster - Database Schema Design (feature 45)

Phase 2 TL · `account_db` migration `V4__agency_roster.sql` (chain: V1 account core, V2 deletion, V3 concierge). BA sources: `docs/01-requirements-ba/45-agency-roster/`.

## Flyway V4 (`V4__agency_roster.sql`)

```sql
CREATE TABLE orgs (
  id TEXT PRIMARY KEY,
  owner_user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  logo_key TEXT,
  contact_email TEXT,
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','suspended_deleted')),
  delete_after TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_org_delete CHECK (status <> 'suspended_deleted' OR delete_after IS NOT NULL)
);
CREATE INDEX idx_org_owner ON orgs (owner_user_id);

CREATE TABLE org_members (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL REFERENCES orgs(id),
  user_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('owner','manager','member','client')),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','invited','suspended','removed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_om UNIQUE (org_id, user_id)
);
CREATE INDEX idx_om_user ON org_members (user_id, status);

CREATE TABLE org_clients (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL REFERENCES orgs(id),
  profile_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','active','unlinked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_oc UNIQUE (org_id, profile_id)
);
CREATE INDEX idx_oc_profile ON org_clients (profile_id, status);

CREATE TABLE client_assignments (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL REFERENCES orgs(id),
  profile_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_ca UNIQUE (org_id, profile_id, user_id)
);
CREATE INDEX idx_ca_user ON client_assignments (user_id, org_id);

CREATE TABLE org_invites (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL REFERENCES orgs(id),
  username TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('manager','member','client')),
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','accepted','declined','revoked','expired')),
  invited_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_oi_pending UNIQUE (org_id, username, status)
);
CREATE INDEX idx_oi_expiry ON org_invites (expires_at) WHERE status = 'pending';

CREATE TABLE act_sessions (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL REFERENCES orgs(id),
  actor_user_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  step_up_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  hard_stop_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  end_reason TEXT CHECK (end_reason IN ('user','revoked','expired','hard_stop','suspended','complaint'))
);
CREATE UNIQUE INDEX uniq_act_active_profile ON act_sessions (profile_id) WHERE ended_at IS NULL;
CREATE INDEX idx_act_actor ON act_sessions (actor_user_id) WHERE ended_at IS NULL;
```

## RLS and grants

```sql
ALTER TABLE orgs ENABLE ROW LEVEL SECURITY;
CREATE POLICY org_member_read ON orgs FOR SELECT TO app_role
  USING (EXISTS (SELECT 1 FROM org_members m WHERE m.org_id = id
    AND m.user_id = current_setting('app.user_id', true) AND m.status = 'active'));
CREATE POLICY org_owner_write ON orgs FOR ALL TO app_role
  USING (owner_user_id = current_setting('app.user_id', true))
  WITH CHECK (owner_user_id = current_setting('app.user_id', true));
-- org_members/org_clients/client_assignments: member-of-org read + role-gated write (service context for matrix writes);
-- org_invites: owner/manager read; act_sessions: client reads own-profile rows, staff own, owners org-wide
GRANT SELECT, INSERT, UPDATE ON orgs, org_members, org_clients, client_assignments, org_invites, act_sessions TO app_role;
-- audit_log: INSERT/SELECT only (append-only, no UPDATE/DELETE grants)
```

## Semantics

- Invites (D-5): token = signed random secret shown once; `token_hash = sha256(token)` stored; accept verifies hash + `pending` + unexpired in one transaction (`status='accepted'`), links membership with the exact role; expiry job flips stale `pending` rows; the pending-uniqueness index blocks duplicate live invites per username.
- Membership transitions (D-4/D-6): role/status changes audit before/after; `suspended` rows fail all matrix checks; removals flip `removed`; the ≤ 60 s roster cache revalidates on every request class change.
- Client links (D-9): staff-initiated inserts `pending` + owner notification; owner accept → `active`; either-side unlink → `unlinked` + `org.client_link_changed.v1`; ownership columns are never written.
- Assignments (BR-07): staff assignment to linked profiles lives in `client_assignments`; Act-as gates and 47's access read consume it; assign/unassign audit and emit `org.membership_changed.v1` (`change='assignment'`) so caches purge ≤ 60 s.
- Act sessions (D-3/D-6): start checks gates + step-up freshness (≤ 10 min) → insert (partial unique index enforces the presence lock; violation → `presence_locked`); TTLs: idle expiry `expires_at = now() + 30 min` sliding on activity (max `hard_stop_at = start + 8 h`); end sets `ended_at` + reason; revoke paths close rows with their reason; the ≤ 3/staffer cap counts live rows.
- Audit (D-7): roster mutations + session start/end + every act-as mutation write the extended `audit_log` shape; client history view = rows where `on_behalf_of_profile = :profileId`; support reads the same rows (one truth).
- Org delete (D-10): typed confirm → `suspended_deleted` + `delete_after`; restore within grace; daily job hard-deletes past-grace org rows; `org_clients` rows drop without touching profiles.
- Seat seam (D-8): `GET /internal/v1/agency/seats` computes counts in-service from roster state (active owner+manager+member per org; clients free; invited/suspended excluded) — 46's snapshot job is the only consumer.
- No new Redis keys beyond `ag:rl:*`; zero token material in logs.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V4 after V3; six tables + indexes (incl. the partial unique) + RLS; audit grants append-only |
| Matrix fixture | Role × endpoint fixture generated from `RoleMatrix` is green; escalation attempts 403 + audit-flagged |
| Invite single-use | Concurrent accepts of one token → exactly one membership; replay refused |
| Presence lock | Two starts on one profile → one session; second `presence_locked`; cap at 3/staffer |
| Effect timing | Removal/unlink visible on the next request; stale-session probes fail closed within TTL |
| Assignment scoping | Act-as gates + access reads reflect assignments; changes purge caches ≤ 60 s |
| Audit replay | Fixture act session replays action-for-action with before/after; client view = support view |
| Delete grace | Typed-confirm suspend; restore intact; past-grace hard delete leaves profiles untouched |
| RLS probes | Cross-org reads zero rows; client reads only own-profile sessions; invites unreadable outside owner/manager contexts |
