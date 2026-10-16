# Agency Roster - Architecture and Tech Stack (feature 45)

Phase 2 TL · Service: `account-service` (port 8081, `account_db`, Redis db 7). Implements `docs/01-requirements-ba/45-agency-roster/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Host rationale: membership + step-up are the authorization hot path — the legacy "monolith agency module" verdict lands as in-service hosting per the baseline's extend-in-service doctrine (no extraction triggers fire; every agency request would otherwise pay a cross-service membership lookup).

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| `orgs`/`org_members`/`org_clients`/`org_invites`/`act_sessions` (V4); org lifecycle; invites; role matrix; client linking; Act-as-user sessions + history + revocations; the act-as identity class | Step-up flow -> account-service's own (in-service); agency-subscription entitlement for staff invites -> 46/35; notification enqueue -> 32's contract; profile existence + assignment facts -> 08/09 (internal reads); audit conventions -> baseline 03 |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.account.agency`: `OrgService`, `InviteService`, `RoleMatrix`, `LinkService`, `ActSessionService`, `StepUpGate`; the role matrix is one server-side table that generates the endpoint fixture | Matrix is code, not UI (BR-03); fixture derives from it |
| D-2 | `V4__agency_roster.sql`: six tables + indexes + RLS + a partial unique index enforcing one active act session per profile | Presence lock at the DB level; single DDL owner |
| D-3 | Act-as identity class: short-TTL signed tokens `{act_as:true, actor, org, profile}`, distinct issuer from login sessions; the gateway passes them as a second identity class; every service enforces a per-endpoint-class allowlist and denies account-level classes by default | Distinct-issuer rule; one platform seam, drilled per service (baseline 08 cross-edit) |
| D-4 | Hot path stays in-service: membership + step-up checks evaluate per request with a ≤ 60 s roster cache; no cross-service lookup exists on this path | NFR-01; the legacy monolith verdict, preserved |
| D-5 | Invites: signed token, SHA-256 hash at rest, 7-day expiry, single-use status transition; invite-to-register converts on signup; re-invite limit 3/day/username | BR-02; no shadow accounts |
| D-6 | Presence + concurrency: partial unique index for one active session per profile; ≤ 3 concurrent per staffer enforced in-service; second staffer gets `presence_locked` | BR-09/Q-03 |
| D-7 | Audit: `audit_log` extended shape with `on_behalf_of_profile` + before/after; append-only grants (no UPDATE/DELETE); client-readable history view | BR-10; disputes resolve from evidence |
| D-8 | Seat seam for 46: internal `GET /internal/v1/agency/seats?cursor=` returns per-org seat counts (active owner+manager+member; invited/suspended excluded); 46's daily snapshot job consumes it (billing owns the contact post-transfer) | Seat rules are roster rules; billing stores its own snapshots |
| D-9 | Events: `org.membership_changed.v1` and `org.client_link_changed.v1` (post-commit); 47 purges reporting caches on unlink; additive consumers only | Instant-effect propagation without cross-service reads |
| D-10 | Org delete: typed confirm → `suspended_deleted` + `delete_after = now() + 30 days`; restore flips back; a daily job hard-deletes past-grace org rows (members/clients per retention decision; profiles untouched) | No silent cascade (BR-01) |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md` | Act-as-user identity class bullet (distinct issuer; allowlist enforcement; account-level deny-by-default) |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | account-service feature list gains 45 |
| 46 | Consumes `GET /internal/v1/agency/seats` (registered here) |
| 47 | Consumes `org.client_link_changed.v1` for cache purge |
| `MIGRATION.md` | rows 45–47 + batch line at the batch tail |
