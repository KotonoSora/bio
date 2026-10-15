# Team Roster (orgs, invites, roles)

PLAN ref: P4-5 (agency: invite-by-username, roles owner/manager/member) + P3-4 skeleton (inherits `orgs` / `org_members` DDL — see `monetize/organizations/agency-skeleton.md`, no rewrite). P4 scope — do NOT build in P1–P3. Act-as-user sessions in `access/act-as-user.md`; reporting in `reporting/client-reporting.md`; seats in `billing/seat-billing.md`.

## Purpose

Give agencies a master account that holds people and client profiles together: create an org, invite staff and clients by username, assign owner/manager/member roles, and manage everything from Users and Managers tabs. Membership is the root of all agency power — reporting, Act-as-user, and billing all read it — so invites, roles, and removals must be exact, audited, and instantly effective.

## Users

- Agency owner: *As an Agency owner, I want to create the org, invite managers/members/clients, assign roles, and remove people so that membership roots all agency power exactly.* Creates the org, invites managers/members/clients, assigns roles, removes people.
- Manager: *As a Manager, I want to invite members and clients and manage the day-to-day roster without touching the owner role so that daily operations stay within my matrix.* Invites members and clients, manages day-to-day roster (cannot remove the owner or promote to owner).
- Member/client: *As a Member/client, I want to accept an invite, appear in the roster, and work within my scope so that clients see only their own profiles.* Accepts an invite, appears in the roster, works within their scope (clients see only their own profiles).
- Future P4 developer: *As a Future P4 developer, I want to build on skeleton tables without migration surgery so that P4 agency work inherits the skeleton DDL.* Builds on skeleton tables without migration surgery.

## Functional requirements

1. Org lifecycle: one user may own multiple orgs; org creation names the org + owner (creator); org settings (name, logo, contact) editable by owner/manager. Deleting an org suspends (per the skeleton's no-silent-cascade rule) with member/client data retained per the retention decision — deletion is a confirm-typed, audited, reversible-within-grace action.
2. Invite-by-username: inviter enters a platform username + role/client-scope; invitee gets an in-dashboard invite + email notice (transactional class); invites carry signed single-use tokens with 7-day expiry; acceptance links an existing account (no shadow accounts — unknown usernames get an invite-to-register flow that converts to membership on signup). Re-invite rate-limited; declined invites recorded (no nag loops).
3. Roles (closed set, enforced server-side on every endpoint): `owner` (full control incl. billing + role grants + org delete), `manager` (invite/remove members and clients, assign member/client roles, never touch owner role or billing), `member` (staff: works assigned client profiles via Act-as-user, no roster changes), `client` (profile owner linked for reporting/service: sees own profiles + reports, never staff tooling). Display maps to link.me's Users tab (members + clients listed) and Managers tab (role assignment surface) — tabs are views, roles are the truth.
4. Client-profile linking: client membership attaches `profile_id`s (their claimed profiles) to the org as `org_clients{org_id, profile_id, user_id}` — profiles stay user-owned (answers the skeleton's Q2: aggregate, no ownership transfer in v1). Linking requires the profile owner's acceptance when initiated by staff; unlinking by either side severs reporting + Act-as-user immediately.
5. Instant effect + audit: role changes, removals, and unlinkings take effect on the next request (sessions re-checked, no stale-privilege window beyond token TTL); every roster mutation appends `audit_log{actor, action, target, at}` with before/after roles. Suspensions (compromised accounts) freeze all agency powers instantly, reversible by owner/manager.
6. Multi-org hygiene: users in several orgs get per-org scoped sessions (active-org switcher; powers never bleed across orgs); roster views always state the active org.

## Data / API touches

- Inherits `orgs` + `org_members{role, status}` (adds `client` to the role enum via migration — the skeleton's three roles extended, flagged and versioned) + new `org_clients{org_id, profile_id, user_id, status}` + `org_invites{token_hash, org_id, username, role, expires_at, status}` (tokens hashed at rest, single-use). Standard RLS (members see own orgs; writes role-gated); all PG via `worker/db/*`.
- Endpoints: org CRUD, invite send/accept/decline/revoke, role grant/revoke, client link/unlink, member suspend/reinstate, Users/Managers tab reads (all role-checked server-side; UI tabs never the gate). Session carries `active_org_id`; privilege checks evaluate membership + role per request.

## Acceptance criteria

- Invite drill: username invite → accept links accounts with the exact role; expired/used token rejected; unknown-username flow converts on signup without duplicate rows. *GWT: Given a username invite, expired/used token, or unknown username; When accept or signup runs; Then valid invites link with the exact role, expired/used tokens are rejected, and unknown-username flows convert without duplicates.*
- Role drill: permission matrix fixture (owner/manager/member/client × every agency endpoint) green; direct-API role-escalation attempts (member→grant-owner) rejected 403 + audit-flagged. *GWT: Given the role matrix and a direct-API escalation attempt; When endpoints are exercised; Then the matrix fixture is green and escalation is rejected 403 plus audit-flagged.*
- Effect drill: removed/unlinked user loses reporting + Act-as-user on the next request (stale-session probe fails closed within TTL). *GWT: Given a removed/unlinked user; When the next request runs; Then reporting plus Act-as-user are lost and stale sessions fail closed within TTL.*
- Client-link drill: staff-initiated link pending until owner accepts; either-side unlink severs access immediately; profiles never change `user_id` ownership. *GWT: Given a staff-initiated link or an either-side unlink; When acceptance or unlink completes; Then staff links stay pending until owner accepts, unlinks sever immediately, and profiles never change ownership.*
- Audit drill: every mutation in `audit_log` with actor + before/after; suspension freezes powers verifiably across all surfaces. *GWT: Given any roster mutation or suspension; When audited or powers are probed; Then every mutation carries actor plus before/after and suspensions freeze powers across all surfaces.*

## Out of scope

- Scoped editing sessions (see `access/act-as-user.md`); leaderboards and rollups (see `reporting/client-reporting.md`); seat counting and charges (see `billing/seat-billing.md`).
- Skeleton DDL itself (`monetize/organizations/agency-skeleton.md`); plan/billing internals (`monetize/`); profile ownership transfer (not in v1).

## Open questions

- Q1: Self-serve org creation for any pro user, or agency plan gate (org features as the agency-tier differentiator)?
- Q2: Client role granularity (view-only vs comment vs approve) — flat client role enough for v1?
