# NAPAS Reconciliation (check-transaction, expiry, refunds)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: extends the adapted NAPAS path only. Rail-verified behaviors shape this doc: dynamic codes die at ~15 min (late payment = new transaction, old marked cancelled), and unmatched transfers arrive without any order ref. Happy-path grant in `monetize/napas/balance-sync.md`.

## Purpose

Guarantee no real payment goes unnoticed and no ambiguous transfer auto-grants. Late (post-expiry), edited-content (ref-less), and wrong-amount transfers land in a reviewable exceptions queue with a defined resolution; a scheduled check-transaction sweep catches anything the sync push dropped. Dropped or duplicate deliveries must never go unnoticed.

## Users

- Buyer who paid late, edited the content, or sent the wrong amount: *As a Buyer who paid late, edited the content, or sent the wrong amount, I want to get a resolvable outcome instead of silent loss so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* gets a resolvable outcome instead of silent loss.
- Support/ops: *As a Support/ops, I want to triage the exceptions queue, link transfers to orders, issue entitlements or refund so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* triages the exceptions queue, links transfers to orders, issues entitlements or refunds.
- System (scheduled job): *As a System, I want to poll check-transaction by order ref and sweep stale pendings so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* polls check-transaction by order ref and sweeps stale pendings.

## Functional requirements

1. Exceptions ledger: every acknowledged-but-ungranted inbound transfer persists to `napas_exceptions{napas_txn_id UNIQUE, amount, content?, reason, status, linked_order_code?, resolved_at?}` with `user_id`-scoping where attributable (nullable until linked) + RLS. `reason` ∈ `unknown_ref|amount_mismatch|order_cancelled|order_expired|late_transfer|duplicate_ref`. `status` ∈ `open|linked|refunded|dismissed`.
2. Safe auto-resolution rules (server-side, conservative): exact-ref + exact-amount + pending order → grant (this is the sync happy path, not an exception). Everything else stays `open` for human review — underpays, ref-less transfers, and post-expiry arrivals never auto-grant. Late transfers are linkable by ops within a grace window (default 24 h, see Q3); older ones default to refund.
3. Ops actions (authenticated, audited): link exception → pending order (re-runs the atomic grant), mark refunded (records the rail/bank refund reference; rail refund capability is package-dependent, so manual bank refund is the default path — see Q2), or dismiss with reason. Every action appends `audit_log{actor, action, target, at}`.
4. Check-transaction sweep: scheduled job queries the rail check-transaction API by order/transaction code for stale `pending` orders and settles them through the same match/exception logic (`paid` on rail-paid with the idempotent grant; `expired`/`cancelled` otherwise; already-recorded ones skipped by the UNIQUE guard). Push stays the realtime path; check-transaction is lookup-and-repair only. Cadence and lookback per Q1.
5. Expiry semantics honored: a dynamic code paid after its ~15-min validity is a new rail transaction against a cancelled order — the sweep/exception flow treats it as `late_transfer`, never as a grant for the dead order.
6. Buyer self-service: `GET /api/billing/orders/:orderCode` shows pending-order state including "transfer received, under review" when a same-ref exception exists — no silent limbo.

## Data / API touches

- New table `napas_exceptions` (schema above) + `orders.napas_txn_id` (sync doc). Optional ops endpoints: `GET /api/v1/ops/napas-exceptions`, `POST /api/v1/ops/napas-exceptions/:id/link|refund|dismiss` — restricted role, rate-limited, fully audited. All PG via `worker/db/*`.
- Rail APIs called server-side only (credentials in `wrangler secret`): QR issuance (checkout doc), check-transaction (sweep), test-callback (UAT). Responses logged minus secrets.
- Alerting: new `open` exception → ops channel; sweep delta (found-missing count) → metrics log; consecutive sweep auth failures → page.

## Acceptance criteria

- Simulated edited-content transfer: 200 acknowledged, zero grant, `open` exception with `unknown_ref`, visible in ops queue.
  *GWT: Given Simulated edited-content transfer context; When the checked action occurs; Then 200 acknowledged, zero grant, `open` exception with `unknown_ref`, visible in ops queue.*
- Underpay simulation: `amount_mismatch`, no grant; ops link after buyer tops up grants exactly once.
  *GWT: Given Underpay simulation context; When `amount_mismatch`, no grant; Then ops link after buyer tops up grants exactly once.*
- Post-expiry payment inside grace: linkable by ops to a single grant; outside grace: refund path.
  *GWT: Given Post-expiry payment inside grace context; When linkable by ops to a single grant; Then outside grace: refund path.*
- Sync-suppression drill (drop pushes, run sweep): all missing transfers recovered with correct outcomes, no doubles on redelivery.
  *GWT: Given Sync-suppression drill (drop pushes, run sweep) context; When the checked action occurs; Then all missing transfers recovered with correct outcomes, no doubles on redelivery.*
- Auth probe: non-ops user hitting ops endpoints → 403; RLS denies cross-owner rows.
  *GWT: Given Auth probe: non-ops user hitting ops endpoints; When the transition executes; Then 403; RLS denies cross-owner rows.*
- Refund action records an external reference and flips status to `refunded`; refunded transfers can never later grant.
  *GWT: Given Refund action records an external reference and flips status to `refunded`; When the next step executes; Then refunded transfers can never later grant.*

## Out of scope

- QR issuance and checkout (see `monetize/napas/checkout-orders.md`); sync verification and grant (see `monetize/napas/balance-sync.md`).
- PayOS/SePay/VNPay/ZaloPay handling (their own minors); plan downgrade UX (`docs/monetize/plans/`); accounting/revenue reports; P4/P5.

## Open questions

- Q1: Sweep cadence and stale-pending threshold — every 15 min + 30-min threshold, or tighter?
- Q2: Refund rail — priority package with refund support, or manual bank refunds in v1?
- Q3: Late-transfer grace length — 24 h default acceptable?
