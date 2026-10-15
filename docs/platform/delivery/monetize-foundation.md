# Monetize Foundation (execution plan M0 — shared kernel)

PLAN ref: P3-1 (checkout → `orderCode` + handoff) + P3-2 (verified grant → entitlements) + Security checklist S-1…S-7. BA source: `docs/monetize/overview/monetize-overview.md`, `docs/monetize/payment/order-status-model.md`, `docs/monetize/payment/checkout-orchestration.md`, `docs/monetize/payment/payment-security.md`, `docs/monetize/checkout/checkout-session.md`, `docs/monetize/checkout/provider-selection.md`, `docs/monetize/billing/entitlement-ledger.md`. DEVIATION CARRIED: no hardcoded default provider (explicit `provider` on every create); Spring Boot service on containerized PG replaces Workers/Hono + Neon default per `docs/platform/architecture/architecture-decision.md`.

## Purpose

Build the provider-agnostic money kernel once, before any PSP rail is wired: one `orders` model, one status machine, one checkout front door, one selection contract, one security baseline, one entitlement ledger. Every rail in `docs/platform/delivery/monetize-rails.md` and every post-grant flow in `docs/platform/delivery/monetize-lifecycle.md` plugs into this kernel without redefining it. M0 exits with a container image that quotes, creates, expires, cancels, and polls orders end-to-end against a stubbed provider adapter.

## Users

- Backend developers: implement the `billing` module against frozen contracts.
- QA / release: verify M0 with contract + probe drills before any real PSP key exists.
- Ops: operate kill switches, eligibility config, and audit trail from day one.

## Functional requirements

1. Stack and topology (mandatory): PostgreSQL LTS `18.6`, Spring Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1` wrapper-only. Modular monolith: `billing` Gradle module inside the single deployable per `docs/platform/architecture/architecture-decision.md`. Architecture verdict for M0 — monolith, not microservices. Pros for monolith here: single transaction scope for order-create and grant (no distributed commit), one RLS policy set, one audit log, one release train at 10k–100k concurrent. Cons accepted: single blast radius on deploy (mitigated by kill switches + backward-compatible Flyway migrations + previous-tag rollback). Split requires ≥2 triggers from the architecture decision (none fire in M0); recorded here so a later extract is a move, not a rewrite.
2. Domain kernel: Flyway `V3x__billing_kernel.sql` creates `orders{user_id TEXT NOT NULL + index + RLS, provider TEXT NOT NULL, plan?, product_id?, amount_vnd INT, status canonical enum, orderCode BIGINT UNIQUE, idempotency_key UNIQUE, buyer_email?, expires_at TIMESTAMPTZ}` and `subscriptions{id, user_id TEXT NOT NULL + index + RLS, plan free|pro, status active|grace|cancelled|expired, period_start, period_end, provider, order_id?, cancelled_at?}` with partial unique index on one current row per user plus free-from-signup hook. Canonical statuses `pending|authorizing|paid|failed|cancelled|expired|refunded|disputed|chargeback_reversed` with only the legal edges from `docs/monetize/payment/order-status-model.md`; illegal edges rejected in code + covered by matrix test. `provider` immutable after insert.
3. Checkout front door: `POST /api/billing/quote` (side-effect-free, server re-price, optional `provider` for single-rail preview), `POST /api/billing/checkout` (explicit `provider` required; omitted/unknown → 422 with eligible list; `Idempotency-Key` + fingerprint dedupe → one row), `GET /api/billing/payment-methods` (eligible set = registry ∩ plan/product matrix ∩ kill-switch survivors ∩ capability filter; ops-config display order, API order verbatim when config absent), `GET /api/billing/orders/:orderCode` (canonical `{status, status_detail}`, no native codes), `POST /api/billing/orders/:orderCode/cancel` (owner-scoped, `pending|authorizing → cancelled` once). Return pages poll only; forged `?status=paid` transitions nothing. Expiry sweep flips lapsed `pending|authorizing → expired` idempotently; late money against terminal states rejected + logged per the late-event rule.
4. Security baseline (from `docs/monetize/payment/payment-security.md`): session `user_id` scoping AND RLS on every money endpoint; server re-price + amount re-check on every grant path (VND integers; VNPay ×100 normalized at edge); UNIQUE anti-replay keys; secrets via environment only (never repo/logs/bundle); Turnstile + per-IP/email rate limits on quote/checkout/cancel; `audit_log{actor, action, target, at}` on every transition and kill-switch flip; CSP `default-src 'self'` on checkout/pending/history.
5. Container per release: multi-stage build (`Gradle 9.8.1` → Eclipse Temurin JRE, non-root), OCI image tagged `:<semver>-<sha>` runnable via both `docker run` and `podman run` against empty PG `18.6` (Flyway migrates on boot) per `docs/platform/containers/container-lifecycle.md` and gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schemas: `billing` schema owns `orders`, `subscriptions`, `audit_log` (append-only). Least-privilege app role at runtime vs migration role at boot; statement timeout 5s; Hikari pool. No cross-schema writes except the billing grant transaction.
- Endpoints: `/api/billing/*` (money) + `/api/v1/plan` (ledger read) versioned additive-only; OpenAPI generated per build with diff gate. Stub provider adapter implements the `next{kind: url|qr|sheet|token, payload}` handoff shape so M1 rails conform without contract change.
- Config: eligibility matrix + display-order profile + per-provider kill switches in config/KV (audited flips); `.env.example` documents every variable; health via `/actuator/health/liveness|readiness`.

## Acceptance criteria

- No-default drill: omitted `provider` → 422 with eligible list on plan and product intents; unknown code → 422; grep gate finds no hardcoded provider default or fallback ordering.
- Double-submit drill: same key/fingerprint → single row + same handoff; key reuse across intents → 422; keyless double-click → single row.
- Status drill: every legal edge fires, every illegal edge refused, late confirmation on `cancelled`/`expired` grants nothing; full per-order audit trail replayable.
- Ledger drill: sequential paid plan grants → exactly one current row; product-only order → zero ledger writes; new signup → `free/active` row; concurrent grant race → single current row.
- Security drill: S-1/S-6 IDOR 403 + RLS deny as app role; S-2 XSS strip + CSP; S-5 throttle on anonymous money endpoints; secrets grep clean.
- Container drill: same digest boots under Docker AND Podman, compose cold boot green, previous-tag rollback green, OpenAPI diff clean.

## Out of scope

- Real PSP adapters, signatures, sweeps (see `docs/platform/delivery/monetize-rails.md`); renewals, grace, downgrade, receipts, coupons, refunds, plan matrix detail (see `docs/platform/delivery/monetize-lifecycle.md`); shop delivery, P4/P5 scale-out.

## Open questions

- Q1: Pending TTL — one global value or per-provider config carried by M1 (BA open, default global in M0)?
- Q2: Keyless fingerprint dedupe window length (60–120s proposed)?
