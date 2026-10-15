# Act-as-User (scoped editing, fully audited)

PLAN ref: P4-5 (scoped Act-as-user + audit log). P4 scope — do NOT build in P1–P3. Membership/roster in `roster/team-roster.md` (every session derives from it); this doc owns the session mechanics and the audit trail that makes impersonation safe to offer.

## Purpose

Let agency staff edit a client's profile as if logged in as them — with the client able to see exactly what was done, and the platform able to prove it. Impersonation without scoped, expiring, fully-audited sessions is a breach feature; this doc makes every Act-as-user minute attributable, revocable, and visible to the impersonated user.

## Users

- Staff (manager/member): starts a scoped session on an assigned client profile, edits blocks/themes/products, ends the session.
- Client: sees who accessed their profile, when, and what changed — with one-click revoke and complaint path.
- Auditor/support: replays any session's actions from the audit trail during disputes.

## Functional requirements

1. Session start (gated, all conditions required): actor holds active staff membership in the org; target profile is org-linked to that org AND assigned to the actor (assignment required — org membership alone never grants access to every client); client has not revoked agency access; actor passes a fresh re-authentication (password/webauthn step-up, max 10-min-old) at session start. Missing any condition → 403 with the specific failed condition logged.
2. Scope (least privilege, enforced per request): session powers = exactly the client's own dashboard powers on the assigned profile(s) — blocks, theme, products, leads read, post scheduling where P4 surfaces exist — minus everything account-level (no password/email/plan changes, no billing, no username change, no account delete, no roster changes, no export of other profiles). Scope is a server-side allowlist evaluated per endpoint, never a UI hiding.
3. Session mechanics: short-lived tokens (30-min TTL, sliding to max 8 h with activity, hard stop requiring re-step-up); distinct session marker (`act_as: {actor, org, profile}`) propagated to every write path so rows and logs always distinguish staff actions from client actions; concurrent sessions per actor capped (see Q1); client-visible "staff is editing" presence indicator on their dashboard during active sessions.
4. Audit trail (non-negotiable): session start/end + every mutating action append `audit_log{actor_staff_id, on_behalf_of_profile, action, before?, after?, at}` — before/after snapshots for content edits (blocks, theme, products), references for publishes/sends. Trail readable by the client (their profile's staff-action history), the org owner, and support; immutable (append-only, no staff-side redaction).
5. Revocation: client one-click "revoke agency access" kills all live sessions on their profiles instantly + unlinks per the roster doc (re-link requires fresh acceptance); org owner/manager suspend kills staff sessions org-wide; auto-expiry needs no action. Revoked-session requests fail closed mid-flight (checked per request, not per page-load).
6. Abuse posture: impossible-travel/velocity anomaly on session starts alerts ops; sessions from suspended members impossible by construction (membership check first); client complaint flow freezes the implicated staff member's Act-as-user power pending review (read-only roster + reporting continue).

## Data / API touches

- New tables: `act_sessions{session_id, org_id, actor_user_id, profile_id, started_at, expires_at, ended_at?, step_up_at}` + per-action audit rows in `audit_log` (extended shape with `on_behalf_of` fields; append-only, no UPDATE/DELETE grants to app roles). All PG via `worker/db/*`; RLS (clients read own-profile sessions/actions; staff read own sessions; owners read org-wide).
- Endpoints: `POST /api/v1/agency/act/start` (step-up verified, all gates checked), `POST /api/v1/agency/act/end`, scoped dashboard APIs accept the session marker (allowlisted subset), `GET /api/v1/agency/act/history` (client/staff/owner views), revoke endpoints (client + owner/manager). Session tokens short-TTL signed, distinct issuer from login sessions (never confused by validators).

## Acceptance criteria

- Gate drill: each start-condition failure (unassigned profile, revoked access, stale step-up, suspended membership) returns 403 naming the condition, with zero session created.
- Scope drill: allowlisted edits succeed; account-level attempts (password, plan, username, delete, roster) via live session return 403 + audit-flag; direct API replay with stripped marker fails authentication entirely.
- Audit drill: fixture editing session replays action-for-action with before/after; client history view matches support view; append-only enforced at DB grants.
- Revocation drill: client revoke ends live sessions within seconds (in-flight mutation fails closed); complaint freeze blocks new starts while preserving read surfaces.
- Expiry drill: idle session dies at 30 min; active session hard-stops at 8 h requiring fresh step-up.

## Out of scope

- Roster, invites, roles, client linking (see `roster/team-roster.md`); reporting/leaderboards (see `reporting/client-reporting.md`); seat billing (see `billing/seat-billing.md`).
- Login/auth internals beyond step-up consumption; publish/automation/campaign execution rules (their docs — reachable only through the scoped allowlist).

## Open questions

- Q1: Concurrent Act-as-user sessions per staffer (e.g. max 3) — and per-client (one staffer at a time with presence lock)?
- Q2: Read-only shadow mode (staff previews without a session) — worth a lighter-weight path, or every access a full session?
