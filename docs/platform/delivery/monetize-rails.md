# Monetize Rails (execution plan M1 — eight provider adapters)

PLAN ref: P3-1 (provider handoff) + P3-2 (HMAC verify → idempotent grant) + S-1…S-7 per-provider probe battery. BA source: `docs/monetize/overview/monetize-overview.md` (registry of eight), `docs/monetize/payos/checkout-orders.md`, `docs/monetize/payos/webhook.md`, `docs/monetize/payos/link-lifecycle.md`, plus the `sepay`, `vnpay`, `zalopay`, `napas`, `google-pay`, `apple-pay`, `stripe` minors (checkout + verify + settlement each). Builds on `docs/platform/delivery/monetize-foundation.md`; post-grant effects in `docs/platform/delivery/monetize-lifecycle.md`. DEVIATION CARRIED: explicit `provider` everywhere, PayOS one equal rail, display order ops-configured.

## Purpose

Wire the eight specified PSP rails onto the M0 kernel one wave at a time without touching the kernel contract: each rail contributes a build adapter, a verify/grant handler, and a settlement sweep behind the uniform `next` shape and canonical status map. M1 exits with sandbox end-to-end (checkout → pay → verified grant → entitlement) green on all eight rails from one container image.

## Users

- Backend developers: implement one adapter per rail behind the frozen port interface.
- QA / finance: drive sandbox money per rail and reconcile references.
- Ops: kill-switch any rail without redeploy.

## Functional requirements

1. Stack and topology (mandatory): same pins as M0 — PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for M1 — still monolith: eight adapter plugins inside the `billing` module behind one `PaymentProviderPort{buildHandoff, verifyCallback, queryStatus, executeRefund, sweepPending}` interface. Monolith pros here: adapters share the order/ledger transaction and RLS/audit baseline (no cross-service auth or distributed grant), one probe battery, one release train. Microservices cons avoided: eight deployables would multiply secret handling, webhook URLs, and reconciliation jobs with no ≥10x scaling asymmetry between rails at P3 gates. Split trigger recorded: if a single rail's webhook/sweep volume sustains backlog past the queue-consumer signal, that rail's sweep alone extracts first — adapters already isolate it.
2. Wave sequence (each wave independently shippable, kernel untouched): Wave A — `payos` (payment-link URL + HMAC-SHA256 webhook + link-lifecycle cancel/query/sweep), `sepay` (bank-transfer/QR + transaction-matching + exception re-link), `vnpay` (gateway URL + IPN verify + query-refund; ×100 normalized at edge). Wave B — `zalopay` (gateway order + callback + query-refund), `napas` (dynamic VietQR + balance-sync + reconciliation). Wave C — `googlepay` (`google-pay/checkout` tokenized sheet + authorization + pending-refund via underlying PSP), `applepay` (same pattern), `stripe` (PaymentIntent + webhook + pending-refund; non-VND/international where enabled, never a VND default). Per-rail TTL, return/cancel URLs (same-origin `/app/billing/pending/:orderCode`), and native→canonical status mapping table live in the adapter, reviewed against its BA minor.
3. Verify-then-grant (uniform): authenticate by the rail's scheme (HMAC/signature/token) with constant-time compare; resolve ownership from stored rows; amount-check provider-reported vs stored `amount_vnd` (mismatch → no grant + alert); fire the atomic `pending|authorizing → paid|failed` transition exactly once (UNIQUE `orderCode` + provider event id as anti-replay key; replay = 200 no-op); then write the entitlement/product claim and append `audit_log`. Transient PSP/DB errors → 5xx for provider retry; persistent validation failures → 4xx no-retry. Return pages poll; they transition nothing.
4. Settlement and degradation: per-rail sweep/query job reconciles stuck `pending|authorizing` (expiry, provider-side cancels mirrored in, late-event rejections logged); per-rail kill switch removes the rail from `GET /api/billing/payment-methods` within seconds and routes in-flight pendings to that rail's settlement path with buyer messaging; all flips audited. Secrets per rail via environment (sandbox keys in M1; rotation runbook exercised).
5. Container per wave: each wave cuts a tag runnable via Docker AND Podman per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md` (contract diff additive-only, sandbox grant drill per rail in the wave).

## Data / API touches

- Tables: M0 `orders`/`subscriptions` only, plus per-rail PSP reference columns or `provider_events{orderCode, provider_event_id UNIQUE, payload_ref, at}` for replay keys; raw payloads age out per rail minor while receipts persist. No new money-truth tables.
- Endpoints: kernel endpoints unchanged; member callbacks keep their BA-minor URLs (e.g. PayOS webhook, VNPay IPN, ZaloPay callback, Stripe webhook) — all authenticate by provider scheme, never session. Ops: audited kill-switch flip per rail.
- Sandbox: per-rail sandbox credentials + `webhooks/confirm`-equivalent runbook (public tunnel for local reproduction) exercised per wave; PSP references (`vnp_TransactionNo`, `zp_trans_id`, `stripe_pi_id`, rail txn ids) recorded on the order for finance traceability.

## Acceptance criteria

- Per-rail sandbox drill (all eight): quote = charged = receipt amounts to the VND; checkout → pay → verified grant → entitlement visible within SLA; replay is a no-op single grant; tamper (bad sig, altered amount, unknown ref) rejected without grant or leakage.
- Matrix drill: each plan/product × provider cell allowed/rejected per config via API on all eight paths; incapable device excludes wallet rails server-side.
- Kill-switch drill per rail: disable → delisted within seconds with audited flip + buyer messaging; in-flight pendings settle via that rail's path.
- No-default drill: no rail reachable when ineligible; grep gate still clean of hardcoded provider default/fallback.
- Security battery per rail (S-1…S-7): IDOR 403 + RLS deny, XSS/CSP on rail-touched views, Turnstile + WAF throttle, secrets grep clean, per-order audit complete.
- Container drill per wave tag: Docker + Podman run to ready, digest pair recorded, rollback demonstrated.

## Out of scope

- Kernel contracts (see `docs/platform/delivery/monetize-foundation.md`); renewals, receipts, coupons, refunds routing, plan gates (see `docs/platform/delivery/monetize-lifecycle.md`); ninth rail (registry path in `docs/monetize/checkout/provider-selection.md`, needs its own specified minor first).

## Open questions

- Q1: Sandbox-to-prod credential cutover — per-rail staggered or all-eight flag day (propose staggered A→B→C)?
- Q2: Stripe scope in v1 — one-shot only everywhere, or native recurring where non-VND (decide before Wave C)?
