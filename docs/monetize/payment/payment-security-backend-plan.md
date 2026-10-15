# Payment Security Backend Plan (guards + abuse + probe battery — server build steps)

PLAN ref: S-1…S-7 enforcement on every money path + incident response + quarterly rotation. BA source: `docs/monetize/payment/payment-security.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Inventory in `docs/monetize/payment/payment-security-database-plan.md` (enforcement points, key contract, battery matrix — never redefined here). Secure page patterns in the security frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the shared security baseline: owner-scoping guards, webhook authentication, amount/replay/terminal guards, secrets hygiene, tiered abuse controls, audit emission, incident response, and the per-provider probe battery as a deploy gate. Follow steps 1–8 in order. Rail slices call these guards; none reimplements them.

## Users

- Backend developer: executes steps 1–8 in package `com.app.billing.security` (shared guards + battery runner).
- Rail adapter developers: authenticate webhooks per scheme, then delegate to guards.
- QA/auditor: runs the battery per provider every public deploy; red blocks promotion.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith shared guards (in-process calls from every money path). Monolith pros: one scoping helper, one replay guard, one audit emitter (no per-rail security drift — the drift this baseline exists to kill); counters in the shared cache layer. Microservices cons avoided: a security service would add a network hop to every money call for checks that need row-local context. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: guards are constant-time checks (indexed scoping, cached buckets); webhook auth is HMAC-compare cheap; abuse buckets shed load before PG.
2. Step 1 — `MoneyScope.requireOwner(userId, rowUserId)`: app-level ownership assertion used by every money endpoint (RLS remains the second layer — either denies alone). Webhook paths skip session identity entirely and authenticate by provider scheme first (HMAC/signature/token per rail adapter, constant-time compare, timestamp-skew window, unknown-ref → 401 without touching orders).
3. Step 2 — amount guard: re-price checkouts from catalogs (client amounts ignored + logged on divergence); grant paths re-check provider amount vs stored `amount_vnd` (mismatch → no-grant + `billing.amount_mismatch` alert). VNPay ×100 normalized at the edge adapter (one place, tested).
4. Step 3 — replay/terminal guard: anti-replay UNIQUE key check (database plan rule 3) + terminal fast-path via the status-model guard (replayed paid → success-without-effect; cancel/expiry races resolve to one terminal outcome). Double-submit collapse owned by the session slice's idempotency; this guard covers provider-side redelivery.
5. Step 4 — secrets hygiene enforcement: build-time grep (patterns from the database plan) over repo + bundles; runtime secret access only via env/vault handles (never string-concatenated into logs — log-capture tests assert redaction of signatures/keys); no PSP key, private key, or token in any client bundle (bundle-content test per release).
6. Step 5 — abuse controls: Turnstile verification on checkout/token-intake/public money endpoints (S-5); per-IP/email token buckets in the cache layer (cards tier tighter); WAF managed + rate rules from day one; velocity alerts feed the ops dashboard; quarterly rotation job for all money secrets (no-downtime: dual-read window, old credentials dead after — P5 drill shared).
7. Step 6 — audit emission: `AuditPort.emit(actor, action, target)` called by every grant/transition/refund/dispute/flip/override (append-only; redacted payloads); per-order trail query for support.
8. Step 7 — incident response runbook (code + procedure): `rotateSecret(rail)` → `killSwitch(rail, reason)` → `sweepReconcile(rail)` → `reEnable(rail)` — executed in this order, each step audited; quarterly drill rotates one rail end-to-end on staging.
9. Step 8 — probe battery runner `BillingProbeBattery` (deploy gate): per-rail execution of the database plan's matrix (IDOR, bad-sig/tamper/unknown-ref, replay, double-submit, cancel/expiry races, kill-switch, secrets grep, k6 gate reference) with recorded results attached to the release (numbers, not pass/fail); red anywhere blocks promotion by tooling.

## Data / API touches

- Schema: database plan above (no tables here; guards run over slices' rows + `audit_log` + cache buckets). No new endpoints (guards live inside money flows; ops flip/rotate endpoints owned by overview/orchestration slices). Metrics: `billing.amount_mismatch`, `webhook.auth_fail{rail,reason}`, `abuse.throttled{endpoint,tier}`, battery results per release.

## Acceptance criteria

- Battery drill: eight-rail matrix green, recorded per deploy; any red blocks promotion (tooling proof, not policy text).
- Secrets drill: repo/logs/bundles grep clean; redaction tests green; rotation drill no-downtime.
- WAF drill: bursts throttled per-IP/email; valid traffic unaffected; card tier tighter (verified ratios).
- Audit drill: sampled money actions all carry actor/action/target with redacted secrets.
- Incident drill: quarterly rotate→kill→reconcile→enable run green on staging.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Enforcement inventory (database plan above); page patterns (frontend plan); rail webhook mechanics (rail slices); dispatcher/kill-switch operation (orchestration plans); status edges (status-model plans); ledger/lifecycle/plans; P4/P5 except shared rotation.

## Open questions

- Q1: Thresholds now vs tuned (see database plan Q1)?
- Q2: Radar/device signals at launch vs first incident (see database plan Q2)?
