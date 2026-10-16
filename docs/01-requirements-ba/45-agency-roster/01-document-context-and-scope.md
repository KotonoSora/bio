# Agency Roster - Document Context and Scope (feature 45)

Phase 1 BA · Feature 45 of 53 — agency 1/3. A master account over people and client profiles: exact invites and roles, scoped audited Act-as-user editing, and instant-effect membership. Sources: `agency/roster/team-roster.md`, `agency/access/act-as-user.md`, `platform/delivery/agency-teams.md` (FR-2..FR-3, FR-6), `monetize/organizations/agency-skeleton.md` (inherited DDL intent). Implemented by `docs/02-architecture-techlead/45-agency-roster/`. Service: account-service (port 8081, `account_db`, Redis db 7) — membership + step-up are the authorization hot path and evaluate in-service per the baseline's doctrine; the legacy "monolith agency module" verdict lands here as the host-service choice.

## Context

Membership is the root of all agency power — reporting, Act-as-user, and billing all read it — so invites, roles, and removals must be exact, audited, and instantly effective. The legacy skeleton (`orgs`/`org_members`, `role ∈ owner|manager|member`) becomes the roster kernel with `client` added to the role enum (flagged, versioned); the skeleton's behavior limit (no invite/role/Act-as-user code before this feature) is enforced by the E4 gate audit. Act-as-user without scoped, expiring, fully-audited sessions is a breach feature; this feature makes every Act-as-user minute attributable, revocable, and visible to the impersonated user.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Org lifecycle: multi-org ownership, settings by owner/manager, typed-confirm audited deletion with reversible-within-grace suspension (no silent cascade) |
| OBJ-02 | Invite-by-username with hashed single-use tokens (7-day expiry), invite-to-register for unknown usernames, no shadow accounts |
| OBJ-03 | Closed role set (`owner|manager|member|client`) enforced server-side per endpoint; UI tabs are views, roles are truth |
| OBJ-04 | Client-profile linking as aggregation only (profiles stay user-owned); staff-initiated links pend until the owner accepts; either-side unlink severs instantly |
| OBJ-05 | Scoped Act-as-user: all gates required, allowlisted powers only, short-lived sessions, distinct identity class, fully audited with before/after |
| OBJ-06 | Instant effect: changes apply on the next request (no stale-privilege window beyond token TTL); suspensions freeze powers instantly |

## Scope

In scope (SC-01..SC-06): `orgs`/`org_members`/`org_clients`/`org_invites`/`act_sessions` (V4 in `account_db`); org CRUD + restore; invites send/accept/decline/revoke; role grant/revoke/remove/suspend/reinstate; client link/unlink; Act-as-user session lifecycle + history + revocations; Users/Managers tab reads; the act-as identity class seam (baseline cross-edit); E4 gate audit.

Out of scope: seat counting/charges (46); reporting/leaderboards (47); profile ownership transfer (never in v1); plan/billing internals (32–40); publishing/automation execution (41–44); white-label portals.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Agency owner | C/R/U/D org; C/R/U/D members + invites + clients; C act-suspend; D org (typed confirm) | Full control incl. billing contact (46) |
| Manager | C/R/U members + clients (never owner role, never billing) | Day-to-day roster within the matrix |
| Member | R assigned client profiles; act-as within assignment | Staff work, no roster changes |
| Client | R own profiles + reports; U unlink; U revoke act-as | Owns their profiles; sees every staff action on them |
| Support/auditor | R audit trail (scoped) | Reconstructs any agency action from `audit_log` alone |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Self-serve org creation vs agency-plan gate (agency-teams Q1 / roster Q1) | Creation is self-serve (free); staff invites require an active agency subscription (46's entitlement read); clients are unlimited/free. Differentiator = seats + tooling, not creation |
| Q-02 | Client role granularity (agency-teams Q2 / roster Q2) | Flat `client` role in v1: own profiles + reports only; finer grants are a later amendment |
| Q-03 | Act-as concurrency (act-as Q1) | ≤ 3 concurrent sessions per staffer; one active editing session per profile (presence lock, `presence_locked` on conflict) |
| Q-04 | Read-only shadow mode (act-as Q2) | Not in v1: every access is a full, audited session — no silent previews exist |
| Q-05 | Org-owned profiles (skeleton Q2) | Aggregation only; `org_clients` never changes a profile's `user_id` ownership |
| Q-06 | Org delete retention | Suspension within grace (30 days) → hard delete of org rows with member/client data retained per the platform retention decision; client profiles never touched |

## Constraints carried

Invite tokens hashed at rest, single-use, 7-day expiry; step-up freshness ≤ 10 min at act-as start; act-as sessions 30-min idle TTL sliding to an 8-h hard stop; every mutation audits (actor + before/after; act-as rows carry actor + on-behalf-of); append-only audit with no staff-side redaction; secrets via vault/env only; zero token material in logs.
