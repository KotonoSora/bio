# Entitlement Ledger Backend Plan (grant writes + plan reads — server build steps)

PLAN ref: P3-3 (grant → ledger → gates; free-from-signup; product orders bypass) + S-1/S-6/S-7. BA source: `docs/monetize/billing/entitlement-ledger.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Schema in `docs/monetize/billing/entitlement-ledger-database-plan.md` (single table, written once). Period movement in the lifecycle plans; plan display in the ledger frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for plan truth: the ledger writer every rail grant calls, the single-current-row guarantee under concurrency, the read path every gate shares, and the free-from-signup hook. Follow steps 1–7 in order. Nothing else is plan truth — gates reading `orders`, cookies, or client claims are bugs, grep-verifiable as such.

## Users

- Backend developer: executes steps 1–7 in package `com.app.billing.ledger`.
- Rail adapter developers: call `LedgerWriter.grantPaidPlan` post-transition (never write `subscriptions` directly — direct writes bypass single-current enforcement).
- Gated-feature code: calls `PlanResolver.effectivePlan` and gets one answer.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith ledger writer (in-transaction with the rail grant). Monolith pros: grant transition + ledger close/open + audit commit atomically (a paid order with no ledger row is the "I paid but I'm free" ticket wearing a race condition); partial-unique enforced at the DB, not hoped in code. Microservices cons avoided: splitting ledger from grant opens the paid-without-plan window on every slow network. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: data-jpa, validation, security, cache, actuator, micrometer. Load posture: grant writes are event-paced (trivial); plan reads are owner-indexed single-row (hot gates optionally KV-cached short TTL, PG reconciled on dispute).
2. Step 1 — schema: run the ledger database plan script once (after `orders`). No other DDL in this slice.
3. Step 2 — `service/LedgerWriter.kt#grantPaidPlan(userId, plan, term, provider, orderCode)` `@Transactional`: lock current row (`SELECT … FOR UPDATE` by the partial-unique scope) → close it (`cancelled` + `cancelled_at` + audit reason `superseded-by-<orderCode>`, or insert directly when none) → open new row (`period_start = grant time`, `period_end` per term math owned by the lifecycle slice's term table — this writer takes bounds as parameters, never computes terms) → audit (`actor: "system"`) → publish `PlanChanged` event (gates with KV fast-path invalidate on it). Product-only orders (`product_id` set, no `plan`) NEVER call this writer (call-site assertion in review — plan truth untouched by product purchases, tested).
4. Step 3 — concurrency guarantee: the partial unique makes double-grant atomicity structural (second concurrent grant fails closed → retries against history → converges to one current row + full history). Double-webhook + sweep racing grants produce a single audit chain (tested, not reasoned).
5. Step 4 — `service/PlanResolver.kt#effectivePlan(userId)`: read single current row (`active`, or `grace` within window) → `{plan, status, period_end, quotas}`; NO row (signup-hook failure) → loud 500 + `plan.missing_row` alert (fail-closed, never silent free-pass). `GET /api/v1/plan` serves this (owner-scoped). KV fast-path optional (short TTL, invalidated on `PlanChanged`, PG reconciled on dispute — cache never cited in billing arguments).
6. Step 5 — signup hook: user creation inserts `free/active` (null `period_end`) in the same signup unit of work (hook test asserts every new user holds the row — gates never branch on absence).
7. Step 6 — AI-credit keying hook: `ai_generations` computation (plans slice) reads the current row's period window via `PlanResolver` (period source owned here, credit math owned there — one direction, no cycle).
8. Step 7 — tests + container: sequence test (two paid grants → one current + full history + plan-visibility within SLA across all gates: domains, realtime, templates, AI); product-bypass test (zero writes, reads unchanged); free-hook test; concurrency test (racing grants → single current + single chain); IDOR test (cross-user 403 + RLS deny); `./gradlew build` green; same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (writer + lifecycle job are the only writers). Endpoints: `GET /api/v1/plan` → `200 {plan, status, period_end, quotas}`. No `PUT /api/v1/plan` exists (plan changes happen only through paid grants, lifecycle transitions, audited ops — absence asserted). Events: `PlanChanged` (cache invalidation). Metrics: `plan.missing_row`, grant visibility lag per gate.

## Acceptance criteria

- Sequence drill: two grants → one current, history complete, all gates reflect within SLA (no provider-specific lag — rail-agnostic proof).
- Bypass drill: product purchases write zero rows.
- Free drill: signup hook green for every new user; missing-row path alerts loudly.
- Concurrency drill: racing grants converge, single chain.
- IDOR drill: cross-user 403 + RLS deny.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Table DDL (database plan above); plan screens (frontend plan); periods/renewal/grace/downgrade (lifecycle plans); receipts/history (invoices-receipts plans); rail grant mechanics (rail slices); plan matrix/AI caps (plans slices); agency seats; P4/P5.

## Open questions

- Q1: Terms monthly-only vs +yearly (see database plan Q1)?
- Q2: Org-owned rows later (see database plan Q2)?
