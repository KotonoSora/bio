# SSO Login (Google, Apple, Facebook, LinkedIn + OIDC)

PLAN ref: P1a-1 (Better Auth on PG + session + 30-day delete flow). DEVIATION FLAGGED: PLAN P1a-1 assumes session + password reset; this spec replaces password auth with SSO-only per product decision. No `password` hashes, no reset flow. `password-security.md` is superseded for login/recovery — sessions/step-up/deletion mechanics there still apply but re-auth is SSO re-auth, not password.

## Purpose

Let every account be created and accessed exclusively through third-party SSO (Google, Apple, Facebook, LinkedIn, plus generic OIDC for future providers). motivation is breach-surface reduction: the platform never stores, recovers, or validates passwords, so credential-stuffing, password reuse, and reset-token abuse have no target. Verified email comes from the identity provider (IdP), not from user input, and all account identity resolves to `users{id}` with linked IdP rows.

## Users

- Creator/visitor: signs up or logs in with one click via a provider button; links extra providers for resilience; unlinks a provider without losing access.
- Returning user with changed email: still reaches the same account via stable provider `sub`, not via the mutable email string.
- Attacker (adversarial): forges no callback, replays no code, links no attacker IdP to a victim account, enumerates no user list via auth responses.
- Support/ops: recovers access without ever seeing or setting a secret; rotates provider client secrets without code deploys.

## Functional requirements

1. Provider set (closed at launch, extensible): Google (OIDC), Apple (OIDC + private-relay aware), Facebook (OAuth 2.0, email may be absent), LinkedIn (OIDC `openid profile email`). Generic OIDC adapter reserved for future providers (Microsoft, GitHub, Zalo) — adding a provider is config + client-ID/secret + callback registration, never a schema change. Login screen shows only enabled providers; no email/password fields, no "forgot password" entry point.
2. Password surface removed (hard): email/password signup, login, and reset endpoints disabled (return 404 or `auth_disabled` — never a working fallback). No `password_hash` column populated; any legacy hash rows (if migration fixtures exist) are ignored and flagged for purge. This is verified by grep gate + runtime probe, not by UI hiding alone.
3. Standard flow (all providers): `POST /api/auth/sign-in/social {provider, callbackURL}` → 302 to IdP with `state` + `nonce` + PKCE (`code_challenge`) → IdP consent → `GET /api/auth/callback/:provider?code=&state=` → server validates `state`/PKCE, exchanges code server-side, fetches verified profile (`sub`, `email`, `email_verified`, name, picture URL) → find-or-create `users` row → create cookie session → redirect to `callbackURL` (same-origin allowlist only, no open redirect). Failures return a generic `auth_failed` page (no provider-error passthrough that leaks account existence).
4. Identity resolution: primary key is `(provider_id, provider_sub)` in the linked-account table, never email. Verified email from IdP stored on `users{email, email_verified BOOL, updated_at}`; unverified or absent email (Facebook deny, Apple private relay) still creates the account — profile completion prompts for a contact email later, but login never blocks on it. Email change at the IdP updates `users.email` on next login only when `email_verified=1` and no collision with another `users.email`; collision → login succeeds to the `sub`-matched account and surfaces a "contact email conflict" notice, never merges accounts silently.
5. Linking/unlinking (authenticated only): settings → connect additional provider (same OAuth dance, then insert link row for current `user_id`). Unlink allowed only when ≥2 providers remain or an org-owner attestation path exists — the last link cannot be removed (prevents lockout). Linking requires a fresh SSO step-up (≤10 min, see `password-security.md` step-up primitive reused with SSO re-auth instead of password).
6. Username provisioning: first SSO login with no `profiles` row → redirect to choose-username screen (lowercase unique, reserved slugs `app,api,admin,static,r,cv,shop,login,signup,pricing,blog` rejected in app + DB check per P1a-2; retry-safe on race). Avatar prefill from IdP picture allowed only after re-fetch server-side + 2MB cap + magic-byte check → R2 random key (never hotlink IdP URLs in SSR). Display name prefill stripped of script/iframe/event-handlers (XSS baseline).
7. Sessions: cookie session per stack contract (`HttpOnly` + `Secure` + `SameSite=Lax`, server-side session table via Better Auth). Idle timeout + absolute lifetime per `password-security.md` Q2 decision (consumed, not re-decided). Sensitive actions (billing change, username change, account deletion, Act-as-user start) require fresh SSO re-auth ≤10 min. Session list + per-session revoke + revoke-all in account settings. Logout revokes server session + clears cookie on all tabs via `Max-Age=0`.
8. Abuse controls: Turnstile + WAF/rate rules on `/api/auth/*` from P0 (S-5); per-IP + per-provider-attempt throttling with exponential backoff; `state`/`nonce`/PKCE single-use with 10-min TTL (replay fails closed). Auth responses uniform across existing/non-existing accounts (no enumeration oracle in timing or wording).
9. Support recovery: no password to reset. Locked-out user (lost all IdP access) verifies via documented identity path (billing receipt + access to account email, or org-owner attestation) → support triggers a one-time re-link invitation (short-lived signed token, single-use, audited), never sets credentials. Every assisted recovery writes an `audit_log` row.

