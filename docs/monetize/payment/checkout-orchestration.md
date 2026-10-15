# Checkout Orchestration (unified multi-provider checkout)

PLAN ref: P3-1 (unifies the eight `monetize/*` provider checkout docs behind one dispatcher). DEVIATION FLAGGED: PLAN P3-1 and the VN-first guardrail name PayOS sandbox as the default path; per product correction there is NO hardcoded default provider — `provider` is always explicit and listing/display order is ops config with no code fallback. PayOS is one of eight equal rails. Provider mechanics live in their own minors; entitlements in `billing/entitlement-ledger.md`. Full registry in `checkout/provider-selection.md`.

## Purpose

Define the single checkout front door all buyers use regardless of rail: one dispatcher endpoint, one provider-selection contract, one pending-order UX. Each provider minor specifies its own URL/QR/token mechanics, but none defines how buyers choose between them, what happens when a provider is down, or what the shared request/response shape is — this doc does, so adding a ninth provider is config plus a provider module, never a checkout rewrite.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Pick a plan/product, see eligible payment methods, pay, land on pending/return experience | One checkout front door regardless of rail; defined order |
| Frontend checkout page | Call one endpoint, render one provider list, poll one status shape | Supports one consistent pending/return experience |
| Support/ops | Disable a degraded provider in one place | Avoids redeploying |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Pick a plan/product once, see eligible payment methods in a defined order, pay, and land on one consistent pending/return experience | I use one checkout front door regardless of rail | Must |
| US-02 | Frontend checkout page | Call one endpoint, render one provider list, and poll one status shape | Buyers get one consistent pending/return experience | Must |
| US-03 | Support/ops | Disable a degraded provider in one place | I avoid redeploying | Must |

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

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Eight-provider matrix drill: each plan/product x provider cell allowed or rejected per config, verified via API (client-side filtering alone is a failure) | Plan/product x provider config | Each cell is verified via API | Allowed cells pass and rejected cells fail with client-side filtering alone treated as failure |
| AC-02 | Kill-switch drill: disabling a provider mid-incident removes it from the list within seconds; in-flight pendings route to that provider's settlement flow with buyer messaging intact | A provider disabled mid-incident | The list is read and in-flight pendings settle | The provider is removed from the list within seconds and pendings route to that provider's settlement flow with buyer messaging intact |
| AC-03 | Double-submit drill on every provider handoff kind: single `orders` row, same handoff re-issued | A double-submit on any provider handoff kind | The checkout is submitted twice | A single `orders` row exists with the same handoff re-issued |
| AC-04 | Return/cancel URLs from all redirect providers converge on the one pending page with correct status rendering | Return/cancel URLs from all redirect providers | The buyer returns or cancels | All land on the one pending page with correct status rendering |
| AC-05 | IDOR probe: user A operating on user B orders returns 403; RLS denies cross-user rows | User A operating on user B orders | The operation is attempted | The API returns 403 and RLS denies cross-user rows |
| AC-06 | No-default drill: omitted `provider` on create returns 422 with the eligible list (never a silent PayOS fallback); grep gate finds no hardcoded provider default or fallback ordering in checkout code | A create with omitted `provider` | Checkout is created and the grep gate scans checkout code | The API returns 422 with the eligible list, never a silent PayOS fallback, and the grep gate finds no hardcoded default or fallback ordering |
| AC-07 | Provider-addition dry run (review-only): a ninth provider fits the dispatcher + `next` shape with no contract changes | The dispatcher + `next` shape | A ninth provider is reviewed against the contract | It fits with no contract changes |

## Out of scope

- Provider URL/QR/token mechanics and verification (their own minors); canonical statuses and transitions (see `payment/order-status-model.md`); shared security rules (see `payment/payment-security.md`).
- Entitlement ledger and lifecycle (`billing/`); plan matrix (`plans/`); receipts (`billing/invoices-receipts.md`); P4/P5.

## Open questions

- Q1: Display-order sourcing — ops-configured order vs experiment-driven order, with what behavior when config is empty (proposed: render API-returned order verbatim, no code default)?
- Q2: Guest product checkout in v1 — enable with magic-link history, or login-required everywhere?
