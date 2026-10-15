# Agency Skeleton (seats tables only, no roster logic)

PLAN ref: P3-4. Full agency roster, roles, and Act-as-user belong to P4 and must not be built here.

## Purpose

Reserve the minimal database shape for future agency seats so P4 roster work needs no destructive migration — and draw a hard line that nothing behavioral ships in P3. Tables exist, are constrained and audited, but no impersonation, invitation, or leaderboard logic is implemented behind them.

## Users

| Role | Permissions | Notes |
|---|---|---|
| P3 developer | Create the tables with constraints now | Writes no agency queries beyond existence checks |
| P4 developer (future) | Inherit clean tables to build invite/role/impersonation/leaderboard on | Served in P4, not P3 |
| Organization owner (future user) | Named here only to fix the vocabulary | Served in P4 |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | P3 developer | Create the tables with constraints now | P4 roster work needs no destructive migration | Must |
| US-02 | P4 developer | Inherit clean tables to build invite/role/impersonation/leaderboard on | Nothing behavioral ships in P3 | Should |
| US-03 | Organization owner | Have my vocabulary fixed now | I am served in P4 | Should |

## Functional requirements

1. Tables created in the P3 migration: `orgs{id, owner_user_id, name}` and `org_members{org_id, user_id, role, status}` with `role` ∈ `owner|manager|member` and `status` ∈ `active|invited|suspended`. Both carry standard `user_id`-scoping equivalents (`owner_user_id` / `user_id`) + indexes + RLS policies.
2. Constraint: no orphan rows — `org_members.org_id` FK to `orgs`, `user_id` references the platform user; deleting an org suspends (never silently cascades) member rows pending the P4 data-retention decision.
3. P3 behavior limit: the API exposes at most `GET /api/v1/orgs` (own memberships list, owner-scoped) for forward-compat testing. No invite, role-change, Act-as-user, or leaderboard endpoints exist in P3.
4. Any P3 code touching these tables logs to append-only `audit_log{actor, action, target, at}` like all plan/role/username/domain actions.
5. Explicit non-goals enforced by review: scoped impersonation tokens, audit-gated Act-as-user sessions, per-seat billing, and leaderboard aggregations are rejected from P3 diffs and resubmitted against P4 specs.

## Data / API touches

- DDL only (plus the read-only list endpoint above). RLS: members see rows for orgs they belong to; only `owner` role rows are writable even in P4 — in P3 all writes go through migrations/seeds, not API.
- No Queues, OAuth vault, or token-encryption involvement in P3. `TOKEN_ENC_KEY` and `social_accounts` remain untouched P4 concerns.
- Migration hygiene: standard Drizzle migration via `npm run db:migrate` (local → staging → prod order); never hand-edit applied SQL; fixture data must not depend on agency rows.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Migration applies cleanly on a fresh database and on the staging mirror; tables, FKs, indexes, and RLS policies all present | A fresh database and the staging mirror | The P3 migration applies | Tables, FKs, indexes, and RLS policies are all present |
| AC-02 | Direct PG probe as app role: cross-org row reads denied; unauthenticated API access to `/api/v1/orgs` rejected | Cross-org row reads as app role and unauthenticated API access | The probe runs | Cross-org reads are denied and unauthenticated access to `/api/v1/orgs` is rejected |
| AC-03 | Grep gate on the P3 tree: no `impersonat*`, invite-accept, role-grant, or leaderboard aggregation code paths exist outside comments referencing this doc | The P3 tree | The grep gate scans | No impersonation, invite-accept, role-grant, or leaderboard aggregation code paths exist outside comments referencing this doc |
| AC-04 | `audit_log` captures the table-creation/membership-seed events with actor/action/target | Table-creation/membership-seed events | They occur | `audit_log` captures them with actor/action/target |
| AC-05 | P4 readiness note filed: the exact DDL is referenced by the future agency spec, requiring no rewrite | The P4 future agency spec | Readiness is checked | The exact DDL is referenced requiring no rewrite |

## Out of scope

- Everything behavioral in P4: invite-by-username, roles enforcement, scoped Act-as-user + audit-gated sessions, leaderboard (views/clicks/CTR/sales), per-seat billing.
- Checkout, webhook, and plan matrix (see sibling docs); shop delivery; P5 scale-out.

## Open questions

- Q1: Per-seat vs flat agency pricing — decided in P4 pricing, not here; any constraint to bake into the skeleton now?
- Q2: Org-owned vs user-owned profiles — can a profile transfer into an org in P4, or do orgs only aggregate analytics?
