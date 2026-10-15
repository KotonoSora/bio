# Password + Sessions (reset, lockout, hygiene)

PLAN ref: P1a-1 (Better Auth on PG + security checklist S-5 abuse throttling). Auth library internals are a dependency — this doc owns the flows, guarantees, and probes around them, not the library's implementation.

## Purpose

Keep the front door both welcoming and barred: legitimate users recover access in minutes, while credential-stuffing, enumeration, and session theft fail silently against uniform responses, throttles, and short-lived tokens. Every flow here assumes the attacker already read this doc.

## Users

- User: *As a User, I want to sign up, log in, reset a forgotten password, and review and revoke sessions so that I recover access in minutes while my account stays barred to attackers.* Signs up, logs in, resets a forgotten password, reviews and revokes sessions.
- Attacker (adversarial): harvests no user list, stuffs no credentials at scale, replays no reset token.
- Support: *As a Support, I want to verify identity for locked-out users without ever seeing secrets so that assisted recovery stays auditable.* Verifies identity for locked-out users without ever seeing secrets.

## Functional requirements

1. Password rules (closed, enforced server-side): minimum length + breach-list screening (haveibeenpwned-style k-anonymity check or vendored list — see Q1), no complexity-theater composition rules, no periodic rotation. Storage is the auth library's salted hash (argon2/bcrypt per library default) — plaintext or reversible passwords never exist anywhere (logs, DB, exports).
2. Reset flow: request (email) → uniform "if an account exists, a reset link is on its way" response (no enumeration via timing or wording) → single-use signed token link (15–60 min expiry, see Q2; bound to the requesting account + password version) → new-password set → all other sessions revoked + notification email ("your password was changed; reply if this wasn't you"). Token reuse, expiry, or account-mismatch fails closed with a generic message; rate-limited per email + IP (Turnstile on the request step per S-5).
3. Login hardening: per-account + per-IP throttling with exponential backoff (never a lockout that lets attackers deny service to victims — throttles slow, lockouts are short and IP-scoped); breached-password login triggers a forced-reset nudge, not a silent pass; concurrent-session visibility in account settings (device, IP-class, last-active) with one-click revoke per session and revoke-all.
4. Session posture: cookie sessions (per the stack contract), `HttpOnly` + `Secure` + `SameSite=Lax`, short idle timeout with sliding renewal capped at an absolute lifetime (see Q2); privilege step-up (password/webauthn re-auth ≤10 min old) required for billing changes, username change, account deletion, Act-as-user start, and billing-contact transfer — the sensitive actions named across sibling docs all consume this one primitive.
5. Signup/login abuse: Turnstile + WAF/rate rules on all anonymous auth routes from P0 (per PLAN); disposable-email policy consistent with the leads decision (see leads Q2 — one platform rule, decided once); no auth-state oracle differences between existing and non-existing accounts on any endpoint.
6. Support recovery: locked-out users verify via a documented identity path (billing receipt + access to the account email, or org-owner attestation for staff seats) — support can trigger a fresh reset link, never set or view a password; every assisted recovery is audit-logged.

## Data / API touches

- Auth tables owned by the Better Auth schema (session/user/account/verification) with RLS on and least-privilege app role per P1a-1; new: `password_versions` counter (reset invalidates outstanding tokens) + `step_up_grants{user_id, granted_at, scope}` (10-min TTL). No custom credential storage. All PG via `worker/db/*`.
- Endpoints (auth-library-backed, throttled): signup/login/logout, reset request/confirm, session list/revoke, step-up challenge. Uniform error shapes across all of them (single error-code enum, no per-case messages that leak existence).
- Secrets: mail-sending keys, breach-check keys via `wrangler secret`; rotation per P5-4; `.gitignore` guards (`.wrangler/`, `.dev.vars*`) from P0.

## Acceptance criteria

- Enumeration drill: reset/login/signup responses byte-equivalent (modulo timing-noise budget) for existing vs non-existing accounts; timing analysis shows no reliable oracle. *GWT: Given existing vs non-existing accounts; When reset/login/signup is attempted; Then responses are byte-equivalent within the timing-noise budget with no reliable oracle.*
- Reset drill: token single-use (replay fails), expiry enforced, cross-account use fails, post-reset all other sessions dead + notification sent. *GWT: Given an issued reset token; When it is replayed, expired, used cross-account, or consumed validly; Then replays, expiries, and cross-account uses fail closed, and valid use kills other sessions plus sends notification.*
- Stuffing drill: credential-burst throttled per account+IP with backoff; no victim lockout exceeding the short IP-scoped window. *GWT: Given a credential burst; When per-account plus per-IP throttling applies; Then requests back off with no victim lockout beyond the short IP-scoped window.*
- Step-up drill: each sensitive action without a fresh grant refused (403 with the step-up path); grant older than 10 min refused. *GWT: Given a sensitive action without a fresh grant or with a grant older than 10 min; When the action is attempted; Then it is refused with 403 plus the step-up path.*
- Support drill: assisted recovery completes without any party viewing a password; audit row present with actor + identity evidence cited. *GWT: Given a locked-out user; When assisted recovery completes; Then no party views a password and an audit row cites actor plus identity evidence.*

## Out of scope

- Plan/subscription/billing mechanics (`monetize/`); account deletion (see `lifecycle/account-deletion.md`); custom domains (see `domains/custom-domains.md`); agency sessions (see `docs/agency/` — which consume step-up from here).
- OAuth social login (named future; password-first in v1 — see Q1 in spirit); passkey-first UX (v2 candidate).

## Open questions

- Q1: Breach-screening mechanism (k-anonymity API vs vendored list) and social-login priority — which ships first after password launch?
- Q2: Timeout values (reset-token TTL, idle vs absolute session lifetimes) — launch numbers?
