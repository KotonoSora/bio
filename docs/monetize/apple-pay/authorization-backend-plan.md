# Apple Pay Authorization Backend Plan (validate + charge + grant — server build steps)

PLAN ref: P3-2 adapted (server-side merchant validation, PSP-confirmed charge, atomic grant, webhook confirmation path) + S-1/S-3/S-6/S-7. BA source: `docs/monetize/apple-pay/authorization.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/apple-pay/authorization-database-plan.md` (sessions, keys, binding, audit — never redefined here). Sheet/verdict UX in the authorization frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for trusting Apple Pay money: server-fetched single-use merchant sessions, owner/status/amount/freshness-checked token intake, serialized PSP charge with idempotency, atomic grant, and the verified webhook path. Follow steps 1–8 in order. Forged tokens, replays, wrong amounts, and paid-races can never create a double grant or a free upgrade.

## Users

- Backend developer: executes steps 1–8 in package `com.app.billing.rail.applepay.auth`.
- QA: runs validation-failure, replay, tamper, timeout, and burst drills (Apple + PSP sandboxes).
- PSP/Apple servers: counterparties with scheme-verified, mutually untrusting interfaces.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith rail auth (in-process with order + ledger writes). Monolith pros: intake-checks + charge + grant + audit in one transaction scope (no half-granted upgrades); row-locked serialization per order. Microservices cons avoided: splitting auth from the grant writer opens the double-grant window between charge success and entitlement write. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: per-order serialization (one charge in flight — concurrent authorizes queue on the row lock, bounded wait, never parallel charges); PSP client timeouts + circuit-breaker (slow PSP degrades to sweep-recoverable pendings, not thread exhaustion); webhook endpoint throttled before PG (WAF + rate, valid traffic unaffected).
2. Step 1 — `POST /api/billing/applepay/validate-merchant {orderCode, validationURL}` (authenticated, order-owner scoped): call Apple with the Merchant Identity Certificate (TLS client identity), passing Apple's `validationURL` and merchant/session details untouched → return Apple's opaque session → client `completeMerchantValidation`. Per-transaction sessions (fresh per sheet — reuse rejected); fast or fail-closed (slow validation fails before payment UI confusion, never degrades into client-side session requests).
3. Step 2 — charge execution (inside `POST /api/billing/applepay/authorize`): intake checks (owner + `pending` + amount binding + identifier freshness — any failure rejects BEFORE any PSP call, cheapest-first ordering) → mark `authorizing` (row-locked, serialized) → PSP adapter `charge(token, amount_vnd, orderCode, idempotency_key = orderCode)` → PSP-confirmed capture → atomic `authorizing → paid` + `psp_txn_id` + entitlement + audit in ONE transaction → response verdict feeds `completePayment(SUCCESS|FAILURE)`.
4. Step 3 — idempotency everywhere: PSP-side key (safe charge retry) + local `psp_txn_id` UNIQUE + guarded terminal transitions (status-model guard) + terminal fast-path (paid replays succeed-without-effect). Same token twice, retried requests, redelivered webhooks: single transition, single entitlement, single audit entry.
5. Step 4 — PSP webhook handler (public, no session, HTTPS only): verify per PSP scheme FIRST (signature/HMAC — bare POSTs never trusted; unknown refs logged-rejected without leak) → terminal confirmations through the guarded transition (same atomicity as charge path) → non-terminal events (review/pending) routed to settlement states (pending-refund slice, never granted here).
6. Step 5 — failure mapping: PSP-declined/failed → order `failed` + `completePayment(FAILURE)` with buyer-safe reason (gateway codes never leak — reason allowlist, not passthrough); transport/timeout → back to `pending` for sweep resolution (ambiguity preserved as pending, resolved by query — never guessed).
7. Step 6 — deploy hygiene: validation + webhook URLs and verified domains re-confirmed per environment after every deploy/cert renewal touching them; TEST-vs-production credentials strictly separated (config review asserts separation); local reproduction via public tunnel to dev service against PSP + Apple sandboxes.
8. Step 7 — observability: redacted structured logs per step (orderCode, psp_txn_id, result, latency); alerts on consecutive validation failures, verification failures, already-paid spikes; per-step timers (validation vs charge vs webhook paths separable in dashboards).
9. Step 8 — tests + container: sandbox end-to-end (verdict matches grant outcome); validation-failure test (fails closed pre-charge); replay test (token×2 + redelivered webhook → single everything); tamper tests (forged/malformed token, altered amount, unknown orderCode — failures without grant/leak); timeout test (sweep recovery, exactly-once preserved); burst test (bogus webhooks throttled, valid unaffected). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no tables here; transitions over `orders` + entitlement rows + `audit_log`). Endpoints: `POST /api/billing/applepay/validate-merchant`, `POST /api/billing/applepay/authorize` (verdicts), PSP webhook receiver (verified-first). PSP client: `charge/status/refund/webhook-verify` adapter shape (shared with Google Pay — one family, decided jointly). Metrics per step 7.

## Acceptance criteria

- E2E drill: sandbox chain to `paid` + entitlement within SLA; verdicts match outcomes.
- Failure drill: validation failures fail closed with zero charge attempts.
- Replay drill: single transition/entitlement/audit across token×2 + redelivery; cross-order identifier reuse rejected.
- Tamper drill: forgeries/mismatches/unknowns fail without grant or leak.
- Timeout drill: sweep recovery with exactly-once intact.
- Burst drill: throttling holds, valid traffic clean.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Evidence contracts (database plan above); sheet/verdict UX (frontend plan); session config/gating/intake (checkout plans); pending/sweep/refund/dispute (pending-refund plans); `orders`/entitlement DDL; plan matrix/AI/agency; shop delivery; P4/P5.

## Open questions

- Q1: Paid-on-charge vs confirming-until-webhook (see database plan Q1)?
- Q2: Cert storage/rotation mechanics timing (see database plan Q2)?
