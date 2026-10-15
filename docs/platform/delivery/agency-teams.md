# Agency Teams (execution plan P4b — roster, Act-as-user, reporting, seats)

PLAN ref: P4-5 (invite-by-username, owner/manager/member, scoped Act-as-user + audit, leaderboard) + P3-4 skeleton (inherits `orgs`/`org_members`, no rewrite) + S-1/S-6. BA source: `docs/agency/roster/team-roster.md`, `docs/agency/access/act-as-user.md`, `docs/agency/reporting/client-reporting.md`, `docs/agency/billing/seat-billing.md`. Builds on A0 authorization; gated like P4a. DEVIATION CARRIED: Spring Boot service + containerized PG replace Worker defaults; role matrix and audit semantics retained; contract frozen.

## Purpose

Give agencies a master account over people and client profiles: exact invites and roles, scoped audited Act-as-user editing, combined reporting without ownership transfer, and per-seat billing. P4b exits with the permission-matrix fixture green and every impersonated action attributable to both actor and subject.

## Users

- Agency owner: creates orgs, invites staff/clients, grants roles, manages billing contact, deletes orgs.
- Manager/member/client: works assigned profiles within role; clients see own profiles + reports only.
- Support/auditor: reconstructs any agency action from `audit_log` alone.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for P4b — monolith `agency` module, not an org service. Monolith pros: roster + membership checks + Act-as-user scoping evaluate in-request against the A0 grant loader (≤60s membership cache); reporting reads the same aggregates B2/M2 expose; seat charges ride the M0 dispatcher; one audit log. Microservices cons avoided: extracting orgs would split the authorization hot path (every request pays a cross-service membership lookup or stale-cache risk) with no team-ownership or ≥10x asymmetry evidence at P4 entry. Split trigger recorded: read-model replica for combined reporting only on measured reporting-load evidence at P5.
2. Roster kernel: inherits skeleton `orgs` + `org_members{role, status}` (migration adds `client` to the role enum, flagged/versioned) + new `org_clients{org_id, profile_id, user_id, status}` (aggregate linking — profiles stay user-owned, never transferred) + `org_invites{token_hash, org_id, username, role, expires_at, status}` (hashed at rest, single-use, 7-day expiry). Multi-org ownership allowed; settings editable by owner/manager; org delete = confirm-typed, audited, reversible-within-grace suspension (no silent cascade). Invite-by-username: dashboard invite + transactional email; unknown usernames get invite-to-register converting on signup (no shadow accounts, no duplicates); re-invites rate-limited; declines recorded without nag loops. Roles closed set with server-side matrix: owner (roles, billing, delete), manager (invite/remove members+clients, no owner/billing changes), member (assigned-profile work only), client (own profiles + reports only). Client linking requires profile-owner acceptance when staff-initiated; either-side unlink severs reporting + Act-as-user immediately. Mutations effective next request (no stale-privilege window beyond token TTL); per-org scoped sessions via active-org switcher (powers never bleed across orgs); suspensions freeze all agency powers instantly.
3. Act-as-user kernel: entry only via scoped session (assigned profiles, time-boxed, fresh SSO step-up); every action stamped `actor_id` + `subject_user_id` in `audit_log`; denied attempts logged; no global admin bypass in v1.
4. Reporting kernel: per-profile views/clicks/CTR/sales rollups plus combined org leaderboard reading B2/M2 aggregates (never recomputed); client views restricted to linked profiles; export with source + generated-at columns.
5. Seat billing kernel: per-seat counting from active roster states (invited-pending excluded, suspended included per policy as decided); seat changes prorate through the M2 lifecycle (explicit re-purchase math, no card-on-file where one-shot rails apply); billing-contact transfer step-up gated; downgrade severs paid agency powers with data retained.
6. Security baseline: permission-matrix fixture (role × every agency endpoint) enforced server-side (S-1/S-6 403 + RLS deny + audit-flagged escalation attempts); effect-latency and unlink probes; secrets via environment only.
7. Container per release: P4b tag runs via `docker run` and `podman run` against PG `18.6` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Tables: inherited `orgs`/`org_members` + `org_clients` + `org_invites`; standard RLS (members see own orgs; writes role-gated). Session carries `active_org_id`; privilege checks evaluate membership + role per request.
- Endpoints: org CRUD, invite send/accept/decline/revoke, role grant/revoke, client link/unlink, suspend/reinstate, Users/Managers tab reads, scoped Act-as-user start/stop, reporting reads, seat/billing-contact endpoints — all role-checked server-side (UI tabs never the gate).
- Audit: every mutation with actor + before/after; Act-as-user rows carry both ids; support recovery rows included.

## Acceptance criteria

- Invite drill: accept links exact role; expired/used tokens rejected; unknown-username converts on signup without duplicates.
- Matrix drill: full role × endpoint fixture green; member→owner escalation 403 + audit-flagged.
- Effect drill: removed/unlinked users lose reporting + Act-as-user on next request; staff-initiated client links pend until owner accepts; `user_id` ownership never moves.
- Act-as-user drill: outside assignment/time-box refused + logged; every action attributable to actor + subject.
- Reporting drill: combined figures match B2/M2 origins cell-for-cell; clients see only linked profiles.
- Seat drill: roster-state counting exact; prorated changes settle through the money ledger; billing-contact transfer step-up gated.
- Container drill: digest pair boots, compose green, rollback green, OpenAPI diff clean.

## Out of scope

- Skeleton DDL authorship (consumed, not rewritten); plan/billing internals (M0–M2); publishing/automation execution (see `docs/platform/delivery/publish-automate.md`); profile ownership transfer (not v1); replicas/ISR (see `docs/platform/delivery/scale-out.md`).

## Open questions

- Q1: Self-serve org creation for any pro user vs agency-plan gate?
- Q2: Client role granularity — flat client role enough for v1?
