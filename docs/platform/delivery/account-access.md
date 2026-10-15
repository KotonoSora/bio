# Account Access (execution plan A0 — SSO identity, authorization, lifecycle)

PLAN ref: P1a-1 (auth on PG + RLS + least-privilege roles + 30-day delete flow) + P3-3/P3-4 gates and skeleton hooks + S-1…S-7. BA source: `docs/account/access/sso-login.md`, `docs/account/access/authorization-model.md`, `docs/account/access/password-security.md` (superseded for credentials; timeouts/step-up consumed), `docs/account/lifecycle/account-deletion.md`, `docs/account/plans/tier-presentation.md`. Builds on `docs/platform/delivery/scaffold-platform.md`. DEVIATION CARRIED: SSO-only (no password hashes, no reset flow) per product decision; Spring Security + PG sessions replace Better Auth defaults; contract frozen.

## Purpose

Give every later increment one identity truth: SSO-only login with stable provider-`sub` resolution, deny-by-default authorization with double enforcement, server-side plan gates, audited step-up for sensitive actions, and a 30-day deletion flow with purge certificate. A0 exits with password auth provably dead and IDOR/RLS/gate drills green in one container image.

## Users

- Creator/visitor: one-click signup/login, links extra providers, revokes sessions, deletes account with grace.
- Collaborator/agency operator: acts only inside ownership, plan, or org-role grants (P4 mechanics consume this model).
- Support/ops: recovers access via one-time re-link (never sets credentials); rotates IdP secrets without deploys.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for A0 — monolith `platform`/`account` module, not a standalone auth service. Monolith pros: session validation + grant loading (`ownership + entitlement + org memberships`) runs in-request with local RLS; no token-translation boundary, no second session store, one audit log. Microservices cons avoided: an auth-service split would add per-request auth round-trips on the hottest read path, cross-service IDOR surface, and dual session/revocation semantics with no scaling asymmetry (auth checks scale with the same requests they guard). Split trigger recorded: none fires before P5; extract only on measured grant-load evidence.
2. SSO-only login: closed launch set (Google/Apple/Facebook/LinkedIn OIDC/OAuth2 + generic OIDC adapter reserved); login screen shows provider buttons only — no password fields, no reset entry. Standard PKCE flow (`POST /api/auth/sign-in/social` → 302 with `state`+`nonce`+PKCE → `GET /api/auth/callback/:provider` validates, exchanges server-side, find-or-creates `users` by `(provider_id, provider_sub)` stable `sub`, never email) → server session cookie → same-origin `callbackURL` allowlist; generic `auth_failed` errors (no existence oracle). Password endpoints disabled (404/`auth_disabled`); no `password_hash` column populated (grep + runtime probe). Identity rules: IdP-verified email stored, unverified/absent still creates account with completion prompt; IdP email change updates only when verified and collision-free (collision → `sub`-matched login + conflict notice, never silent merge); Apple private-relay aware.
3. Linking/sessions/recovery: link extra providers (fresh ≤10-min SSO step-up required); last link unremovable; first login routes to choose-username (lowercase, reserved slugs app + DB check, race-safe); avatar prefill re-fetched server-side with 2MB + magic-byte + random key; server-side sessions with idle + absolute lifetimes, per-session revoke + revoke-all; logout revokes + clears cookie. Lockout recovery via short-lived single-use signed re-link invitation (billing-receipt or org-owner attestation), fully audited.
4. Authorization (deny-by-default, double-enforced): valid SSO session required for all non-public calls (server `user_id` only; client `user_id` ignored); app middleware scopes every query by `user_id` AND RLS re-checks (`current_user_id = user_id`, membership predicates for org rows) — either layer denies alone. Owner full CRUD subject to plan gates and deletion-grace state; public reads scope `username + is_public=1`; private content 404s on API (no existence oracle), forbidden mutations 403. Plan gates read the entitlement ledger only (lapsed pro loses capability in-window; serving stops, data retained). Org roles `owner > manager > member` (+ P4 `client`) with fixed capability matrix, per-profile assignment, ≤60s membership cache. No v1 break-glass admin (support-recovery only). Step-up (fresh SSO ≤10 min) for billing/username/unlink/deletion/Act-as-user/billing-contact-transfer. Append-only `audit_log{actor_id, subject_user_id?, action, target, at, ip_class}` for role/grant/plan/username/domain/impersonation/recovery events.
5. Lifecycle and tiers: 30-day deletion (immediate deactivation, purge after grace, purge certificate); tier presentation free/pro (Plus naming not copied; yearly/trial values per finance sign-off) with `GET /api/v1/plan` reading the ledger. Concierge (`docs/account/concierge/managed-service.md`) stays a staffed P4-revenue service — no code in A0. Abuse: Turnstile + WAF/rate rules on `/api/auth/*` from P0, per-IP/provider backoff, single-use 10-min `state`/PKCE; uniform auth timing/wording.
6. Container per release: E0 image plus account module; `docker run` and `podman run` parity per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Tables: `users{id TEXT PK, email, email_verified, name, image_key?, created_at, updated_at}`, `linked_accounts{(provider_id, provider_sub) UNIQUE, user_id + index + RLS}`, `sessions`, single-use `verification{state/nonce/PKCE, expires_at}` (10-min TTL), `audit_log` (no app-role UPDATE/DELETE). Every business table keeps `user_id TEXT NOT NULL` + index + RLS.
- Endpoints: `POST /api/auth/sign-in/social`, `GET /api/auth/callback/:provider`, link/unlink (step-up, last-link guard), sign-out, `GET /api/v1/me/sessions` + revokes, `GET /api/v1/plan`, username check/change, deletion request/confirm. IdP `client_id` env + `client_secret` via vault; KV-style throttles in the cache layer, no PII beyond hashed IP.
- Login vault strictly separate from P4 publishing token vault (short-lived code artifacts never stored vs encrypted long-lived grants, never shared rows).

