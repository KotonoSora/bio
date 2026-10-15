# Refund Processing (request, execute, revoke — any rail, no default)

PLAN ref: P3-1/P3-2 (settlement) + `payment/order-status-model.md` (paid → refunded edge) + `payment/payment-security.md` (baseline). DEVIATION FLAGGED: PLAN's PayOS-default reading is not implemented — refunds route by the order's stored `provider` across all eight specified rails with explicit dispatch and no fallback. Member mechanics stay in their minors; ledger/lifecycle/receipts in `billing/`; credit-note shape in `invoices/credit-notes.md`.

## Purpose

Define one refund lifecycle every rail shares: who may ask, who must approve, how money moves back on that rail, and what happens to entitlements and evidence afterward. Each provider minor documents its own reversal mechanism today (API, manual transfer, or cancel-only); this doc is the router and the rulebook so a refund on SePay is as audited and idempotent as one on Stripe, and no path silently defaults to PayOS behavior.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Request a refund for a paid order; track request to approved to reversed states | One refund lifecycle shared by every rail |
| Support/ops (restricted role) | Approve, execute, and evidence reversals, including manual bank transfers | Two-person evidenced for manual bank reversals |
| Finance | Reconcile PSP references against the refunds ledger and credit notes | Uses one refunds ledger and credit notes |
| System | Block double-execution and illegal transitions regardless of caller | Idempotent and terminal-safe by construction |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Request a refund for a paid order and track request to approved to reversed states | A refund on any rail is as audited and idempotent as any other | Must |
| US-02 | Support/ops | Approve, execute, and evidence reversals including manual bank transfers | No path silently defaults to another rail's behavior | Must |
| US-03 | Finance | Reconcile PSP references against the refunds ledger and credit notes | Reversals stay audited and idempotent | Must |
| US-04 | System | Block double-execution and illegal transitions regardless of caller | Retries and races resolve to one outcome | Must |

## Functional requirements

1. Eligibility (uniform): only `paid` orders may be refunded; `pending|authorizing|failed|cancelled|expired` requests are rejected with the terminal-state reason. Full refund moves `paid → refunded` (terminal); partial refund keeps `paid` and appends a partial entry in the refunds ledger (resolves status-model Q2 for v1: no `partially_refunded` status). Disputes are not refunds — `disputed` orders follow their member's dispute path first.
2. Explicit provider routing (no default): execution dispatches on the order's stored `provider` to that member's mechanism — `payos`: cancel-before-pay only in `payos/link-lifecycle.md`, post-paid reversal is manual/ops-evidenced; `sepay`: manual bank reversal via `sepay/transaction-matching.md` exceptions flow; `vnpay`: refund API per `vnpay/query-refund.md`; `zalopay`: refund API per `zalopay/query-refund.md`; `napas`: rail/manual reversal per `napas/reconciliation.md`; `googlepay`/`applepay`: reversal via the underlying PSP path per `google-pay/pending-refund.md` / `apple-pay/pending-refund.md`; `stripe`: Refunds API per `stripe/pending-refund.md`. Unknown provider → reject; cross-provider execution (e.g. refunding a SePay order via Stripe) is impossible by construction.
3. Request + approval: buyer (owner-scoped, Turnstile + rate-limited) may open a request with reason; execution requires the restricted ops role (two-person evidenced for manual bank reversals: reversal transfer reference recorded before any ledger transition). Buyer self-service execution window per Q2; default is request-only in v1. Every request carries an idempotency key — retries and double-clicks are durable no-ops.
4. Atomic execution: the `paid → refunded` transition fires only inside the member's amount-checked settlement transaction (full amount equals `amount_vnd`; partial sums never exceed it). Mismatch, already-terminal, or PSP failure → no transition + alert + retryable request state, never a half-revoked entitlement.
5. Entitlement revocation: on full refund, plan grants downgrade per `billing/subscription-lifecycle.md` (lossless hide/read-only, never silent delete) and product claims invalidate in the shop major; on partial refund, entitlements stand and the ledger records the adjustment. Re-grant of a `refunded` order is refused — repurchase means a new order.
6. Evidence: every approval, execution, and PSP reference appends append-only `audit_log{actor, action, target, at}`; buyer history and receipts update to the refunded/adjusted state; credit notes follow `invoices/credit-notes.md`. Card/bank/PAN material never appears in logs, receipts, or notes.

## Data / API touches

