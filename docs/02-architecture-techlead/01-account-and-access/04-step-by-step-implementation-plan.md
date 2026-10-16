# Account and Access - Step-by-Step Implementation Plan (feature 01)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/01-account-and-access/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | Register `account-service`: compose row (port 8081), `account_db` on PG 18.6, Redis db 7, edge db 2 key namespace reserved, image + health checks | Baseline `04-containers-and-compose.md` | Clean boot; G3 smoke includes the login redirect |
| T-02 | BE | Flyway `V1__account_core.sql` (users, linked_accounts, sessions, audit_log) + RLS policies + constraints/indexes | T-01 | G2 fresh replay clean; TL 02 verification drill 1-3 green |
| T-03 | BE | Spring Security OAuth2: four provider configs + generic OIDC adapter; Redis single-use `state`/`nonce`/PKCE; callback validation + generic failures | T-02 | AC-04 green |
| T-04 | BE | Find-or-create by `(provider_id, provider_sub)`; verified-email sync + conflict flag; contact-email code send/verify | T-03 | AC-03; BA 05 email rows green |
| T-05 | BE | Session issue (registry + edge key writer), revoke, revoke-all-others, logout, 50-session quota, expiry sweeper | T-04 | AC-06 green |
| T-06 | DevOps | Gateway integration: cookie validation against db 2, sliding TTL refresh, JWT mint with `auth_time`, CSRF double-submit, introspect fallback | T-05 | AC-14 green; G3 login smoke |
| T-07 | BE | Step-up endpoint + freshness enforcement on the six sensitive actions | T-06 | AC-07 green |
| T-08 | BE | Provider link/unlink + last-link guard + audit rows | T-07 | AC-05 green |
| T-09 | FE | Login, step-up dialog, providers list, conflict banner, onboarding handoff routes | T-03 | AC-02, AC-13 UI halves green |
| T-10 | BE | First-login handoff: consume `by-user` read (status cached 60 s); register the additive internal row in the feature 06 contract file | T-04 | AC-13 green |
| T-11 | BE | Entitlement snapshot API + fail-closed consumer tests (`<= 30 s` cache, miss -> free, `entitlement_snapshot_miss` counter); ledger source = feature 35's `GET /internal/v1/billing/plan/{userId}` | T-02 | AC-09 green on the free floor |
| T-12 | BE | Audit coverage (login/link/unlink/step_up/session_revoke/recovery) + `recovery-link` internal route + ops evidence reference | T-02 | AC-10, AC-12 green |
| T-13 | BE | Session settings endpoints (`me`, sessions list with masked fields) + `last_active_at` coalescing | T-05 | NFR-12 masking fields verified |
| T-14 | DevOps | Drill suite wired to gates: password-dead probe, abuse/enumeration, IDOR/RLS, provider-secret rotation, edge-degrade | T-08, T-09, T-12, T-13 | G5 probes + G7 spot-check green |

## Notes

- DEVIATION CARRIED: legacy Better Auth complicates nothing here - Spring Security OAuth2 client is the mandated stack; the legacy `password-security.md` timeouts (idle/absolute) resolve in BA 01 Q-07.
- T-11's forward half (ledger projection) is realized by feature 35 (`GET /internal/v1/billing/plan/{userId}`); the free floor remains the fail-closed path, never a stub.
- T-10's profile-service row is additive-only per G4; the canonical contract row lives in `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`.
- Grep gate: no `password` field, route, or column may appear; the probe suite (T-14) fails the release on any hit (S-1).
