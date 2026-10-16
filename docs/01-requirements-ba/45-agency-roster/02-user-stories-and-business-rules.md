# Agency Roster - User Stories and Business Rules (feature 45)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/45-agency-roster/`. Scope: `docs/01-requirements-ba/45-agency-roster/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Agency owner | create the org, invite managers/members/clients, assign roles, remove people | membership roots all agency power exactly | High |
| US-02 | Manager | invite members and clients and manage the roster without touching the owner role | daily operations stay within the matrix | High |
| US-03 | Member/client | accept an invite, appear in the roster, work within my scope | clients see only their own profiles | High |
| US-04 | Staff | start a scoped session on an assigned client profile, edit, and end it | every minute is attributable and revocable | High |
| US-05 | Client | see who accessed my profile, when, and what changed, with one-click revoke | impersonation stays visible to me | High |
| US-06 | Auditor/support | replay any session's actions from the audit trail | disputes resolve from evidence | High |

## Business rules

- **BR-01 Org lifecycle.** One user may own multiple orgs; creation names org + owner (creator); settings (name, logo, contact) editable by owner/manager; deletion is a typed-confirm, audited, reversible-within-grace (30 days) suspension — never a silent cascade; restore within grace returns the org intact.
- **BR-02 Invites.** Inviter enters platform username + role (+ client scope); signed single-use token hashed at rest with 7-day expiry; acceptance links an existing account; unknown usernames get invite-to-register converting on signup (no shadow accounts, no duplicates); re-invites rate-limited; declines recorded without nag loops.
- **BR-03 Roles (closed set, server-side).** `owner` (full incl. billing contact, role grants, delete), `manager` (invite/remove members + clients; assign member/client roles; never the owner role or billing), `member` (assigned-profile work via Act-as-user; no roster changes), `client` (own profiles + reports; no staff tooling). Every endpoint checks membership + role per request; UI tabs are never the gate.
- **BR-04 Client linking (aggregation only).** `org_clients` attaches `profile_id` + `user_id` without changing ownership; staff-initiated links pend until the profile owner accepts; either-side unlink severs reporting + Act-as-user immediately; staff assignment to linked profiles is explicit (`client_assignments`) — an unassigned member holds no powers over a client.
- **BR-05 Instant effect.** Role changes, removals, suspensions, and unlinks apply on the next request (sessions re-checked; no stale-privilege window beyond token TTL); suspension freezes all agency powers instantly and is reversible by owner/manager.
- **BR-06 Multi-org hygiene.** Per-org scoped sessions via the active-org switcher; powers never bleed across orgs; roster views always state the active org.
- **BR-07 Act-as gates (all required).** Actor holds active staff membership in the org; target profile is org-linked AND assigned to the actor (membership alone never grants every client); client has not revoked agency access; actor passes fresh step-up (≤ 10 min old) at start. Missing any gate → 403 naming the condition, logged.
- **BR-08 Act-as scope (least privilege).** Session powers = exactly the client's own dashboard powers on the assigned profile(s) — blocks, theme, products, leads read, post scheduling — minus everything account-level: no password/email/plan changes, no billing, no username change, no account delete, no roster changes, no cross-profile export. Scope is a server-side allowlist per endpoint class, never UI hiding.
- **BR-09 Act-as mechanics.** Distinct identity class: short-TTL signed act-as tokens (`{actor, org, profile, act_as:true}`), separate issuer from login sessions (never confusable); 30-min idle TTL sliding with activity to an 8-h hard stop requiring fresh step-up; ≤ 3 concurrent sessions per staffer; one active editing session per profile (presence lock); client-visible presence indicator during active sessions.
- **BR-10 Audit (non-negotiable).** Session start/end + every mutating action append `audit_log{actor_staff_id, on_behalf_of_profile, action, before?, after?, at}` — before/after for content edits, references for publishes/sends; append-only (no UPDATE/DELETE grants); readable by the client (their profile's staff-action history), the org owner, and support; no staff-side redaction.
- **BR-11 Revocation.** Client one-click revoke kills all live sessions on their profiles instantly and unlinks (re-link needs fresh acceptance); owner/manager org-wide act-suspend kills staff sessions; revoked requests fail closed mid-flight (per-request check, not per page).
- **BR-12 Abuse posture.** Session-start velocity/impossible-travel anomalies alert ops; suspended members cannot start sessions by construction (membership check first); a client complaint freezes the implicated staffer's act-as power pending review (read surfaces continue).
- **BR-13 Seat seam.** Staff invites and Act-as-user starts require an active agency subscription (46's entitlement read, `GET /internal/v1/billing/agency/entitlement`); refusal is `agency_subscription_required` with the subscribe path — a lapse freezes staff powers, never client surfaces; clients never require seats.
- **BR-14 Audit retention + privacy.** Roster/act audits retained 24 months; org data scoped by RLS; invite tokens hashed; zero token material in logs; ops sees scoped audit reads only.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/45-agency-roster/06-acceptance-criteria.md`: invite, matrix, effect, client-link, act gate/scope/audit/revocation/expiry. GWT forms live there.
