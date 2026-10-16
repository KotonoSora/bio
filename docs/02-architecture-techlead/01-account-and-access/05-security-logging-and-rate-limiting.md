# Account and Access - Security, Logging, and Rate Limiting (feature 01)

Phase 2 TL · BA sources: `docs/01-requirements-ba/01-account-and-access/` (BR-04, BR-07, BR-08, BR-11, BR-12, BR-13). Baseline envelope: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | SSO-only, hard: no password field/route/column anywhere; probes return `404 auth_disabled`; any legacy verifier ignored and flagged | Grep gate + runtime probe (T-14) fail the release on any hit |
| S-2 | Callback integrity: single-use `state`/`nonce`/PKCE (10 min), same-origin `callbackURL` allowlist, server-side code exchange | AC-04 replay/mismatch/expiry suite |
| S-3 | No enumeration: byte-equivalent wording, timing class p95 diff `<= 50 ms`; provider errors never pass through | AC-11 analysis; interleaved probe pairs |
| S-4 | Step-up freshness: services enforce `now - auth_time <= 600 s` from the JWT only; the six actions never execute on a stale grant | AC-07; forged/aged claim tests |
| S-5 | Deny-by-default: `user_id` server-set; app scoping + RLS; `403` mutations / `404` private reads; no v1 bypass | AC-08 IDOR/RLS drill |
| S-6 | Session integrity: cookie flags + CSRF; registry truth; edge key deleted on revoke; revocation `<= 60 s` | AC-06, AC-14 drills |
| S-7 | Entitlement fail-closed: consumer cache `<= 30 s`, miss -> free, `entitlement_snapshot_miss` counted, never a silent widen | AC-09; outage drill |
| S-8 | Audit completeness: append-only rows for login/link/unlink/step_up/session_revoke/recovery; app role has no UPDATE/DELETE | AC-10; grant probe |
| S-9 | Recovery safety: single-use token 15 min, ops-triggered with evidence reference, no credential ever set or shown | AC-12; replay + expiry suite |
| S-10 | Secrets: client secrets + signing keys from vault only; dual-config provider rotation without deploy; JWKS dual-key window | Quarterly rotation drill; repo grep gate |
| S-11 | Prefill/XSS: IdP name/avatar stripped; CSP holds on login/step-up/onboarding; avatar prefill consumes feature 09 rules (2 MB, magic-byte, random key) | AC-15 drill |

## Rate envelopes (gateway enforces; service backstops on counters)

| Route class | Envelope | On breach |
|---|---|---|
| `/api/auth/sign-in`, `/api/auth/callback` | 20 req/min/IP (baseline auth class); per-provider exponential backoff | `429 throttled` + `Retry-After`; challenged on sustained signals |
| `/api/auth/step-up`, link/unlink | 10 req/min/user | `429`; no silent degradation |
| Owner API reads (`/api/v1/me*`) | 60 req/min/user | `429` |
| Contact-email send / verify | 5/hour/user; resend after 60 s | `429`; verify attempts capped at 5 per code |
| `/internal/v1/*` | service-level budget, trace-attributed | `429` + trace; caller circuit-breaker |

Probing emits stable codes and is counted, not punished. No auth response reveals account existence, provider detail, or session counts.

## Degrade matrix

| Condition | Behavior |
|---|---|
| IdP outage | Login/step-up/link fail generically with retry guidance; existing sessions unaffected (edge keys) |
| Edge Redis (db 2) unavailable | Introspect fallback keeps validating sessions; if account-service is also unreachable, owner routes fail closed (`401`/`503`) - no fail-open path |
| Account-service unavailable | Existing sessions validate at the edge; new login/step-up/link down; entitlement consumers clamp to free |
| Mail provider down | Verification codes/notices not sent; contact email never auto-verified; UI states retry |
| Feature 35 ledger unavailable | Snapshot resolves free floor (fail closed); `entitlement_snapshot_miss` rise is alertable |
| Signing key rotation mid-flight | Dual-key window; old key verifies until expiry, new key signs; zero-downtime drill |
| Edge counter store loss | Requests pass with alert; service-side backstops (Redis db 7 counters) still hold per baseline 08 |

## Logging and data handling

- Log fields: route, status, timing, trace id, `user_id` (internal only), provider, `code`; never emails, names, tokens, cookie values, or raw IPs.
- `ip_class` and `device_label` are the only device/network facts stored (hashed class, coarse UA family) per NFR-12.
- Alertable counters: `auth_failed`, `step_up_required`, `entitlement_snapshot_miss`, `edge_introspect_fallback`, `throttled`, session sweeper backlog, recovery invocations.

## Gates

- G4: OpenAPI additive-only; the code catalog above is rename-frozen.
- G5: S-1, S-2, S-5, S-6, S-8 probes run per release; S-10 rotation is quarterly (standing drill).
- G7: drill results from T-14 attach to the tag; deviation log reconciled (baseline microservices + database-per-service carried).