## Data / API touches

- Tables (Better Auth PG schema via `worker/db/*`, RLS on, least-privilege app role): `users{id TEXT PK, email TEXT, email_verified BOOL, name TEXT, image_r2_key TEXT?, created_at, updated_at}` + `linked_accounts{user_id TEXT NOT NULL + index + RLS, provider_id TEXT, provider_sub TEXT, email_at_link TEXT?, linked_at}` UNIQUE `(provider_id, provider_sub)` + standard `sessions{...}` + `verification{state/nonce/PKCE, expires_at}` (10-min TTL, single-use). Every business table keeps `user_id TEXT NOT NULL` + index + RLS; dashboard scopes by `user_id`, public scopes by `username + is_public=1`. No password/verifier columns in scope.
- Endpoints (Better Auth-backed, throttled): `POST /api/auth/sign-in/social`, `GET /api/auth/callback/:provider`, `POST /api/auth/link/:provider`, `POST /api/auth/unlink/:provider` (step-up gated, last-link guard), `POST /api/auth/sign-out`, `GET /api/v1/me/sessions` + revoke endpoints. Disabled: all email/password + reset endpoints (probe asserts 404/`auth_disabled`).
- Secrets/config: provider `client_id` (non-secret, env) + `client_secret` via `wrangler secret` (never in repo; `.gitignore` has `.wrangler/`, `.dev.vars*`); quarterly rotation per P5-4; Apple `team_id/key_id/private_key` same path. Callback URLs registered per environment (local `cloudflared tunnel → wrangler dev` for webhook-style testing where needed). KV `RATE_LIMIT` backs auth throttles; no PII in KV keys beyond hashed IP.
- Distinction: this login vault is separate from P4 `social_accounts{...access_token_enc...}` publishing vault (`cf-aes-gcm` + `TOKEN_ENC_KEY`, never logged). Login tokens are short-lived code-exchange artifacts, never stored; publishing tokens are long-lived grants stored encrypted. The two never share rows.

## Acceptance criteria

- Password-dead drill: email/password/signup/reset endpoints return 404 or `auth_disabled`; DB probe finds no populated password verifiers; UI contains no password field or reset link.
- Happy-path drill (per provider × new vs returning): new SSO → username screen → dashboard + session cookie flags correct; returning SSO → same `users.id` even after IdP email change (stable `sub` match); private-relay / email-denied logins succeed with completion prompt, not a block.
- Collision drill: IdP email matching another account does not merge — login lands on `sub`-matched account with conflict notice; no cross-user data exposure.
- Callback-security drill: replayed `code`/`state`, mismatched PKCE, expired `state` (>10 min), cross-account `state` reuse, and off-allowlist `callbackURL` all fail closed with generic error; no session issued.
- Linking drill: second provider links with fresh step-up only; unlink of last provider refused; unlink with ≥2 succeeds and both logins still resolve to one `users.id`.
- IDOR/RLS drill (S-1/S-6): `userA` session cannot read/write `userB` profile/block/order/deletion row (403 at API + denied at DB as app role); `audit_log` covers link/unlink/recovery/impersonation events.
- Abuse drill (S-5): rapid `/api/auth/*` bursts throttled (Turnstile + WAF + per-IP backoff); enumeration analysis shows no reliable existing-vs-new oracle.
- XSS/CSP drill (S-2): IdP-supplied name/picture-borne markup stripped; CSP holds on login/callback/username screens.
- Secrets drill: `grep -r client_secret|private_key` finds nothing in repo; rotation runbook executes without downtime; avatar prefill rejects SVG/HTML/5MB fakes (S-4).

## Out of scope

- Password auth, reset, breach-list screening, and password step-up (see `access/password-security.md` — superseded for credentials; sessions/step-up timeouts consumed from there with SSO re-auth substituted).
- Publishing OAuth vault, per-network tokens, fan-out/scheduler (`docs/publish/`, `docs/automate/`; P4 only — never stubbed here).
- Native mobile SDK login (PWA per PLAN §P0 stack decision); passkey/WebAuthn-first UX (v2 candidate); SCIM/directory sync; advertiser-side auth.
- Account deletion mechanics themselves (see `lifecycle/account-deletion.md` — re-auth step now SSO); plan/billing gates (`monetize/`); custom domains (`domains/custom-domains.md`).

## Open questions

- Q1: Launch provider order — all four day-one, or Google + Apple first with Facebook/LinkedIn staged (review overhead vs coverage)?
- Q2: Email-collision UX copy and contact-email verification (Resend code?) for IdP-email-absent accounts — block checkout until verified?
- Q3: Apple private-relay relay-address handling for transactional mail (receipts, deletion reminders) — forward via relay or require a direct contact email?
