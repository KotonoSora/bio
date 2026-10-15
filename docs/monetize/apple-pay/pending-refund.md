# Apple Pay Pending + Refund + Dispute (settle, reverse, contest)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: extends the adapted Apple Pay path only. Card-specific realities shape this doc: charges can sit pending (3DS step-up, issuer review), money can be pulled back (refunds), and buyers can force reversals (chargebacks) — none of which exist on bank-transfer rails. Happy-path grant in `monetize/apple-pay/authorization.md`.

## Purpose

Close every gap the sheet-charge round-trip can leave: sessions abandoned mid-3DS, authorizations stuck in review, legitimate refund requests, and chargeback disputes. A status-query sweep settles the ambiguous middle; a defined refund path reverses what should not stand; a dispute playbook protects revenue with evidence instead of improvisation.

## Users

- Buyer stuck on "confirming" (3DS abandoned, issuer review, webhook delayed): gets resolution via status verify instead of limbo.
- Support/ops: looks up live charge status, issues refunds, assembles dispute evidence.
- System (scheduled job): sweeps `authorizing`/stale-`pending` orders against PSP truth.

## Functional requirements

1. Pending-state machine: `authorizing` covers charge-in-flight; PSP-reported review/3DS-required maps to a buyer-visible `confirming` sub-state (stored as `authorizing` + `confirming_reason`) with a TTL. The buyer page polls `GET /api/billing/orders/:orderCode` and offers "check again" (on-demand status query) plus support escalation — no silent limbo, no auto-grant on non-terminal states. A sheet closed without authorization (`completePayment` never reached) leaves the order `pending`, eligible for a fresh sheet.
2. On-demand verify: past a short threshold, the server calls the PSP adapter `status(orderCode | psp_txn_id)` and applies the authoritative result through the same atomic, amount-checked, idempotent grant logic as the charge path. Throttled per order to bound PSP calls.
3. Reconciliation sweep: scheduled job scans `authorizing`/stale-`pending` orders, status-queries each, and settles them (`paid` on PSP-captured with the idempotent grant; `expired`/`failed` otherwise; already-`paid` rows skipped). Cadence and stale threshold per Q1.
4. Refunds (ops- or buyer-requested per policy in Q2, authenticated, audited): PSP adapter `refund(psp_txn_id, amount)` full or partial; records the gateway refund reference, flips status to `refunded` (or stays `paid` with a partial-refund ledger entry per implementation note), and revokes the entitlement (plan downgrade per the plans doc's downgrade rule; product claim invalidated in the shop major). Every refund appends `audit_log{actor, action, target, at}`.
5. Disputes/chargebacks (card-only risk): PSP dispute webhooks flip the order to `disputed` (grant frozen — no new entitlements, existing access flagged per the plans/shop dispute rule) and open an evidence task with a deadline clock. Win → status restored; loss → status `chargeback_reversed`, entitlement revoked, loss logged for fraud review. Repeat-offender signals (same buyer/device) feed a lightweight blocklist proposal for human approval — never auto-ban in v1.
6. Fraud hygiene proportionate to cards: per-IP/email velocity limits on checkout + validate + authorize (shared with Turnstile/WAF baseline), 3DS outcomes honored (never bypass step-up), device/session anomalies (sheet opened but token posted from a different session) logged for review.

## Data / API touches

- Reads/writes `orders` (`status` incl. `authorizing|disputed|chargeback_reversed`, `psp_txn_id`) plus entitlement/claim rows owned by sibling specs; optional `refunds{order_id, psp_refund_id, amount_vnd, status}` ledger for partials with standard `user_id` + RLS. Idempotency comes from the guarded transitions shared with the charge path.
- PSP APIs called server-side only (keys in `wrangler secret`): charge (authorization doc), status query (sweep), refund, dispute webhooks. Responses logged minus secrets.
- Ops endpoints (restricted role, rate-limited, audited): `POST /api/v1/ops/applepay/verify`, `GET /api/v1/ops/applepay/orders/:orderCode`, `POST /api/v1/ops/applepay/refund`, dispute evidence upload. All PG via `worker/db/*`.
- Alerting: sweep-settled counts → metrics; every refund/dispute → ops channel; consecutive PSP auth failures → page.

## Acceptance criteria

- 3DS-abandon drill (sandbox): order parks in confirming, on-demand status returns non-terminal, sweep later expires it; no grant, no charge captured. Sheet-closed-without-payment leaves a reusable `pending` order.
- Webhook-suppression drill: status query grants exactly once on PSP-captured; redelivered webhook afterward is a no-op.
- Refund drill (sandbox, full + partial): references recorded, entitlements revoked per rule, re-grant impossible.
- Dispute drill: dispute webhook freezes the order, evidence submitted before the deadline, win/loss transitions applied correctly.
- Auth probe: non-ops user on ops endpoints → 403; RLS denies cross-owner rows.

## Out of scope

- Session config, gating, token intake, validation, and charge execution (see `monetize/apple-pay/checkout.md` and `monetize/apple-pay/authorization.md`).
- Bank-rail exception handling (their own minors); plan downgrade UX detail (`docs/monetize/plans/`); accounting/revenue reports; P4/P5.

## Open questions

- Q1: Sweep cadence and stale threshold — every 15 min + 30-min threshold, or tighter for cards?
- Q2: Buyer-initiated refunds (self-service window) vs ops-only in v1; partial refunds supported at launch?
- Q3: Dispute evidence pack contents (order logs, delivery proofs, buyer history) — template now or first-dispute-driven?
