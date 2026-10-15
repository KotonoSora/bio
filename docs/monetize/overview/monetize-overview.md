# Monetize Overview (module map, no default provider)

PLAN ref: P3-1 (checkout → `orderCode` + provider handoff) + P3-2 (verified webhook grant → entitlements) + P3-3 (plan matrix + gates) + P3-4 (org skeleton). DEVIATION FLAGGED: PLAN §P3 and the VN-first guardrail name PayOS sandbox as the default path; per correction there is NO hardcoded default provider — every money flow carries an explicit `provider` across the eight specified rails below. PayOS is one equal member, not the fallback.

## Purpose

Give implementers, reviewers, and support one map of the monetize module: which providers exist, which shared minors own the cross-provider contracts, and the no-default rule that binds them. Provider minors own their rail mechanics; shared minors own the lifecycle every rail shares (selection, session, statuses, security, ledger, plans, refunds, receipts). No flow may assume a provider when none was chosen.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Buy plans/products and request refunds on any eligible rail | One consistent experience across rails |
| Implementer | Find the owning doc for any money question (selection vs session vs grant vs refund) | No guessing between minors |
| Support/ops/finance | Trace any order by `orderCode` across provider references, ledger rows, receipts, and audit trail | Uses one map of the monetize module |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Buy plans/products and request refunds on any eligible rail | I have one consistent experience | Must |
| US-02 | Implementer | Find the owning doc for any money question (selection vs session vs grant vs refund) | I do not guess | Must |
| US-03 | Support/ops/finance | Trace any order by `orderCode` across provider references, ledger rows, receipts, and audit trail | I use one map of the monetize module | Must |

## Functional requirements

1. Provider registry (closed, eight): `payos` (checkout-orders, link-lifecycle, webhook), `sepay` (checkout-orders, transaction-matching, webhook), `vnpay` (checkout-orders, ipn, query-refund), `zalopay` (checkout-orders, callback, query-refund), `napas` (checkout-orders, balance-sync, reconciliation), `googlepay` (`google-pay/checkout`, authorization, pending-refund), `applepay` (`apple-pay/checkout`, authorization, pending-refund), `stripe` (checkout-orders, webhook, pending-refund — non-VND/international where enabled; never a VND default). If a rail has no specified minor, checkout cannot offer it.
2. No-default contract (normative): `provider` is required on every checkout create, refund execution reads the order's stored `provider`, and display/listing order comes from ops config (API order verbatim when config is absent). Omitted/unknown codes → 422 with the eligible list. No silent fallback exists anywhere (grep-gated in `checkout/provider-selection.md` and `refunds/refund-processing.md`).
3. Shared-minor ownership: selection (`checkout/provider-selection.md`) owns the eligible set; session (`checkout/checkout-session.md`) owns quote/create/expire/return; dispatch (`payment/checkout-orchestration.md`) owns routing + kill switches; statuses (`payment/order-status-model.md`) own the canonical enum and legal edges; security (`payment/payment-security.md`) owns the cross-rail probe battery; ledger (`billing/entitlement-ledger.md`) owns plan truth; lifecycle (`billing/subscription-lifecycle.md`) owns periods/grace/downgrade; receipts (`billing/invoices-receipts.md` + `invoices/`) own evidence; catalog (`subscription/plan-catalog.md`) owns priced terms; trials/coupons (`subscription/trials-promotions.md`, `promo-code/`) own discounted entry; plans (`plans/subscription-plans.md`, `plans/plan-purchase-options.md`) own matrix + per-rail purchase; refunds (`refunds/refund-processing.md`) own reversal routing; sales/affiliate/advertising/user-behavior/data-analysis consume money events, never move money.
4. Money lifecycle (uniform): quote (server re-price, no side effects) → create (one `orders` row, numeric int64 `orderCode` UNIQUE, `amount_vnd INT`, immutable `provider`, idempotent) → provider handoff (`next: url|qr|sheet|token`) → verified confirmation (webhook/callback/IPN/query/sweep, amount-checked, idempotent grant) → ledger entitlement → receipt → lifecycle (renew/repurchase, grace, downgrade) → refund/dispute via originating rail only. Return pages poll and render; they transition nothing.
5. Data discipline: every business/ledger table carries `user_id TEXT NOT NULL` + index + RLS; dashboard scopes by session `user_id`; webhooks authenticate by provider scheme and resolve ownership from stored rows. All PG via `worker/db/*`, least-privilege app role; counters/throttles in KV `RATE_LIMIT`; secrets per rail via `wrangler secret` (never in repo/logs/bundles).

