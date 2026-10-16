# Account and Access - Architecture and Tech Stack (feature 01)

Phase 2 TL · Service: `account-service` (new registration), database `account_db` (PostgreSQL 18.6), own Redis db 7; edge session keys in platform Redis db 2. Implements `docs/01-requirements-ba/01-account-and-access/`. Pins: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Identity, sessions, entitlements, deletion orchestration, tier reads, and concierge data live in one service: `account-service` (features 01-03, 05). Feature 04 extends `profile-service` (namespace authority) instead. | Avoids an auth-service split with per-request round-trips: session validation stays at the edge; everything stateful sits behind one identity boundary. |
| D-2 | Dual session state: durable registry in `account_db` (list/revoke/audit truth) + edge validation key in Redis db 2 (`edge:session:{sid}`), read-only for the gateway; miss path rebuilds via introspect. | Server-side revocability without per-request service calls; cache loss self-heals instead of mass logout. |
| D-3 | Step-up is JWT `auth_time` freshness (`≤ 10 min`), refreshed by an SSO re-dance; no grants table. | One primitive for all six sensitive actions; nothing to leak or clean up. |
| D-4 | `state`/`nonce`/PKCE live as single-use Redis db 7 keys; no verification table. | Loss only forces a login retry; no durable secret residue. |
| D-5 | Entitlement snapshot is an internal API (`free`\|`pro` + capability map, fail-closed to free, consumers cache `≤ 30 s`). Values come from the feature 35 ledger projection (`GET /internal/v1/billing/plan/{userId}`); the ledger materializes the free floor structurally - free is a resolved value, never an invented default. | One contract for 06, 09, 13-16, 04, 45-47; replaces the 300 s placeholder sketch in `docs/02-architecture-techlead/09-themes-and-avatar/01-architecture-and-tech-stack.md`. |
| D-6 | Gateway mints the internal JWT (claims incl. `auth_time`); services never read cookies or session stores. | Matches baseline 08; step-up and identity checks are local. |
| D-7 | Org role matrix and the deliverable-email signal are fixed here; org data tables land with features 45-46; the checkout gate lands with features 32-40. | Consumers get one source; no mechanics are stubbed early. |

## Registration and boundaries

DEVIATION CARRIED: the legacy account-access execution plan chose a monolith `account` module over an auth service; the mandated microservices baseline (`docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md`) supersedes that verdict. `account-service` is registered there and in `MIGRATION.md` in this change. Extraction doctrine re-scored: no trigger pair fires for a further split inside this feature (validation is edge-resident; login volume scales with edge traffic, not 10x beyond it); the A0 round-trip concern is honored by D-2's edge key. DEVIATION CARRIED (from the same source): Spring Security + server-side sessions replace the legacy Better Auth defaults; no password surface exists.

The service owns: provider registry + callback handling, account rows, link rows, sessions, step-up, entitlement snapshot, contact email, recovery invitations, audit rows. It does not own: usernames/profile content (feature 06), plan values/ledger (features 32-40), org tables (features 45-46), publishing tokens (features 41-44), deletion purge runner (feature 02, executed through this service).

## Stack and build slice

Spring Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1` (wrapper-only), JVM 21. Spring Security OAuth2 client for the four providers + generic OIDC adapter (config-only additions); resource-server validation for internal service JWTs; springdoc OpenAPI (additive gate); Flyway-on-boot; Micrometer/OTel; Jackson JSON.

- Compose slice: `account-service` host port 8081 (dev), `account_db` on the shared PG 18.6, Redis db 7; health `/health/liveness` + `/health/readiness` (readiness fails on DB loss; Redis loss degrades to introspect-only, never fails closed for existing sessions).
- No new platform containers: gateway, SPA, and Redis are existing baseline rows; Redis db 2 gains the edge session keys (contract in `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`).

## Edge and React integration

- Gateway routes `/api/auth/*` and `/api/v1/me*` (session class) to `account-service`; validates the `rb_session` cookie against `edge:session:{sid}`; refreshes the sliding TTL; on miss calls introspect; mints JWTs with `sub`, `aud`, `iat`, `exp` (60 s), `jti`, `auth_time`; CSRF double-submit on mutations per baseline 08.
- SPA: `/login` (provider buttons only), step-up dialog, `/welcome/username` handoff target (feature 06 screen), `/app/account/settings` (sessions, providers, contact email, notices). No token is ever JS-readable; all auth calls are same-origin cookie fetches.

## Consumers

| Consumer | Contract consumed |
|---|---|
| Feature 06 | First-login handoff check (`by-user` internal read); consumed by name in `docs/01-requirements-ba/06-public-profile/01-document-context-and-scope.md` |
| Features 09, 13-16 | Entitlement snapshot (fail-closed pro gate) |
| Feature 04 | Entitlement snapshot for the custom-domain gate |
| Features 32-40 | Deliverable-email signal + step-up enforcement on billing actions |
| Features 45-47 | Role matrix, step-up, audit fields for Act-as-user |
