# Stripe Pending + Refund + Dispute (settle, reverse, contest)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: extends the adapted Stripe path only. Card-specific realities shape this doc: intents can sit mid-3DS (`requires_action`), captures can be delayed, money can be pulled back (refunds), and buyers can force reversals (disputes) — none of which exist on bank-transfer rails. Happy-path grant in `monetize/stripe/webhook.md`.

## Purpose

Close every gap the PaymentIntent round-trip can leave: buyers who abandon 3DS, intents stuck `requires_action`/`processing`, legitimate refund requests, and chargeback disputes. A retrieve-based sweep settles the ambiguous middle; a defined refund path reverses what should not stand; a dispute playbook protects revenue with evidence instead of improvisation. (If Stripe's webhooks ever go quiet, Stripe's own guidance applies: poll the API for the resource's current state.)

## Users

- Buyer stuck on "confirming" (3DS abandoned, asynchronous method pending, webhook delayed): *As a Buyer stuck on "confirming", I want to get resolution via status verify instead of limbo so that gap the PaymentIntent round-trip can leave as defined here.* gets resolution via status verify instead of limbo.
- Support/ops: *As a Support/ops, I want to look up live intent status, issue refunds, assemble dispute evidence so that gap the PaymentIntent round-trip can leave as defined here.* looks up live intent status, issues refunds, assembles dispute evidence.
- System (scheduled job): *As a System, I want to sweep `authorizing`/stale-`pending` orders against Stripe truth so that gap the PaymentIntent round-trip can leave as defined here.* sweeps `authorizing`/stale-`pending` orders against Stripe truth.

## Functional requirements

1. Pending-state machine: client-side 3DS/`requires_action` is completed in-page via Stripe.js; if the buyer abandons it, the order parks in `authorizing` (sub-state `confirming`) with a TTL. The buyer page polls `GET /api/billing/orders/:orderCode` and offers "check again" (on-demand retrieve) plus support escalation — no silent limbo, no auto-grant on non-terminal states.
2. On-demand verify: past a short threshold, the server retrieves the PaymentIntent (`stripe_pi_id`) and applies the authoritative status through the same atomic, amount-checked, idempotent grant logic as the webhook (`succeeded` → grant exactly once; `requires_payment_method`/`canceled` → local `failed`/`expired`; anything non-terminal → still confirming). Throttled per order to bound API calls.
3. Reconciliation sweep: scheduled job scans `authorizing`/stale-`pending` orders, retrieves each intent, and settles them (grant on `succeeded` with the idempotent transition; terminalize the rest; already-`paid` rows skipped). Cadence and stale threshold per Q1.
4. Refunds (ops- or buyer-requested per policy in Q2, authenticated, audited): Refunds API against the charge/PaymentIntent, full or partial with a `reason`; records the Stripe refund id, flips status to `refunded` (or stays `paid` with a partial-refund ledger entry per implementation note), and revokes the entitlement (plan downgrade per the plans doc's downgrade rule; product claim invalidated in the shop major). Note the currency caveat: converted-amount disputes/refunds settle at current rates, so refunded VND-equivalents can differ from the original — finance-visible, surfaced in ops tooling. Every refund appends `audit_log{actor, action, target, at}`.
5. Disputes/chargebacks (card-only risk): `charge.dispute.created` flips the order to `disputed` (grant frozen — no new entitlements, existing access flagged per the plans/shop dispute rule) and opens an evidence task with Stripe's deadline clock; submit evidence via the Disputes API before it lapses. `won` → status restored; `lost` → `chargeback_reversed`, entitlement revoked, loss logged for fraud review. Repeat-offender signals feed a lightweight blocklist proposal for human approval — never auto-ban in v1.
6. Fraud hygiene proportionate to cards: per-IP/email velocity limits on checkout + confirm (shared with Turnstile/WAF baseline); Radar/review outcomes honored where enabled (never bypass holds); 3DS liability-shift signals recorded on the order for dispute evidence.

## Data / API touches

- Reads/writes `orders` (`status` incl. `authorizing|disputed|chargeback_reversed`, `stripe_pi_id`, seen event ids) plus entitlement/claim rows owned by sibling specs; optional `refunds{order_id, stripe_refund_id, amount_vnd, status}` ledger for partials with standard `user_id` + RLS. Idempotency comes from the guarded transitions + UNIQUE event ids shared with the webhook.
- Stripe APIs called server-side only (restricted secret key in `wrangler secret`): retrieve PaymentIntent (verify + sweep), Refunds, Disputes/evidence. Responses logged minus secrets.
- Ops endpoints (restricted role, rate-limited, audited): `POST /api/v1/ops/stripe/verify`, `GET /api/v1/ops/stripe/orders/:orderCode`, `POST /api/v1/ops/stripe/refund`, dispute evidence upload. All PG via `worker/db/*`.
- Alerting: sweep-settled counts → metrics; every refund/dispute → ops channel; consecutive Stripe API auth failures → page.

## Acceptance criteria

- 3DS-abandon drill (sandbox): order parks in confirming, on-demand retrieve returns non-terminal, sweep later expires it; no grant, nothing captured.
  *GWT: Given 3DS-abandon drill (sandbox) context; When order parks in confirming, on-demand retrieve returns non-terminal, sweep later expires it; Then no grant, nothing captured.*
- Webhook-suppression drill: retrieve grants exactly once on `succeeded`; redelivered webhook afterward is a no-op.
  *GWT: Given Webhook-suppression drill context; When retrieve grants exactly once on `succeeded`; Then redelivered webhook afterward is a no-op.*
- Refund drill (sandbox, full + partial): references recorded, entitlements revoked per rule, re-grant impossible.
  *GWT: Given Refund drill (sandbox, full + partial) context; When the checked action occurs; Then references recorded, entitlements revoked per rule, re-grant impossible.*
- Dispute drill: dispute webhook freezes the order, evidence submitted before the deadline, win/loss transitions applied correctly.
  *GWT: Given Dispute drill context; When the checked action occurs; Then dispute webhook freezes the order, evidence submitted before the deadline, win/loss transitions applied correctly.*
- Auth probe: non-ops user on ops endpoints → 403; RLS denies cross-owner rows.
  *GWT: Given Auth probe: non-ops user on ops endpoints; When the transition executes; Then 403; RLS denies cross-owner rows.*

## Out of scope

- PaymentIntent creation and client confirmation (see `monetize/stripe/checkout-orders.md`); webhook verification and grant (see `monetize/stripe/webhook.md`).
- Bank-rail exception handling (their own minors); plan downgrade UX detail (`docs/monetize/plans/`); accounting/revenue reports; P4/P5.

## Open questions

- Q1: Sweep cadence and stale threshold — every 15 min + 30-min threshold, or tighter for cards?
- Q2: Buyer-initiated refunds (self-service window) vs ops-only in v1; partial refunds supported at launch?
- Q3: Adaptive Pricing / multi-currency presentation now, or VND-only at launch?