- Ledger `refunds{order_id UNIQUE per full refund (partial stacking per policy), user_id TEXT NOT NULL + index + RLS, provider, amount_vnd INT, status (requested|approved|executing|reversed|failed), psp_ref?, idempotency_key UNIQUE, actor, at}`. All PG via `worker/db/*`, least-privilege app role; dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints: `POST /api/billing/orders/:orderCode/refund-request` (owner-scoped, Turnstile + per-IP/email limits), `GET /api/billing/refunds/:id` (owner view of own requests), ops `POST /api/v1/ops/refunds/:id/approve|execute|reject` (restricted role, rate-limited, audited). Member PSP APIs (refund/retrieve/query) called server-side only; per-member secrets via `wrangler secret` (`.dev.vars` locally; never committed/logged/bundled); `.gitignore` guards `.wrangler/`, `.dev.vars*`.
- Jobs/queues: execution retries with backoff (never in-request PSP blocking beyond the first attempt); ops alerts on every execution/failure; metrics feed settlement dashboards.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Eight-rail refund drill: one paid order per registry member (payos, sepay, vnpay, zalopay, napas, googlepay, applepay, stripe) driven request to approve to execute; each lands `refunded` (full) with its member's PSP/manual reference recorded, entitlements revoked per rule, re-grant refused | One paid order per registry member | Request to approve to execute runs | Each lands `refunded` with its member's PSP/manual reference recorded, entitlements revoked per rule, and re-grant refused |
| AC-02 | Partial drill: partial sums ledger correctly, order stays `paid`, totals never exceed `amount_vnd`; over-partial rejected | A partial refund | Partial sums are ledgered | The order stays `paid`, totals never exceed `amount_vnd`, and over-partial is rejected |
| AC-03 | Idempotency drill: retried request/execution with the same key is a no-op single reversal; concurrent approve+execute races resolve to one outcome | A retried request/execution with the same key and concurrent approve+execute races | Retries and races run | The retry is a no-op single reversal and races resolve to one outcome |
| AC-04 | Eligibility drill: refund of non-`paid` orders refused with the terminal reason; `disputed` orders routed to dispute handling, not refunded | A refund for non-`paid` and `disputed` orders | Eligibility runs | Non-`paid` refunds are refused with the terminal reason and `disputed` orders route to dispute handling |
| AC-05 | No-default drill: routing reads the order's `provider` only with unknown codes rejected; grep gate finds no hardcoded provider default or cross-rail fallback in refund code | Refund routing on stored `provider` | Routing runs and the grep gate scans refund code | Routing reads the order's `provider` only, unknown codes are rejected, and the grep gate finds no hardcoded default or cross-rail fallback |
| AC-06 | IDOR probe (S-1/S-6): user A requesting/reading user B refunds returns 403; RLS denies cross-user rows at DB; non-ops on ops endpoints returns 403 | User A requesting/reading user B refunds and non-ops on ops endpoints | The operations are attempted | The API returns 403 and RLS denies cross-user rows at DB |
| AC-07 | Baseline: request/history views XSS-stripped with CSP holding (S-2); anonymous-adjacent endpoints Turnstile + WAF throttled (S-5); secrets grep clean with quarterly rotation (P5-4); manual bank reversals show two-person evidence before transition | Request/history views, anonymous-adjacent endpoints, repo under secrets grep, and manual bank reversals | XSS, abuse, secrets, and evidence checks run | Views are XSS-stripped with CSP holding (S-2), endpoints are Turnstile + WAF throttled (S-5), secrets grep stays clean with quarterly rotation, and manual reversals show two-person evidence before transition |

## Out of scope

- Member verify/grant/sweep mechanics and dispute contest detail (each member minor: `stripe/pending-refund.md`, `vnpay/query-refund.md`, `zalopay/query-refund.md`, `google-pay/pending-refund.md`, `apple-pay/pending-refund.md`, `napas/reconciliation.md`, `sepay/transaction-matching.md`, `payos/link-lifecycle.md` + `payos/webhook.md`).
- Canonical transitions beyond the refund edge (see `payment/order-status-model.md`); probe battery (see `payment/payment-security.md`); downgrade execution (`billing/subscription-lifecycle.md`); receipt/credit-note rendering (`billing/invoices-receipts.md`, `invoices/credit-notes.md`); catalog/coupons (`subscription/`, `promo-code/`); P4/P5.

## Open questions

- Q1: Partial refunds in v1 — full-only with re-purchase for adjustments, or partial ledger entries from launch (same as VNPay-doc Q2, decided once here)?
- Q2: Buyer self-service execution (windowed auto-approve) vs ops-only execution in v1?
- Q3: Bank-rail reversal SLA and evidence bar (reversal transfer reference + second approver proposed) — confirm?
