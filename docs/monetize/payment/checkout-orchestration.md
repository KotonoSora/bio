# Checkout Orchestration (unified multi-provider checkout)

PLAN ref: P3-1 (unifies the eight `monetize/*` provider checkout docs behind one dispatcher). DEVIATION FLAGGED: PLAN P3-1 and the VN-first guardrail name PayOS sandbox as the default path; per product correction there is NO hardcoded default provider — `provider` is always explicit and listing/display order is ops config with no code fallback. PayOS is one of eight equal rails. Provider mechanics live in their own minors; entitlements in `billing/entitlement-ledger.md`. Full registry in `checkout/provider-selection.md`.

## Purpose

Define the single checkout front door all buyers use regardless of rail: one dispatcher endpoint, one provider-selection contract, one pending-order UX. Each provider minor specifies its own URL/QR/token mechanics, but none defines how buyers choose between them, what happens when a provider is down, or what the shared request/response shape is — this doc does, so adding a ninth provider is config plus a provider module, never a checkout rewrite.

## Users

- Buyer: picks a plan/product once, sees eligible payment methods in a defined order, pays, and lands on one consistent pending/return experience.
- Frontend checkout page: calls one endpoint, renders one provider list, polls one status shape.
- Support/ops: disables a degraded provider in one place instead of redeploying.

## Functional requirements

1. Dispatcher `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider, plan? | product_id?, buyer_email?, provider_options?}`. Server validates provider eligibility (plan/product × provider matrix — e.g. international cards via Stripe only where enabled), re-prices from canonical tables (client amounts never trusted), creates the single `orders` row with `provider` set, then delegates to that provider's builder. Response shape is uniform: `{orderCode, amount_vnd, expires_at, provider, next: {kind: url|qr|sheet|token, payload}}` where `next` carries the provider-specific handoff (payment URL, QR image/link, sheet config, or client secret).
2. Provider ordering and availability: no hardcoded default provider and no hardcoded display order. The eligible set is derived per request from the provider registry (`checkout/provider-selection.md`: payos, sepay, vnpay, zalopay, napas, googlepay, applepay, stripe) intersected with plan/product × provider eligibility config and per-provider kill switches. Display order comes from ops config (or request-time sort input); when config is absent the API returns a stable order and the client renders the returned order verbatim — never a code-level PayOS-first fallback. Omitted `provider` on create → 422 requiring explicit choice (quote previews all eligible without creating). Ineligible providers (e.g. wallet buttons on incapable devices, card rails where disabled) are filtered server-side — the client never decides eligibility.
3. Degradation switch: per-provider kill switch (config + audited ops endpoint) removes a provider from the list and fails its in-flight `pending` orders into the settlement path of that provider's minor — never silently, always with buyer-visible messaging.
4. Pending-order UX contract (shared by all providers): after handoff, the buyer lands on `/app/billing/pending/:orderCode`, which polls `GET /api/billing/orders/:orderCode` and renders `pending | confirming | paid | failed | expired` from the canonical status model (`payment/order-status-model.md`). Provider return/cancel redirects land here too — no provider owns its own return page.
5. One order, one provider: `provider` is immutable after creation; switching methods mid-order means cancel-then-new-checkout (keeps every provider's idempotency story intact). Double-submit while pending re-issues the same handoff, never a second row.
6. Guest policy (decides the recurring checkout Q2 across providers): v1 requires login for plans; products allow buyer-email guest checkout only if explicitly enabled — then history access is magic-link based (see `billing/invoices-receipts.md` Q2).

## Data / API touches

- Reads/writes `orders` (creation + `provider` stamp); reads plan/product catalogs, provider config, kill switches. All PG via `worker/db/*`; dashboard calls scope `WHERE user_id = session.userId`.
- Endpoints: `POST /api/billing/checkout`, `GET /api/billing/payment-methods` (eligible list with display order + capability hints for the client), `GET /api/billing/orders/:orderCode`, `POST /api/billing/orders/:orderCode/cancel`. Provider callbacks/webhooks keep their own URLs in their minors.
- Kill-switch state in config/KV (fast) mirrored to audit log on every flip; provider health signals (consecutive webhook failures, sweep deltas) feed the ops dashboard that informs flips.

## Acceptance criteria

- Eight-provider matrix drill: each plan/product × provider cell allowed or rejected per config, verified via API (client-side filtering alone is a failure).
- Kill-switch drill: disabling a provider mid-incident removes it from the list within seconds; in-flight pendings route to that provider's settlement flow with buyer messaging intact.
- Double-submit drill on every provider handoff kind: single `orders` row, same handoff re-issued.
- Return/cancel URLs from all redirect providers converge on the one pending page with correct status rendering.
- IDOR probe: user A operating on user B orders returns 403; RLS denies cross-user rows.
- No-default drill: omitted `provider` on create → 422 with the eligible list (never a silent PayOS fallback); grep gate finds no hardcoded provider default or fallback ordering in checkout code.
- Provider-addition dry run (review-only): a ninth provider fits the dispatcher + `next` shape with no contract changes.

## Out of scope

- Provider URL/QR/token mechanics and verification (their own minors); canonical statuses and transitions (see `payment/order-status-model.md`); shared security rules (see `payment/payment-security.md`).
- Entitlement ledger and lifecycle (`billing/`); plan matrix (`plans/`); receipts (`billing/invoices-receipts.md`); P4/P5.

## Open questions

- Q1: Display-order sourcing — ops-configured order vs experiment-driven order, with what behavior when config is empty (proposed: render API-returned order verbatim, no code default)?
- Q2: Guest product checkout in v1 — enable with magic-link history, or login-required everywhere?