## Acceptance criteria

- Password-dead drill: credential endpoints 404/`auth_disabled`; no populated verifiers; no password UI.
- Login drill per provider × new/returning: stable `users.id` across IdP email change; private-relay/denied-email succeeds with prompt; replayed code/state, PKCE mismatch, expired state, off-allowlist callback all fail closed.
- Link drill: second provider links with step-up only; last-provider unlink refused; both logins resolve to one id.
- IDOR/RLS drill (S-1/S-6): cross-user reads/writes/deletes 403 (404 for private reads); app-role PG returns zero foreign rows; audit covers link/unlink/recovery/roles.
- Gate/step-up drill: lapsed-pro pro-calls refused with upgrade path; sensitive actions without fresh re-auth refused; revoked member denied within 60s; Act-as-user outside assignment refused + logged.
- Abuse drill (S-5): auth bursts throttled; no enumeration oracle; IdP-supplied markup stripped with CSP holding (S-2/S-4 for avatar prefill).
- Container drill: digest pair boots, compose green, rollback green, OpenAPI diff clean.

## Out of scope

- Publishing OAuth vault, fan-out, automations, broadcasts (P4 — see `docs/platform/delivery/publish-automate.md`); agency roster/reporting/seat detail (see `docs/platform/delivery/agency-teams.md`); checkout/ledger mechanics (M0–M2); custom-domain verification mechanics (see `docs/account/domains/custom-domains.md` — execution triple pending in the account queue); passkeys/WebAuthn (v2 candidate).

## Open questions

- Q1: Launch provider order — all four day-one vs Google + Apple first?
- Q2: API 403-vs-404 split (403 mutations / 404 private reads) — confirm or uniform 404?
- Q3: Membership cache TTL (60s proposed) vs per-request load at 100k mix — performance sign-off?
