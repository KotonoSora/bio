# Invoices + Receipts (billing history, proof of payment)

PLAN ref: P3-1/P3-2 evidence trail (decides the checkout docs' open invoice-format Q2 across providers). Grants happen in provider minors; this doc proves them to the buyer.

## Purpose

Give every buyer one trustworthy billing history: plan purchases, product purchases, renewals, and refunds rendered as receipts with stable identifiers, delivered by email at grant time and re-downloadable from the dashboard. This resolves the "invoice/receipt format for VN compliance" question left open in the provider checkout docs, and gives support a single artifact to reference instead of eight provider dashboards.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Receive receipt email on every paid grant and refund; re-download history | One trustworthy billing history for reimbursement or tax filing |
| Support/ops | Cite one receipt id per dispute | Uses a single artifact instead of eight provider dashboards |
| Finance | Export order/refund rows for bookkeeping | Reports themselves stay out of scope |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Receive a receipt email on every paid grant and refund and re-download history | I have one trustworthy billing history for reimbursement or tax filing | Must |
| US-02 | Support/ops | Cite one receipt id per dispute | I use a single artifact instead of eight provider dashboards | Must |
| US-03 | Finance | Export order/refund rows for bookkeeping | Receipts prove every grant to the buyer | Must |

## Functional requirements

1. Receipt content (uniform across all eight providers): receipt id (`RCPT-<orderCode>`), buyer identifier (account email + optional buyer_email), line items (plan term or product title), `amount_vnd` integer, provider name + provider reference (`orderCode`, plus `vnp_TransactionNo` / `zp_trans_id` / `stripe_pi_id` / rail txn id where applicable), grant timestamp ISO8601, and support contact. No card/bank/PAN data ever appears.
2. Billing history page (`/app/billing/history`, owner-scoped): chronological orders + refunds with statuses (`pending|paid|cancelled|expired|failed|refunded|disputed`), receipt re-download (print-friendly HTML → `window.print()` PDF, matching the resume-print pattern — no server PDF engine), and per-row "need help" linking the receipt id into support.
3. Email delivery at grant/refund time via Resend receipts channel (transactional only — campaigns remain deferred to P4): subject carries plan/product + amount; body mirrors the dashboard receipt; delivery failures logged and retried with backoff, visible in ops metrics. Unsubscribed-from-marketing users still receive transactional receipts (separate consent class, consistent with the leads `consent_at` model).
4. VN compliance note (explicit, non-blocking): v1 receipts are payment confirmations, not VAT e-invoices (`hóa đơn GTGT`); the doc states this on the receipt footer so no buyer mistakes it for a tax invoice. Full e-invoice issuance is a named future work item with its own tax-registration prerequisites — not smuggled into v1 scope.
5. Corrections: refunds append a linked credit note to the original receipt (same receipt id, `refunded` annotation + gateway refund reference) rather than editing history — the ledger is append-only.
6. Retention: receipts follow order retention (same lifecycle as `orders`); raw provider payloads age out per their settlement docs while receipts persist.

## Data / API touches

- No new tables in v1: receipts render from `orders` + `subscriptions` + refund annotations (a `receipts` materialization is deferred until volume demands it). Reads scope `WHERE user_id = session.userId` (+ buyer_email claim match for guest product orders, if guest checkout was enabled).
- Endpoints (owner-scoped): `GET /api/billing/history`, `GET /api/billing/receipts/:orderCode` (HTML + print CSS). Email send is a queued side-effect of each provider's grant/refund handler (Queue, never in-request blocking).
- Resend credentials via `wrangler secret`; templates versioned in code; per-send logging (to, template, orderCode, result) minus PII beyond the owner scope.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Paid grant on any provider yields receipt email within the delivery SLA + history row with matching ids, amounts, and references; print view renders clean A4 | A paid grant on any provider | Grant completes | A receipt email sends within the delivery SLA with a history row carrying matching ids, amounts, and references and a clean A4 print view |
| AC-02 | Refund drill: credit note appended to the original receipt, email sent, history row `refunded` with original entry never edited | A refund on a paid order | Refund executes | A credit note is appended to the original receipt, email is sent, the history row reads `refunded`, and the original entry is never edited |
| AC-03 | Tamper/IDOR probe: user A fetching user B receipts returns 403; RLS denies cross-user rows; receipt URLs unguessable (orderCode + owner scope, no sequential public ids) | User A fetching user B receipts | The fetch is attempted | The API returns 403 and RLS denies cross-user rows |
| AC-04 | Unsubscribed-from-marketing buyer still receives transactional receipts; marketing-campaign sends remain a separate, P4-gated system | A buyer unsubscribed from marketing | A transactional receipt sends | The buyer still receives it while marketing-campaign sends remain a separate P4-gated system |
| AC-05 | Footer carries the non-VAT-invoice disclaimer verbatim on every receipt and email | Every receipt and email | Rendering occurs | The footer carries the non-VAT-invoice disclaimer verbatim |
| AC-06 | Email-outage drill: queued retries, no grant blocked on send failure, backlog visible in ops metrics | An email outage during grant/refund | Delivery fails | Sends queue with retries, no grant is blocked on send failure, and backlog is visible in ops metrics |

## Out of scope

- Ledger writes and lifecycle transitions (see `billing/entitlement-ledger.md`, `billing/subscription-lifecycle.md`).
- Provider checkout/webhook/settlement internals (their own minors); plan matrix/AI caps (`plans/`); marketing campaigns and broadcasts (P4); accounting/revenue-recognition reports; full VAT e-invoice issuance (named future work).

## Open questions

- Q1: Receipt localization — Vietnamese + English bilingual v1, or Vietnamese-first?
- Q2: Guest-checkout receipts (buyer-email claim, no account) — magic-link access in v1, or account-required to view history?
