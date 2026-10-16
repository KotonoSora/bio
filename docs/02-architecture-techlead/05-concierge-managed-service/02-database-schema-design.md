# Concierge Managed Service - Database Schema Design (feature 05)

Phase 2 TL · `account_db` migration `V3__concierge.sql` (V1 feature 01, V2 feature 02). BA sources: `docs/01-requirements-ba/05-concierge-managed-service/` (BR-03, BR-04, BR-08, BR-09). Baseline conventions apply (TEXT ids, RLS, no money columns).

## DDL

```sql
CREATE TABLE account.concierge_contracts (
  id                 TEXT PRIMARY KEY,
  user_id            TEXT NOT NULL,           -- client account (baseline convention)
  status             TEXT NOT NULL CHECK (status IN ('draft','active','paused','exited')),
  sla_tier           TEXT NOT NULL,           -- config-mapped windows; no numbers here
  sow                JSONB NOT NULL,          -- menu items + volumes picked
  pending_transition TEXT CHECK (pending_transition IN ('pause','exit')),
  pending_at         TIMESTAMPTZ,
  activated_at       TIMESTAMPTZ,
  paused_at          TIMESTAMPTZ,
  exited_at          TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account.concierge_tasks (
  id             TEXT PRIMARY KEY,
  user_id        TEXT NOT NULL,               -- client account (denormalized for RLS)
  contract_id    TEXT NOT NULL REFERENCES account.concierge_contracts(id) ON DELETE CASCADE,
  kind           TEXT NOT NULL CHECK (kind IN ('setup','campaign','post_batch','email','automation','report')),
  status         TEXT NOT NULL CHECK (status IN ('requested','approved','in_progress','done','refused')),
  origin         TEXT NOT NULL DEFAULT 'client' CHECK (origin IN ('client','scheduled')),
  requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at    TIMESTAMPTZ,
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  work_refs      JSONB NOT NULL DEFAULT '[]', -- agency artifact references (ids only)
  refusal_reason TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account.concierge_approvals (           -- append-only
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  task_id     TEXT NOT NULL REFERENCES account.concierge_tasks(id) ON DELETE CASCADE,
  scope       TEXT NOT NULL,                -- what exactly was approved
  channel     TEXT NOT NULL CHECK (channel IN ('dashboard','signed_email')),
  approved_by TEXT NOT NULL,                -- acting client user id
  approved_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account.concierge_staff (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL UNIQUE,          -- operator account
  role       TEXT NOT NULL CHECK (role IN ('operator','owner')),
  active     BOOLEAN NOT NULL DEFAULT true,
  added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  removed_at TIMESTAMPTZ
);

CREATE TABLE account.concierge_sow_templates (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  menu       JSONB NOT NULL,
  version    INT NOT NULL,
  active     BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_concierge_tasks_contract ON account.concierge_tasks (contract_id, status);
CREATE INDEX idx_concierge_tasks_user     ON account.concierge_tasks (user_id);
CREATE INDEX idx_concierge_approvals_task ON account.concierge_approvals (task_id);
CREATE INDEX idx_concierge_contracts_user ON account.concierge_contracts (user_id);
```

## RLS and access paths

- All five tables: owner predicate `user_id = app.user_id`; `app.context='svc'` for the service's own flows (operator assignment checks, staffing gate, approval-check route, purge participant).
- `concierge_approvals` is append-only by grants: `app_role` has INSERT + SELECT only (no UPDATE/DELETE); corrections happen only by superseding, never editing.
- `concierge_staff` and `concierge_sow_templates` have no dashboard path; internal routes only.

## Transitions (enforced in service code, proven by drill)

| From | To | Trigger |
|---|---|---|
| draft | active | Owner activation + staffing presence check passes |
| active | paused | Dual-confirmed pause |
| paused | active | Dual-confirmed resume (pause counterpart of exit; owner-initiated) |
| active/paused | exited | Dual-confirmed exit + handoff checklist complete |
| pending_transition | cleared | Initiator cancels |
| requested | approved | Client approval recorded (immutable) |
| approved | in_progress | Assigned operator starts |
| in_progress | done | Operator completes + `work_refs` attached |
| requested/approved | refused | Off-menu/over-volume/boundary refusal with reason |

## Verification drills

1. Fresh apply: `V3` clean on 18.6; CHECKs, FKs (`ON DELETE CASCADE` contract -> tasks), indexes, RLS policies present.
2. Append-only drill: `UPDATE`/`DELETE` on `concierge_approvals` as `app_role` fails.
3. RLS drill: client A sees zero client B rows; svc context works for the approval-check and purge paths.
4. Purge drill: feature 02 participant run deletes a client's contracts/tasks/approvals; retention note: post-exit rows are anyway minimized (ids/states/timestamps only) and purge wins over retention (deletion is absolute).
