# Checkout Session (quote, create, expire, return)

PLAN ref: P3-1 (checkout → numeric `orderCode` + provider handoff, VND integers, same-origin return/cancel) + P0-3 (WAF + Turnstile + rate rules). DEVIATION FLAGGED: PLAN P3-1 names a PayOS link as the checkout outcome; per correction there is no default provider — the outcome is the explicitly selected provider's handoff (URL/QR/sheet/token), with PayOS as one of eight equal options. Dispatcher/selection in `payment/checkout-orchestration.md`; registry in `checkout/provider-selection.md`; statuses in `payment/order-status-model.md`; security baseline in `payment/payment-security.md`. Provider handoff mechanics stay in their own minors — this doc owns the buyer-facing session lifecycle they all share.

## Purpose

Define the single buyer journey from price intent to a tracked pending order: preview an exact quote, create exactly one order per intent, expire it if unpaid, cancel it if abandoned, and return the buyer to one pending page that polls but never grants. Provider docs each restate fragments of this today; this doc is the arbiter so double-submit, expiry, and return behavior are identical on every rail.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Preview the exact charge, pay once, return to pending/success/failure state | Double-submit, expiry, and return behavior identical on every rail |
| Frontend checkout | Quote without creating rows, create idempotently, poll one status shape | Follows the single buyer journey from price intent to tracked pending order |
| Support/ops | Look up an order by `orderCode`, cancel stuck pendings | Explains expiry without ambiguity |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Preview the exact charge, pay once, and return to a clear pending/success/failure state | Double-submit, expiry, and return behavior are identical on every rail | Must |
| US-02 | Frontend checkout | Quote without creating rows, create idempotently, and poll one status shape | I follow the single buyer journey from price intent to a tracked pending order | Must |
| US-03 | Support/ops | Look up an order by `orderCode` and cancel stuck pendings | I explain expiry without ambiguity | Must |

- Attacker (adversarial): double-submits, tampers amounts, forges return URLs, races cancel against grant — and gains nothing.

## Functional requirements

1. Quote before create: `POST /api/billing/quote {plan? | product_id?, provider?, coupon_code?}` returns `{amount_vnd, provider, eligible_methods[]}` with zero side effects. Pricing recomputed server-side from `plan_terms` / `products` minus validated coupon (see `subscription/plan-catalog.md`, `subscription/trials-promotions.md`); client amounts ignored. Quote and later charge read the same catalog version in the same intent scope — displayed price and charged price cannot diverge.
2. Create (idempotent): `POST /api/billing/checkout` (authenticated for plans; Turnstile + per-IP/email rate limits) with body `{provider, plan? | product_id?, buyer_email?, coupon_code?, returnUrl?, cancelUrl?}` plus `Idempotency-Key` header. Server validates eligibility (plan/product × provider matrix from orchestration config), re-prices, then creates one `orders` row (`orderCode` numeric int64 unique, `amount_vnd INT`, `status=pending`, `provider` immutable). Same key + same fingerprint re-issues the same handoff; same key + different fingerprint is rejected 422 (prevents key reuse across intents). Double-click without a key still collapses via fingerprint dedupe within a short window (see Q2).
3. Validation (fail closed): unknown plan/product, ineligible provider, invalid/expired coupon, malformed `buyer_email`, or off-allowlist return/cancel URLs → 422 with a generic code (no catalog internals leaked). `returnUrl`/`cancelUrl` must be same-origin under `/app/billing/{return,cancel}` carrying only `orderCode`; off-origin rejected at build, never at redirect time.
4. Expiry: every pending order carries `expires_at` from per-provider config (each provider minor owns its TTL; no global PayOS-derived default). Expiry sweeps flip `pending|authorizing → expired` once; expired orders render the expired UX and accept only cancel-then-new-checkout. Late money against `expired|cancelled` follows the uniform late-event rule (rejected + logged, never auto-granted; audited exception re-link only on rail providers).
5. Cancel: `POST /api/billing/orders/:orderCode/cancel` (owner-scoped) moves `pending|authorizing → cancelled` once; provider-side cancels are mirrored in by that provider's lifecycle handler. Cancel-then-new-checkout is the only way to switch providers mid-intent. Grant racing cancel loses safely: the atomic amount-checked transition refuses to leave a terminal state (see status-model actor authority).
6. Return handling: provider return/cancel redirects land on `/app/billing/pending/:orderCode`, which polls `GET /api/billing/orders/:orderCode` and renders the canonical status (`pending|authorizing|paid|failed|cancelled|expired`, plus post-paid `refunded|disputed` states on history). Return pages and query params transition nothing — a forged `?status=paid` return without a verified provider confirmation changes no row.
7. Guest policy (consumed, not re-decided): plans require login in v1; products allow `buyer_email` guest checkout only if the orchestration Q2 enables it, with history via the magic-link path in `billing/invoices-receipts.md`. Guest rows still carry `user_id` (claim-bound owner) + index + RLS — no null-owner orders anywhere.

## Data / API touches

