# VAT E-Invoices (tax-compliant issuance)

PLAN ref: P3-1/P3-2 evidence trail (activates the "named future work" line in `billing/invoices-receipts.md`). Payment confirmations stay in `billing/`; this doc owns tax invoices only.

## Purpose

Define how the platform issues real Vietnamese VAT e-invoices (`hóa đơn GTGT điện tử`) for paid orders: buyer tax-profile capture, issuance through a contracted e-invoice solution, numbering/series discipline, and adjustments/replacements. The receipts doc deliberately limits v1 receipts to payment confirmations with a non-VAT disclaimer — this doc is the gated follow-on that lifts that disclaimer order by order, only when every legal and technical prerequisite is met. Nothing here ships until the business prerequisites (Q1) are signed off.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Company buyer | Provide tax profile once, receive a compliant e-invoice per paid order | For input-VAT deduction |
| Finance/ops | Monitor issuance status, handle rejections, issue adjustments | Uses series/number discipline and linked adjustments |
| Auditor/tax authority (indirect) | Receive well-formed, sequentially numbered, verifiable invoices | Only when every legal and technical prerequisite is met |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Company buyer | Provide tax profile once and receive a compliant e-invoice per paid order | I can claim input-VAT deduction | Must |
| US-02 | Finance/ops | Monitor issuance status, handle rejections, and issue adjustments | Numbering stays sequential and gapless per series | Must |
| US-03 | Auditor/tax authority | Receive well-formed, sequentially numbered, verifiable invoices | Invoices lift the non-VAT disclaimer order by order | Must |

## Functional requirements

1. Gate: e-invoice issuance stays DISABLED until (a) the operating entity holds tax registration permitting e-invoice issuance, (b) a contracted e-invoice solution is integrated and UAT-verified, and (c) invoice series/numbers are registered per current regulation (confirm the governing circulars at implementation — rules evolve). The gate is a server flag, not UI text; issuance endpoints 403 while closed.
2. Tax-profile capture: company name, tax code (format-validated; checksum-verified where the algorithm is public), address, email — stored per user, editable, versioned (each invoice freezes the profile snapshot used, so later edits never rewrite issued invoices).
3. Issuance flow (per paid order, buyer-opted-in): grant handler emits an e-invoice job carrying order + frozen profile + catalog line items; the adapter calls the e-invoice solution (create → sign → authority-issue), then stores `einvoice{order_id UNIQUE, invoice_no, series, issue_at, status, provider_ref, verification_link}`. Buyer delivery mirrors receipts (dashboard + email); the receipt cross-links the e-invoice and vice versa. Failures park the job in `pending_issue` with bounded retries + ops alert — the underlying grant is never blocked or rolled back by invoice trouble.
4. Numbering discipline: sequential, gapless per series, issued in chronological order; voids/adjustments follow the regulated adjustment/replacement document pattern (never delete, never renumber). Series configuration is an audited ops action.
5. Adjustments and replacements: amount changes post-issuance (e.g. partial refunds after invoicing) produce adjustment invoices linked to the original (same chain id), per regulation; full-order reversals produce replacement/cancellation documents through the same adapter. Credit-note mechanics align with `invoices/credit-notes.md` — one event, both artifacts.
6. Scope limit: e-invoices issue for plan and product sales by the operating entity only; marketplace/agency or cross-entity flows (P4) need their own tax design and are excluded here.

## Data / API touches

- New tables `buyer_tax_profiles{user_id, company, tax_code, address, email, version}` + `einvoices{order_id UNIQUE, invoice_no, series, chain_id, status, provider_ref}` with standard `user_id` + RLS. Issuance jobs ride the existing Queue pattern (never in-request). All PG via `worker/db/*`.
- E-invoice solution behind a narrow adapter (`issue / adjust / replace / fetch-status`) mirroring the PSP-adapter precedent from the wallet minors — swapping solutions is config + adapter work. Credentials in `wrangler secret`; payloads logged minus PII.
- Endpoints (owner-scoped): tax-profile CRUD, `POST /api/billing/einvoices/request/:orderCode` (opt-in issuance), `GET /api/billing/einvoices/:orderCode`; audited ops endpoints for series config and re-drive.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Gate drill: issuance endpoints 403 with the gate closed; full UAT issuance (TEST series) passes before the gate opens | The e-invoice gate closed and TEST series UAT | Issuance is requested and UAT runs | Endpoints return 403 while closed and full UAT issuance passes before the gate opens |
| AC-02 | Issuance drill (sandbox): paid order to job to issued invoice with series/number, frozen profile snapshot, cross-linked receipt; buyer email delivered | A paid order with buyer opt-in | The e-invoice job runs | An invoice issues with series/number, frozen profile snapshot, and cross-linked receipt, and buyer email is delivered |
| AC-03 | Profile-edit drill: editing the tax profile alters future invoices only; issued ones byte-identical | An issued invoice and an edited tax profile | A future invoice issues | Future invoices use the new profile while issued ones stay byte-identical |
| AC-04 | Adjustment drill: partial refund post-invoice produces a linked adjustment document; original never edited or renumbered | A partial refund post-invoice | Adjustment runs | A linked adjustment document produces with the original never edited or renumbered |
| AC-05 | Failure drill: provider outage parks jobs in `pending_issue` with retries + alert; grants unaffected; sweep converges afterward | A provider outage during issuance | Jobs run | Jobs park in `pending_issue` with retries + alert, grants stay unaffected, and the sweep converges afterward |
| AC-06 | Auth probe: cross-user tax-profile/invoice access returns 403; RLS denies cross-user rows | Cross-user tax-profile/invoice access | Access is attempted | The API returns 403 and RLS denies cross-user rows |

## Out of scope

- Payment confirmations and history (`billing/invoices-receipts.md`); proformas (`invoices/proforma-invoices.md`); credit-note presentation detail (`invoices/credit-notes.md`).
- Provider checkout/settlement internals (their own minors); catalog/ledger/lifecycle (`subscription/`, `billing/`); accounting/revenue reports; marketplace/agency tax (P4); P5.

## Open questions

- Q1 (gating): operating-entity tax registration + contracted e-invoice solution — owner and timeline?
- Q2: WHICH e-invoice solution (adapter target) — decision drives field mapping and UAT plan?
