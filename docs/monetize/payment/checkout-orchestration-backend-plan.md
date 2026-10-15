# Checkout Orchestration Backend Plan (dispatcher — server build steps)

PLAN ref: P3-1 (dispatcher, eligibility, kill switches, one-order-one-provider, pending-UX contract) + S-1/S-5/S-6/S-7. BA source: `docs/monetize/payment/checkout-orchestration.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/payment/checkout-orchestration-database-plan.md` (reads, idempotency, immutability — never redefined here). Checkout screens in the orchestration frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the single checkout front door: eligibility-gated dispatch, server re-pricing, idempotent creation, uniform handoff shape, kill-switch operation, and the shared pending contract. Follow steps 1–8 in order. Provider minors own their URL/QR/token mechanics; none defines choosing, degradation, or the shared shape — this dispatcher does.

## Users

- Backend developer: executes steps 1–8 in package `com.app.billing.dispatch`.
- QA: runs the matrix, kill-switch, double-submit, return-convergence, IDOR, and no-default drills.
- Ops: disables degraded rails in one audited place instead of redeploying.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith dispatcher (in-process delegation to rail builders). Monolith pros: eligibility check + re-price + create + delegate execute in one request scope; kill-switch reads are local config; one idempotency story. Microservices cons avoided: a checkout service would split create-from-grant (the money seam) and duplicate the no-default enforcement. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: dispatcher is CPU-bound validation + one insert (horizontally stateless); Turnstile + per-IP/email buckets on quote/checkout (S-5); idempotency lookups indexed (no full scans under double-submit storms).
2. Step 1 — `POST /api/billing/checkout {provider, plan?|product_id?, buyer_email?, coupon_code?, returnUrl?, cancelUrl?}` + `Idempotency-Key`: authenticate (plans require login v1; guest products only if enabled) → Turnstile + rate check → validate eligibility (registry ∩ matrix config ∩ kill-switch survivors ∩ capability filter — ALL server-side; the client never decides) → re-price from canonical catalog − validated coupon (client amounts ignored; quote/charge read the same catalog version in the intent scope) → idempotency resolution (same key+fingerprint → re-issue stored handoff; cross-intent key reuse → 422; keyless → fingerprint window dedupe) → create one `orders` row (`provider` immutable, `orderCode` int64 unique, `status=pending`) → delegate to the rail builder → uniform response `{orderCode, amount_vnd, expires_at, provider, next:{kind,payload}}`. Omitted `provider` → 422 with eligible list (never silent fallback — the grep gate from the overview plan covers this code).
3. Step 2 — `GET /api/billing/payment-methods`: eligible list + API order + capability hints per member (`{provider, display_name, handoff_kind, disabled_reason?}`); wallet members filtered by capability server-side. This endpoint (not client logic) is why incapable devices never see wallet buttons.
4. Step 3 — kill-switch operation: `POST /api/v1/ops/billing/config` flips (`ROLE_OPS`, reason, audit) → list exclusion within seconds (event-propagated) → in-flight pendings of the killed rail follow its settlement path with buyer messaging (owned per rail minor). Health signals (consecutive webhook failures, sweep deltas) feed the ops dashboard informing flips.
5. Step 4 — pending-order contract: post-handoff buyers land on `/app/billing/pending/:orderCode`, polling `GET /api/billing/orders/:orderCode` (canonical `{status, status_detail}` only). Provider return/cancel redirects converge here (no rail owns a return page). Return params transition nothing (forgery-proof by design — no code path reads them as state).
6. Step 5 — one order, one provider: no update path for `provider` (creation-only field); cancel-then-new-checkout is the only switch (documented in the cancel response's `next_action`, so clients guide instead of guessing).
7. Step 6 — same-origin return URLs: `returnUrl`/`cancelUrl` must live under `/app/billing/{return,cancel}` carrying only `orderCode`; off-origin rejected at build time (422), never at redirect time (open-redirect prevention belongs at creation, not at use).
8. Step 7 — observability: `checkout.created{provider}`, `checkout.idempotent_hit`, `checkout.rejected{reason}`, kill-switch flip audit; per-rail handoff latency (delegation cost visible per member).
9. Step 8 — tests + container: matrix test (every plan/product × provider cell via API); kill-switch test (exclusion timing + in-flight routing + audit); double-submit test (all three key scenarios); return-convergence test (all redirect rails land correctly); IDOR test (cross-user 403 + RLS deny); no-default test (omitted → 422 + eligible list on every intent); ninth-rail dry run (review-only: fits dispatcher + `next` shape unchanged). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no tables here; creation executes the session slice's insert path). Endpoints: `POST /api/billing/checkout`, `GET /api/billing/payment-methods`, `GET /api/billing/orders/:orderCode`, `POST /api/billing/orders/:orderCode/cancel` (cancel mechanics owned by the session slice; listed here as the switch path). Provider callbacks keep member-minor URLs. Metrics per step 7.

## Acceptance criteria

- Matrix drill: all cells correct via API; client filtering alone fails review.
- Kill-switch drill: seconds-level exclusion, routed in-flights, audit present.
- Double-submit drill: one row + same handoff; cross-intent reuse 422; keyless collapse.
- Return drill: all rails converge with correct canonical rendering; forged params inert.
- IDOR drill: cross-user 403 + RLS deny.
- No-default drill: 422 + eligible list everywhere; grep gate clean.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Read/idempotency contracts (database plan above); checkout screens (frontend plan); quote/create/expire/return internals (session plans); rail mechanics (provider minors); status edges (status-model plans); probe battery (security plans); ledger/lifecycle/receipts/catalog/coupons/refunds; P4/P5.

## Open questions

- Q1: Display-order sourcing (single overview decision)?
- Q2: Guest enablement (single session decision)?
