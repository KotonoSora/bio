# Refunds - Architecture and Tech Stack (feature 38)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/38-refunds/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| The refunds + credit-notes tables (V5), the request/approve/execute state machine, the eight-rail `RefundStrategy` registry, manual evidence + two-person enforcement, retry/backoff executor, revocation orchestration, `order.refunded.v1` emission, ops queue reads | Order state + `paid → refunded` edge -> 34's guard; subscription rows + lossless downgrade path -> 36; receipt linkage + credit-note rendering + `billing.credit_note` template -> 37; per-rail member mechanics (PSP endpoints, query APIs, manual reversal flows) -> 40 (conformance consumes this contract); system-class callers -> 25 (bookings/tips policies); notification pipeline -> 32 |

No new service, no new Redis keys (`bill:rl:*` reused for throttles), strategies are in-process modules — no scheduled job beyond the retry executor.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.billing.refunds`: request/approve/execute services + `RefundStrategy` registry keyed by `provider`; no strategy lookup ever falls back (unknown = reject; CI grep gate) | 32's no-default posture extended to the reversal path |
| D-2 | `V5__refunds.sql`: `refunds` (one row per order, `uniq_rf_order`) + `credit_notes` (one per refund, `UNIQUE refund_id`); no `orders`/`subscriptions` DDL changes — the status set and the lossless downgrade path already exist (34/36) | Single-DDL-owner: V5 is the only new persistence |
| D-3 | State machine: `requested → approved → executing → reversed`; `requested → rejected` (terminal); `executing → failed` (retryable). Transitions are status-guarded conditional updates under `SELECT … FOR UPDATE`; idempotency key `UNIQUE` makes request retries durable no-ops | One writer, race-safe, replay-proof |
| D-4 | Strategies: `vnpay`/`zalopay`/`stripe` = API refund calls (`applepay`/`googlepay` via their underlying PSP path); `payos`/`sepay`/`napas` = manual bank reversals (evidence + two-person). Each strategy declares `kind: api|manual` + whether a PSP idempotency key is supported; per-member HTTP/flow detail lives with 40's members | Explicit dispatch; 40's conformance exercises members end-to-end |
| D-5 | Execution split: `approved → executing` commits before any external call; PSP/manual work happens outside transactions; the final transaction re-asserts the amount and commits order edge + row `reversed` + `psp_ref` + credit note + revocation + `refund.emitted` marker together | No half-revoked state; replays collapse at the guards |
| D-6 | Revocation: plan orders match the current row via `plan.granted` origin or audited `plan.renewed` extension orderCode (36's audit detail); match -> close `expired` + free floor + `plan.refunded`; no match -> no entitlement change. Product claims invalidate via the event (23/24/25 own consumers). Edge cases compensate via 36's period-fix | Reuses the lossless machinery; no new subscription statuses or columns |
| D-7 | System-class entry: `POST /internal/v1/billing/refunds` (service JWT) with reason allowlist `cancel_ge_24h|creator_cancel|hold_expired` + source ref + idempotency key enters `approved` directly; API-rail rows auto-execute via the executor, manual-rail rows join the ops queue | Policy-driven refunds never touch a human for API rails; nothing skips evidence rules |
| D-8 | Credit notes issue inside the final transaction (`CN-<orderCode>-<seq>`, seq = 1 under full-only); the email enqueues post-commit (`billing.credit_note`, key `cn:{refundId}`); 37 renders the chain | Append-only evidence; rendering stays 37's |
| D-9 | Emission mirrors 34: post-commit publish to `billing.events` (frozen shape `{orderId, refundId, refundedAt}`) + `refund.emitted` marker + reconciliation re-emitting marker-less reversed refunds once; consumers idempotent | Same self-heal mechanics as grants |
| D-10 | Executor: in-process retry loop + backoff (1 min → 15 min → 1 h → 6 h caps, ≤ 5 attempts, ≤ 24 h) then `failed` + ops alert; ops re-execute is status-guarded; rails without PSP idempotency keys query-first before any retry | Bounded automation; no blind double-calls |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | Registry rows: refund request + reads, ops queue routes, internal route |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/02-database-schema-design.md` | Chain reference: `provider_events` row corrected to V7 (37's renumber; stale V6 removed) |
| `docs/02-architecture-techlead/36-subscription-lifecycle/02-database-schema-design.md` | `plan.renewed` audit detail carries the renewing `orderCode` (revocation matcher input) |
| 23 / 24 / 25 | No edits: their `order.refunded.v1` consumers are already frozen forward contracts ("forward, 38"); this feature activates them |
| `docs/02-architecture-techlead/37-invoices-and-receipts/` | No edits: AC-13 activates with this feature; `billing.credit_note` template is 37's D-4 reserved enqueue |
