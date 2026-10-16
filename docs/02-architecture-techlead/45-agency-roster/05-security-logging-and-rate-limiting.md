# Agency Roster - Security, Logging, and Rate Limiting (feature 45)

Phase 2 TL · BA sources: `docs/01-requirements-ba/45-agency-roster/` (BR-01..BR-14, NFR-01..NFR-11). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. The permission-matrix fixture is this feature's probe battery.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Server-side matrix: every endpoint re-checks membership + role via `RoleMatrix`; UI never gates; escalation attempts 403 + audit-flagged | AC-02 fixture |
| S-2 | Act-as identity class: distinct issuer + claims; downstream allowlists enforced per endpoint class; account-level classes deny by default; stripped/tampered markers fail authentication | AC-06 |
| S-3 | Act-as gates: membership + link + assignment + no-revoke + step-up ≤ 10 min, all server-side; failures name the condition and log | AC-05 |
| S-4 | Presence + caps: partial unique index (one active session per profile) + ≤ 3 concurrent per staffer; no interleaved edits exist | AC-10 |
| S-5 | Invite hygiene: tokens signed, SHA-256 hashed at rest, single-use, 7-day expiry; pending-uniqueness blocks duplicates; re-invite limits | AC-01 |
| S-6 | Instant effect: per-request checks with ≤ 60 s cache bound; suspensions freeze all powers; stale-session probes fail closed within TTL | AC-03 |
| S-7 | Revocation: client revoke ≤ 5 s + unlink; org-wide suspend ≤ 5 s; in-flight mutations fail closed per request | AC-08 |
| S-8 | Audit integrity: extended shape (actor + on_behalf_of + before/after); append-only grants (no UPDATE/DELETE); client view = support view | AC-07 |
| S-9 | Scoping: RLS org/member/client/session policies; cross-org reads zero rows; client history exposes only their own profiles | RLS probes |
| S-10 | Privacy: zero token material in logs/errors/events; invite tokens never echoed after mint; audit 24 months; step-up material never stored | Log scan |

## Rate limits (gateway + app; Redis db 7 `ag:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| Invite send | 30/h/org + 3/day/username | 429 `throttled` |
| Invite accept/decline | 30/h/IP | 429 |
| Roster reads | 600/h/user | 429 |
| Roster mutations | 120/h/user | 429 |
| Act start | 20/h/staffer + 10/h/profile | 429 |
| Revoke / complaint | 10/h/client | 429 |
| Internal seats route | 60/h/service | 429 |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Step-up flow down | Act starts refuse (fail closed); roster reads/writes unaffected |
| Billing entitlement read down (46) | Staff invites + act-as starts refuse (fail closed); roster reads unaffected |
| Notification pipeline down | Invites queue with backlog (tokens stay valid); roster state unaffected |
| Redis down | Rate limits degrade to conservative fixed windows; matrix + effect checks unaffected |
| Roster cache stale | Per-request revalidation bound ≤ 60 s; no unbounded stale-privilege window exists |
| Audit pipeline down | Mutations fail closed (audit write is part of the transaction) |

## Audit and observability

Audits (24-month, append-only): org lifecycle (`org.created/deleted/restored`), membership transitions with before/after, invite states, link/unlink, assign/unassign, act session start/end/revoked/suspended/complaint, escalation attempts. Metrics: `orgs_active`, `members{role,status}`, `invites{state}`, `act_sessions_active`, `act_denied{condition}`, `revocations`, `complaint_freezes`, `velocity_alerts`. Zero token labels.

## Support answers

"Why can't I edit as staff?" → gate condition (assignment, step-up, presence lock, suspension) in plain language. "What did staff change?" → client-visible act history with before/after links. "How do I stop agency access?" → one-click revoke + unlink; re-link requires fresh acceptance. "Why can't I add staff?" → subscription requirement (46) with subscribe path. "Why was my session cut?" → TTL/hard-stop/revoke reason from the session row.
