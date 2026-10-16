# Account and Access - Context and Scope (feature 01)

Phase 1 BA · Feature 01 of 53 (account) · Implemented by `docs/02-architecture-techlead/01-account-and-access/` (5 files). Consumers: features 02-03, 05 (same service), 04 (custom domains), 06 (first-login handoff), 09, 13-16 (entitlement snapshot), 45-47 (org roles, Act-as-user mechanics).

## Purpose

One identity truth for a passwordless platform: every account is created and reached exclusively through third-party SSO; every "can X do Y" question resolves deny-by-default from a server-held session; sensitive actions demand fresh re-authentication; and every feature reads plan entitlements from one snapshot contract instead of inventing its own.

## Objectives

- O-1 SSO-only identity: Google, Apple, Facebook, LinkedIn + generic OIDC adapter; no password exists anywhere (no hash, no reset).
- O-2 Stable identity: `(provider_id, provider_sub)` is the key; a mutable email string never locks anyone out or merges two accounts.
- O-3 Deny-by-default authorization: server-set `user_id`, app scoping plus RLS, one fixed 403/404 split, no v1 bypass.
- O-4 Session and step-up lifecycle: server-side sessions, per-session revoke, 10-minute freshness gate for sensitive actions.
- O-5 Entitlement snapshot: one internal contract (`≤ 30 s` staleness, fail-closed to free) that features 06, 09, 13-16 and later majors read for plan gates.
- O-6 Auditable operations: link/unlink/recovery/role/plan events reconstructable from the append-only audit log alone.

## In scope

- SSO login/logout, provider linking/unlinking, first-login username handoff, contact-email capture and code verification.
- Session registry, edge validation contract, idle/absolute lifetimes, session list + revoke + revoke-all-others.
- Step-up (fresh SSO re-auth) primitive for the named sensitive actions.
- Entitlement snapshot internal API and fail-closed consumer rule.
- Fixed org role matrix (mechanics land with features 45-47), audit events, support re-link recovery, abuse controls.

## Out of scope

- Password auth, reset, breach-list screening (dead — SSO-only supersedes; `password-security` material archived; passkeys are a v2 candidate).
- Account deletion mechanics (feature 02), tier presentation (feature 03), custom-domain verification/serving (feature 04), concierge (feature 05).
- Plan definitions, pricing, ledger, checkout, renewal math (features 32-40 — this feature exposes only the snapshot contract and the deliverable-email signal).
- Org membership data, Act-as-user mechanics, agency roster/reporting (features 45-47 — they consume the matrix fixed here).
- Publishing OAuth token vault and fan-out (features 41-44 — strictly separate from login; the two never share rows).

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Visitor (unauthenticated) | Create only: an account via an SSO provider | Signs up or logs in with one click; reaches nothing owner-scoped. |
| Creator (account owner) | Full CRUD on own identity and sessions; read own entitlements | Owns the account; revokes sessions; re-auths for sensitive actions. |
| Collaborator - owner (org) | Full CRUD within the org per the fixed matrix: roles, billing contact, org content, dissolution | Grants roles; accountable for the org. Mechanics: features 45-47. |
| Collaborator - manager | Create invites; update assigned profiles/reports | Acts on assigned profiles only; no role management. |
| Collaborator - member | Update assigned profiles only; read assigned otherwise | Sees nothing unassigned; role revocation effective `≤ 60 s`. |
| Support/ops | Create one recovery invitation after identity evidence; read nothing else; never sets credentials | Assisted recovery and secret rotation, fully audited. |
| Auditor | Read the audit log only; update nothing | Reconstructs who did what without cross-user content access. |
| Attacker (adversarial) | none | Forges no callback, replays nothing, enumerates nothing, escalates nothing. |

Org matrix is `owner > manager > member`, assigned per profile; unassigned profiles are invisible; grants lapse on revocation within 60 s. Features reference this matrix, never redefine it.

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Provider order | All four enabled day-one; per-environment enable flags allow staged rollout with no code change. |
| Q-02 Email collision / contact email | Collision never merges: `sub`-matched login + `email_conflict` notice. Contact email verified by 6-digit code (15 min TTL, 5 attempts). Checkout requires a verified deliverable address — gate lands with features 32-40; signal exposed here. |
| Q-03 Apple private relay | Relay address is accepted and used for transactional mail; on bounce, prompt for a direct contact email. |
| Q-04 403 vs 404 | 403 for forbidden mutations on known-owned targets; 404 for private reads. Decided once here; features cite it. |
| Q-05 Membership cache | `≤ 60 s` effect; performance sign-off at the baseline E3 load gate. |
| Q-06 Break-glass admin | None in v1; support recovery only. Audited two-person break-glass requires a flagged amendment. |
| Q-07 Session lifetimes | Idle 7 days sliding; absolute 90 days; step-up freshness stays 10 minutes. |
| Q-08 Password-era questions | Breach screening and reset flows dead; session/step-up hygiene consumed into this feature. |

Related: user stories and rules in `docs/01-requirements-ba/01-account-and-access/02-user-stories-and-business-rules.md`; error surface in `docs/01-requirements-ba/01-account-and-access/05-exception-handling-and-errors.md`; contracts in `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`.
