# Monetize Overview Backend Plan (registry + conformance harness + ops config — server build steps)

PLAN ref: P3-1/P3-2/P3-3 (cross-rail integration proof) + S-1…S-7 via the security baseline. BA source: `docs/monetize/overview/monetize-overview.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/overview/monetize-overview-database-plan.md` (registry, lifecycle, ownership — never redefined here). Buyer screens in the overview frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the cross-cutting money infrastructure: the provider registry in code, the ops config surfaces, the per-rail conformance harness that proves all eight rails behave identically, and the grep gates that keep defaults out. Follow steps 1–7 in order. Slice logic (dispatch, grant, ledger) lives in its owning backend plans — this file owns what spans all rails.

## Users

- Backend developer: executes steps 1–7 in package `com.app.billing.registry` + shared test harness.
- QA: runs the registry, no-default, end-to-end-per-rail, and baseline drills.
- Ops: flips eligibility/display-order/kill-switches without deploys, fully audited.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`billing` module; grant path isolated in its own package with a dedicated datasource role + kill switches, per `docs/platform/architecture/architecture-decision.md`). Monolith pros: order-create + grant share one transaction scope (no distributed commit on money); one RLS set; one audit log; one release train at 10k–100k concurrent. Microservices cons avoided: extracting billing would distribute the grant transaction (the exact hazard the architecture decision forbids without triggers) and double the audit/backup surface. Split needs ≥2 triggers; none fire in P3. Starters: web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. Load posture: dispatcher concurrency-safe (idempotency keys + fingerprint dedupe), grant handlers reentrant (replay-safe transitions), kill-switch propagation in seconds (config push + event, not deploy).
2. Step 1 — `ProviderRegistry.kt`: closed enum of eight codes + per-rail metadata (minor doc pointer, handoff kinds supported, VND-capable flag — Stripe flagged non-VND/international). Unknown code rejected at every entry point against this constant. Ninth rail = new enum value + minor + config rows + conformance run (checklist in step 5).
3. Step 2 — ops config surfaces: eligibility matrix (plan/product × provider) + display-order profile + kill switches, served from versioned config with an audited flip endpoint (`POST /api/v1/ops/billing/config`, `ROLE_OPS`, reason mandatory, audit row per flip). Reads cached short-TTL; flips effective within seconds (event-propagated, verified by the kill-switch drill). Absent display config → stable API order, client renders verbatim (no code fallback — grep-gated).
4. Step 3 — no-default enforcement: `provider` required at every create boundary (compile-time: no overload without provider; test-time: omitted/unknown matrix per intent type → 422 with eligible list); refund paths take no provider parameter (read stored); grep gate (`provider.*=.*payos|default.*provider|fallback.*order`, curated pattern list) runs in CI over checkout/refund/plan-purchase sources.
5. Step 4 — conformance harness `BillingConformanceTest` (the E3 gate that matters): parameterized over all eight rails × {quote=charged=receipt identity, double-submit single row, replay no-op grant, expiry/cancel races, tampered-amount no-grant, bad-signature 401, illegal-edge refusal, late-confirmation no-grant} — a rail ships only with its full row green. Sandbox-backed (public tunnel to dev service for webhooks, per rail runbook).
6. Step 5 — standardized money API shape (owned here, implemented by slices): `POST quote/checkout`, `GET payment-methods/orders`, problem JSON with stable `code` strings (`provider_required|unknown_provider|…`), `orderCode` numeric int64 in every money URL/payload (no sequential public ids), additive-only evolution with OpenAPI diff gate.
7. Step 6 — audit/evidence spine: every grant/transition/refund/dispute/flip/override appends `audit_log{actor, action, target, at}` with signatures/keys redacted; per-`orderCode` trail replayable for support (the trace contract finance and support rely on).
8. Step 7 — tests + container + rotation: conformance matrix green (recorded, not spot-checked); secrets grep clean (keys/certs/`whsec_`/tokens/PAN patterns across repo/logs/bundles); WAF/rate simulation (bursts throttled, valid traffic unaffected); quarterly rotation drill with P5 (no-downtime rotation, old credentials dead). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`; P3 exit gate: 100k concurrent normal-use mix.

## Data / API touches

- Schema: database plan above (no tables here; slices own DDL). Config: eligibility matrix + display profile + kill switches (versioned ops config, audited flips). Secrets: per-rail PSP keys via env/vault (names inventoried, values never). Metrics: `billing.conformance{rail,case}`, `billing.killswitch_flips`, grant/replay counters per rail.

## Acceptance criteria

- Registry drill: eight rails reachable as specified; ninth rail impossible without minor + entry (attempt returns 422 unknown).
- No-default drill: full intent-type matrix green; grep gate clean.
- Conformance drill: 8×N matrix all green before any rail change ships.
- Kill-switch drill: list exclusion within seconds; in-flight pendings routed with messaging; flip audited.
- Baseline drill: S-1…S-7 per the security baseline on all paths; rotation drill green.
- Container drill: digest pair boots, compose green, rollback green; P3 100k-mix gate green.

## Out of scope

- Registry/config contracts (database plan above); buyer screens (frontend plan); dispatch/session/grant/refund/ledger internals (their backend plans); rail handoff/verify mechanics (provider minors); shop/leads/P4/P5.

## Open questions

- Q1: Stripe recurring vs one-shot-only (see database plan Q1)?
- Q2: Display-order sourcing (see database plan Q2)?
- Q3: Partial refunds day one (see database plan Q3)?
