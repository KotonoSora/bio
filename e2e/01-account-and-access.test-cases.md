# Test cases — feature 01 account-and-access (e2e)

Derived from BA `docs/01-requirements-ba/01-account-and-access/06-acceptance-criteria.md` (AC-01..AC-15) and TL `docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`. Layer: browser/edge e2e against the compose stack (`http://localhost`, through nginx). Stable IDs; never renumber.

Suites: `tests/01-account-and-access.spec.ts` (default, no IdP needed) · `tests/01-account-and-access.full-login.spec.ts` (opt-in, login dance per case) · `tests/01-account-and-access.authenticated.spec.ts` (opt-in, one login for the whole run). Runbook at the bottom.

## Default suite — runs on the standing dev stack

| ID | AC | Given | When | Then |
|---|---|---|---|---|
| E2E-01-01 | AC-01, AC-02 | Login page | Rendered | "Dev SSO" button visible, zero password inputs |
| E2E-01-02 | AC-01 | Password legacy paths | Email/signup/reset probes POSTed | `404 auth_disabled` on all four, nothing issued |
| E2E-01-03 | AC-04, AC-06 | `?notice=failed` / `?notice=expired` | Login renders | Generic failure / session-ended notices |
| E2E-01-04 | AC-04 | `next` = `https://evil`, `//evil`, `/welcome/username` | Form renders | Hidden callbackURL clamps to `/app`, allowlisted value preserved |
| E2E-01-05 | AC-02 | — | `GET /api/auth/providers` | Non-empty list, unique id+label, dev SSO present |
| E2E-01-06 | AC-04 | Unknown provider id | `POST /api/auth/sign-in/social` | `422 validation_failed`, generic detail (no oracle) |
| E2E-01-07 | AC-02, AC-04 | Click Dev SSO | Navigation POST + 302 Location | `state`, `nonce`, S256 PKCE, exact redirect_uri asserted from the 302; click performs the nav POST; csrf cookie Secure/readable/Lax |
| E2E-01-08 | AC-04 | Malformed callbacks (no params / code only / bogus pair) | Callback hit | All land `/app/auth/error`; no `rb_session` |
| E2E-01-09 | AC-04 | Fresh state from sign-in initiation | Bogus code twice | First: exchange fails; replay: state consumed; both `/auth/error` |
| E2E-01-10 | AC-04 | State minted for generic-oidc | Replayed on linkedin callback | Rejected `/auth/error` |
| E2E-01-11 | AC-06, AC-13 | Anonymous browser | Dashboard opened | Bounces to `/app/login?notice=expired` |
| E2E-01-12 | AC-08 | Anonymous browser | `/app/account/settings` opened | MFE mount gated, same bounce (no foreign data) |
| E2E-01-13 | AC-08, AC-14 | Anonymous | `/api/v1/me`, `/me/sessions`, `/me/providers` | `401 unauthenticated` problem+json, `no-store` |
| E2E-01-14 | AC-08 | Anonymous | Unknown `/api/*` route | `401` deny-by-default, no route-existence oracle |
| E2E-01-15 | S-6 | Mutation (sign-out) variants | No token / cookie only / mismatch / valid pair | `403 csrf_invalid` ×3, then `401` with session absent |
| E2E-01-16 | AC-12 | Recovery invitation URL | Landing renders | Provider choreography posts to `/api/auth/recovery/{token}/link/{provider}` |
| E2E-01-17 | AC-12 | Unknown/expired/reused token | GET + POST link | Both `/auth/error`; no session, no credential |
| E2E-01-18 | AC-13 | First-login handoff route | Rendered | Username chooser stub + continue CTA |
| E2E-01-19 | baseline | Unknown `/app/*` route | Rendered | SPA not-found page, back CTA |
| E2E-01-20 | AC-15, baseline 08 | App document | Headers read | CSP `default-src 'self'` + `form-action 'self' https:` (SSO redirect guard), nosniff, frame DENY, referrer, HSTS |

## Full-login suite — opt-in (mock IdP at `:9099`)

| ID | AC | Given | When | Then |
|---|---|---|---|---|
| E2E-01-21 | AC-02, AC-13 | Fresh sub at the mock IdP | Real login dance completed | Lands `/app/welcome/username`; `__Host-rb_session` HttpOnly/Secure/Lax; only csrf token JS-readable; `/api/v1/me` matches email |
| E2E-01-22 | AC-03 | Same sub, changed verified email | Second login | Same account id; email synced (collision-free) |
| E2E-01-23 | BR-04 | `callbackURL=https://evil.example` sent to sign-in | Dance completed | Server clamps redirect to `/app*`; browser never visits the attacker origin |
| E2E-01-24 | AC-06 | Logged-in session | Sign-out with CSRF pair | `204`; `__Host-rb_session` cleared; dashboard bounces to login |

## Authenticated suite — opt-in (seeded session, login skipped)