## Data / API touches

- Core tables: `orders` (intent + `provider` + canonical `status`), `subscriptions` ledger (plan truth), `refunds` ledger (reversals), `plans`/`plan_terms` catalog, coupons/credits ledgers, `audit_log` (append-only actor/action/target). No direct DB writes from clients or beacon-style hits; sweeps/jobs own expiry/reconciliation.
- Core endpoints: `POST /api/billing/quote`, `POST /api/billing/checkout` (explicit `provider`), `GET /api/billing/payment-methods`, `GET /api/billing/orders/:orderCode`, `POST /api/billing/orders/:orderCode/cancel`, refund request + ops approve/execute, `GET /api/v1/plan`, history/receipts reads. Member callbacks keep their minor URLs.
- Config/secrets: eligibility matrix + display-order profile + kill switches (config/KV, audited flips); per-member PSP keys in `wrangler secret` (`.dev.vars` locally; `.gitignore` guards `.wrangler/`, `.dev.vars*`); VND integers end-to-end (VNPay ×100 normalized at the edge).

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Registry drill: all eight member checkout paths reachable exactly as specified; no ninth rail offerable without a specified minor + registry entry | The eight-member registry as specified | Each member checkout path is reached and a ninth rail without a specified minor + registry entry is offered | The eight paths are reachable and the ninth rail is not offerable |
| AC-02 | No-default drill: omitted `provider` on plan/product checkout returns 422 with eligible list; unknown code returns 422; refund routing reads stored `provider` only; grep gate finds no hardcoded provider default or fallback ordering in checkout/refund/plan-purchase code | A plan/product checkout with omitted or unknown `provider` | Checkout is created and refund routing runs and the grep gate scans checkout/refund/plan-purchase code | Omitted/unknown codes return 422 with the eligible list, routing reads the stored `provider` only, and the grep gate finds no hardcoded default or fallback ordering |
| AC-03 | End-to-end drill per rail (sandbox): quote equals charged equals receipt amounts to the VND; single `orders` row on double-submit; verified grant exactly once; ledger visible within SLA; expiry/cancel races safe; full refund revokes per rule with re-grant refused | A sandbox order on any rail | Quote, double-submit, verified grant with replay, gate reads, expiry/cancel races, and full refund are exercised | Amounts match to the VND, a single `orders` row exists, grant applies exactly once, ledger is visible within SLA, races resolve safely, and refund revokes per rule with re-grant refused |
| AC-04 | Baseline probe: IDOR 403 cross-user with RLS deny at DB (S-1/S-6); error/quote/history rendering XSS-stripped with CSP holding (S-2); anonymous money endpoints Turnstile + WAF throttled (S-5); secrets grep clean with quarterly rotation per P5-4 (avatar upload N/A to money flows, owned by P1b-4) | Cross-user access, rendered error/quote/history, anonymous money endpoints, and repo/logs/bundles under secrets grep | IDOR, XSS, abuse, and secrets probes run | Cross-user access returns 403 with RLS deny (S-1/S-6), rendering is XSS-stripped with CSP holding (S-2), anonymous endpoints are Turnstile + WAF throttled (S-5), and secrets grep stays clean with quarterly rotation per P5-4 |

## Out of scope

- Member handoff/verify/sweep mechanics (each provider minor); dispatcher internals beyond the map (see `payment/checkout-orchestration.md`); transitions beyond the map (see `payment/order-status-model.md`); probe battery detail (see `payment/payment-security.md`).
- Ledger/lifecycle/receipts/catalog/trials plan-purchase/refund internals (`billing/`, `subscription/`, `plans/`, `refunds/`, `invoices/`, `promo-code/`); shop delivery, leads campaigns, P4 publish/automate/agency, P5 scale-out.

## Open questions

- Q1: Stripe-native recurring for plans — enable at launch (non-VND/international) or one-shot-only everywhere in v1?
- Q2: Display-order sourcing — static ops config vs experiment-driven order (same as checkout Q1, decided once here)?
- Q3: Partial refunds at launch — full-only with re-purchase, or partial ledger entries from day one (same as refunds Q1, decided once here)?
