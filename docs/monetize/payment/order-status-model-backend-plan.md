# Order Status Model Backend Plan (transition guard — server build steps)

PLAN ref: P3-1/P3-2 (guarded transitions on every rail path) + S-1/S-3/S-6 (webhook auth + replay safety + RLS). BA source: `docs/monetize/payment/order-status-model.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/payment/order-status-model-database-plan.md` (enum, edges, authority — never redefined here). Buyer rendering in the status-model frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the transition guard: the edge table, atomic amount-checked transitions, actor authority enforcement, late-event rejection, idempotent replay, and the per-provider conformance matrix. Follow steps 1–7 in order. No rail, sweep, or ops flow transitions an order except through this guard — direct status writes are the double-spend bug wearing convenient code.

## Users

- Backend developer: executes steps 1–7 in package `com.app.billing.status`.
- Rail adapter developers: call the guard; map native states per the database plan's rule 5.
- QA: runs the edge-matrix, late-event, and replay drills per rail.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith guard (single transaction scope with the calling grant/sweep/ops write). Monolith pros: transition + grant + audit commit atomically (no half-granted orders); one edge table; one audit chain. Microservices cons avoided: a status service would split the grant transaction across a network call (the double-grant window). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: data-jpa, validation, security, actuator, micrometer. Load posture: guard calls are row-locked single-row writes (pessimistic lock on the order row, short-held); replays hit the terminal-state fast path (no lock, success-without-effect).
2. Step 1 — `service/TransitionGuard.kt`: `transition(orderId, to, actor, eventId)` = lock row → assert edge in `ALLOWED[from]` (illegal → `IllegalTransition` exception, counted, never partially applied) → assert actor authorized for the edge (grant/sweep/ops/cancel identities per database plan rule 3; buyer identity authorized for nothing) → amount re-check on grant edges (provider-reported vs stored `amount_vnd`; mismatch → no-grant + alert) → apply + audit (from→to + event id) in the SAME transaction as the caller. Terminal-state replays return success-without-effect before locking (idempotent retry storms collapse here, not in rail code).
3. Step 2 — late-event enforcement: confirmations for `cancelled`/`expired` orders → reject + log (`late.confirmation` metric). Exception re-link exists only as an audited ops flow on bank rails (service method behind `ROLE_OPS` + reason, never an automatic path).
4. Step 3 — replay safety: UNIQUE anti-replay key (`orderCode` + provider transaction/event id, storage owned by the checkout-session database plan) + terminal fast-path (rule: retried paid orders succeed-without-effect; error-retry storms are a caller bug the guard does not reward).
5. Step 4 — native-state mapping conformance: per-rail mapping tables (rail slices) tested against the guard matrix — every legal edge per mapping fires, every illegal edge refuses, non-terminal native states transition nothing. Matrix test is required green before any rail-minor change ships (CI gate on the rail's path).
6. Step 5 — buyer projection: read endpoints expose `{status, status_detail}` only (canonical + human string; owned by the checkout-session read path; this guard guarantees the canonical half).
7. Step 6 — observability: `transition.applied{from,to,rail}`, `transition.rejected{reason}`, `transition.late` counters; per-order trail query for support (orderCode → audit chain).
8. Step 7 — tests + container: edge-matrix test (full cross-product × 8 rail mappings); late-event test (zero grants, all logged); replay test (double webhook + sweep race → single grant, single audit chain); concurrency test (cancel racing confirmation → exactly one terminal outcome, no resurrection); `./gradlew build` green; same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no tables here; guard runs inside callers' transactions over `orders` + `audit_log`). No new endpoints (transitions ride grant/sweep/cancel/ops flows; buyer reads project `{status, status_detail}`). Metrics: `transition.applied/rejected/late`.

## Acceptance criteria

- Matrix drill: legal edges fire, illegal edges refuse, on every rail mapping.
- Late drill: zero grants on terminal orders across all rails, all logged.
- Replay drill: double webhook + sweep race → single grant + single chain.
- Race drill: cancel-vs-confirm → one terminal outcome, no resurrection.
- Audit drill: per-order trails complete with event ids.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Enum/edge/authority contracts (database plan above); buyer rendering (frontend plan); grant/verify/sweep mechanics (rail + session slices); dispatcher/selection; probe battery (security plans); ledger/periods; P4/P5.

## Open questions

- Q1: Dispute-won period math (see database plan Q1)?
- Q2: Partial-refund status (see database plan Q2)?