`beforeAll` runs the SSO dance once per run (primary session + a second session for the same identity + a foreign identity's session), saves the primary via Playwright storage state, and every case below is plain HTTP/UI on those sessions — no per-case login. Each run uses a fresh identity, so lists/counts are deterministic.

| ID | AC | Given | When | Then |
|---|---|---|---|---|
| E2E-01-25 | AC-03 | Seeded session | `GET /api/v1/me` | `200`; id/email match the seeded identity; `emailVerified` true; `contactEmail` null; `no-store` |
| E2E-01-26 | AC-05 | Seeded session | `GET /api/v1/me/providers` | Exactly one linked method (`generic-oidc`, "Dev SSO"); every entry has a boolean `linked` |
| E2E-01-27 | AC-06 | Seeded session, 2 known sessions | `GET /api/v1/me/sessions` | Both sids present, exactly one `current` (the cookie sid); frozen DTO key set (no `user_id`) |
| E2E-01-28 | AC-13 | Seeded session | Dashboard opened | Stays `/app/`; heading + identity render; no bounce, no password field |
| E2E-01-29 | AC-08 | Seeded session | `/app/account/settings` opened | MFE mounts: "Dev SSO / Linked", "Active sessions", "This device", revoke-all button enabled |
| E2E-01-30 | BR-03, NFR-06 | Seeded session | Contact code requested twice | First `202`; immediate resend `429 throttled` with `retryAfterSeconds` 1..60 |
| E2E-01-31 | baseline 05 | Seeded session | Bad email + malformed code POSTed | Both `422 validation_failed` (owner JSON body path after the Jackson fix) |
| E2E-01-32 | BR-03 | Code issued, wrong code given | Verify | `422 email_code_invalid`; `/me` still `contactVerified:false`, no contact email |
| E2E-01-33 | BR-05 | Single linked method | `POST /api/auth/unlink/generic-oidc` | `409 last_link_forbidden`; provider stays linked |
| E2E-01-34 | BR-05 | Method already linked | `POST /api/auth/link/generic-oidc` | `409 already_linked`; session intact |
| E2E-01-35 | BR-07 | Fresh seeded session | Step-up start + callback | `302` to mock authorize (state + S256 PKCE); callback returns to `returnTo`, issues no new session |
| E2E-01-36 | AC-06, AC-08 | 2 own + 1 foreign session | Revoke own other, then foreign sid | Own `204` (repeat `404`), revoked cookie `401`; foreign sid `404` with no oracle, foreign session untouched |
| E2E-01-37 | BR-06 | 2 sessions | `DELETE /api/v1/me/sessions` | `204`; other sessions dead (`401`); current survives |
| E2E-01-38 | S-6 | Live session | Mutation without `X-CSRF-Token` | `403 csrf_invalid`; session not ended |
| E2E-01-39 | AC-06 | Seeded session | Sign-out with CSRF pair | `204`; cookie cleared; old cookie `401`; dashboard bounces to `?notice=expired` |

## Integration-owned (not e2e; reasons)

| AC | Where | Why not e2e |
|---|---|---|
| AC-02 provider matrix, relay/denied email | `services/account-service/src/integrationTest` | Needs per-provider IdP fixtures + unique emails per run |
| AC-03 collision branch, AC-05 linking completion (two providers), AC-06 idle/absolute/revoke timings, AC-07 five other step-up actions, AC-08 DB/RLS assertions, AC-09 entitlements, AC-10 audit, AC-11 enumeration timing, AC-12 real invitation issue | integration tests + `deploy/drill-*.sh` | Clock control, DB/RLS assertions, second account, internal APIs. Link/unlink guards are e2e (E2E-01-33/34), one step-up action is e2e (E2E-01-35), owner-scoped revoke is e2e (E2E-01-36). |
| AC-14 degrade | `deploy/drill-*`, `deploy/probe-security.sh` | Requires stopping edge/account-service mid-flight |
| AC-15 prefill/XSS | integration tests | Needs hostile IdP profile payloads; CSP covered at edge level (E2E-01-20) |

## Runbook

```sh
cd e2e
npm test                      # default suite, headless
npm run test:headed           # visible browser, 400 ms slow-mo
npm run idp:on                # once: point Dev SSO at the mock IdP
npm run test:full             # default + full-login, headless
npm run test:headed:full      # those two, visible browser
npm run test:auth             # seeded-session suite, headless (own command)
npm run test:headed:auth      # seeded-session suite, visible browser
npm run idp:off               # restore placeholder IdP
npm run report                # html report; videos under test-results/
```

Notes: auth endpoints allow 20 ops/min per IP (`429 throttled` on breach). A default run is ~11 ops and a full-login run is ~9, so run those a minute apart; the seeded-session suite is ~9 ops and runs fine on its own (`test:full` excludes it for that reason). `idp:on` also ensures the dev internal-JWT keypair in `.env` (gateway signs, account-service verifies); without it no session can authorize `/api/v1/**`. The mock speaks http, and the edge CSP allows only https form redirects, so full-login tests navigate the server-issued authorize URL directly; the real form-POST leg is covered by E2E-01-07. The seeded-session suite uses a fresh identity per run, keeping session lists and the 5/hour contact-email cap deterministic.
