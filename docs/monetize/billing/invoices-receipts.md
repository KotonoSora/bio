# Invoices + Receipts (billing history, proof of payment)

PLAN ref: P3-1/P3-2 evidence trail (decides the checkout docs' open invoice-format Q2 across providers). Grants happen in provider minors; this doc proves them to the buyer.

## Purpose

Give every buyer one trustworthy billing history: plan purchases, product purchases, renewals, and refunds rendered as receipts with stable identifiers, delivered by email at grant time and re-downloadable from the dashboard. This resolves the "invoice/receipt format for VN compliance" question left open in the provider checkout docs, and gives support a single artifact to reference instead of eight provider dashboards.

## Users

- Buyer: receives a receipt email on every paid grant and refund; re-downloads history for reimbursement or tax filing.
- Support/ops: cites one receipt id per dispute instead of hunting provider consoles.
- Finance: exports order/refund rows for bookkeeping (reports themselves stay out of scope).

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

- Paid grant on any provider → receipt email within the delivery SLA + history row with matching ids, amounts, and references; print view renders clean A4.
- Refund drill: credit note appended to the original receipt, email sent, history row `refunded` — original entry never edited.
- Tamper/IDOR probe: user A fetching user B receipts → 403; RLS denies cross-user rows; receipt URLs unguessable (orderCode + owner scope, no sequential public ids).
- Unsubscribed-from-marketing buyer still receives transactional receipts; marketing-campaign sends remain a separate, P4-gated system.
- Footer carries the non-VAT-invoice disclaimer verbatim on every receipt and email.
- Email-outage drill: queued retries, no grant blocked on send failure, backlog visible in ops metrics.

## Out of scope

- Ledger writes and lifecycle transitions (see `billing/entitlement-ledger.md`, `billing/subscription-lifecycle.md`).
- Provider checkout/webhook/settlement internals (their own minors); plan matrix/AI caps (`plans/`); marketing campaigns and broadcasts (P4); accounting/revenue-recognition reports; full VAT e-invoice issuance (named future work).

## Open questions

- Q1: Receipt localization — Vietnamese + English bilingual v1, or Vietnamese-first?
- Q2: Guest-checkout receipts (buyer-email claim, no account) — magic-link access in v1, or account-required to view history?