- Table `orders{user_id TEXT NOT NULL + index + RLS, provider, product_id?, plan?, plan_term_version?, amount_vnd INT, status, orderCode UNIQUE numeric int64, idempotency_key?, buyer_email?, expires_at}`. All PG via `worker/db/*`, least-privilege app role; dashboard reads scope `WHERE user_id = session.userId`. `provider` immutable after insert; `status` constrained to the canonical enum with the legal-edge guard.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/quote`, `POST /api/billing/checkout`, `GET /api/billing/payment-methods` (eligibility list, owned by orchestration), `GET /api/billing/orders/:orderCode` (returns canonical `{status, status_detail}` only), `POST /api/billing/orders/:orderCode/cancel`. Webhooks/callbacks/IPN keep their provider URLs; sweeps run as scheduled jobs.
- Rate/abuse state in KV `RATE_LIMIT` (per-IP/email buckets on quote/checkout/cancel); kill-switch/config server-side (never client-decided). Secrets (per-provider PSP keys — PayOS, SePay, VNPay, ZaloPay, NAPAS, Google Pay, Apple Pay, Stripe — each via `wrangler secret`) / `.dev.vars` locally — never committed, logged, or bundled; `.gitignore` guards `.wrangler/`, `.dev.vars*`.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | No-default drill: omitted `provider` on create returns 422 with eligible list (never a silent provider fallback); quote with no `provider` previews all eligible without creating | A create with omitted `provider` and a quote with no `provider` | Create and quote run | Create returns 422 with the eligible list and quote previews all eligible without creating |
| AC-02 | Quote-charged identity: quote amount equals created `amount_vnd` to the VND across plan/product x provider x coupon cells; client amount tamper re-priced server-side and logged | A quote across plan/product x provider x coupon cells | The order is created including with tampered client amounts | Quote amount equals created `amount_vnd` to the VND with tamper re-priced server-side and logged |
| AC-03 | Double-submit drill (per provider handoff kind): same key/fingerprint yields single row + same handoff; key reuse across intents returns 422; keyless double-click yields single row | A double-submit per provider handoff kind | The same key/fingerprint is reused, a key is reused across intents, or a keyless double-click occurs | The same key/fingerprint yields a single row with the same handoff, key reuse across intents returns 422, and keyless double-click yields a single row |
| AC-04 | Validation drill: ineligible provider, bad coupon, malformed email, off-origin return/cancel returns 422 with no order row and no catalog leakage | An ineligible provider, bad coupon, malformed email, or off-origin return/cancel | Checkout validation runs | The API returns 422 with no order row and no catalog leakage |
| AC-05 | Expiry drill: unpaid order flips to `expired` once (sweep rerun is a no-op); expired UX offers new checkout only; late confirmation against `expired`/`cancelled` grants nothing and logs | An unpaid pending order | The expiry sweep runs including reruns and late confirmations arrive | The order flips to `expired` once, expired UX offers new checkout only, and late confirmation against `expired`/`cancelled` grants nothing and logs |
| AC-06 | Cancel-race drill: cancel concurrent with provider confirmation resolves to exactly one terminal outcome per the status-model edges; no resurrection out of terminal states | Cancel concurrent with provider confirmation | Both race | Exactly one terminal outcome per the status-model edges results with no resurrection out of terminal states |
| AC-07 | Return drill: forged success params with no provider confirmation change nothing; all redirect providers converge on the one pending page with correct canonical rendering | Forged success params with no provider confirmation | Return handling runs | Nothing changes and all redirect providers converge on the one pending page with correct canonical rendering |
| AC-08 | IDOR probe (S-1/S-6): user A quoting/creating/reading/cancelling user B orders returns 403; RLS denies cross-user rows at DB; webhook-style calls authenticate by provider scheme, never by session | User A quoting/creating/reading/cancelling user B orders | The operation is attempted | The API returns 403, RLS denies cross-user rows at DB, and webhook-style calls authenticate by provider scheme |
| AC-09 | XSS/CSP + abuse + secrets: quote/pending/error rendering strips script/iframe/event-handlers with CSP holding (S-2); rapid quote/checkout bursts throttled via Turnstile + WAF (S-5); secrets grep clean with rotation per P5-4 (avatar upload N/A to this flow, covered in P1b-4) | Rendered quote/pending/error output and rapid quote/checkout bursts plus repo/logs/bundles under secrets grep | XSS, abuse, and secrets probes run | Rendering strips script/iframe/event-handlers with CSP holding (S-2), bursts are throttled via Turnstile + WAF (S-5), and secrets grep stays clean with rotation per P5-4 |

## Out of scope

- Dispatcher ordering, eligibility matrix, kill-switch operation (see `payment/checkout-orchestration.md`); canonical transitions and native-state mapping (see `payment/order-status-model.md`); shared probe battery (see `payment/payment-security.md`).
- Provider link/QR/sheet/token build and verification (their own minors); grant → entitlement writes (`billing/entitlement-ledger.md`); periods/renewal/grace/downgrade (`billing/subscription-lifecycle.md`); receipts/history (`billing/invoices-receipts.md`); catalog versioning (`subscription/plan-catalog.md`); coupon/credit rules (`subscription/trials-promotions.md`, `promo-code/`); P4/P5.

## Open questions

- Q1: Pending TTL per rail (per-provider config, e.g. 30 vs 60 min; longer for high-ticket products) — one global value or per-provider config?
- Q2: Keyless dedupe window (fingerprint-based, e.g. 60–120s) — length, and does it vary by rail latency?
- Q3: Guest product checkout in v1 — enable with magic-link history, or login-required everywhere (same as orchestration Q2, decided once here)?
