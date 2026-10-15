# Credit Notes (refund and adjustment documents)

PLAN ref: P3-1/P3-2 evidence trail (specifies the "linked credit note" line in `billing/invoices-receipts.md`). Refund execution lives in each provider's settlement doc; VAT adjustments in `invoices/vat-einvoices.md`.

## Purpose

Define the paper trail for money going back: every refund or downward adjustment produces a credit note linked to its original receipt (and e-invoice, where one exists) instead of editing history. The receipts doc promises append-only corrections but never specifies their schema, numbering, or delivery — this doc does, so support, buyers, and finance share one consistent reversal story across all eight providers.

## Users

- Refunded buyer: receives a credit note proving what was returned, linked to the original charge.
- Support/ops: cites one credit-note id per refund dispute instead of reconstructing events.
- Finance: books reversals against originals with unbroken chains.

## Functional requirements

1. Issuance trigger: every terminal refund recorded by any provider settlement path (plus manual ops refunds on bank rails) emits exactly one credit note; duplicate refund events (retries, redelivered webhooks) never mint duplicates — keyed on the refund's gateway reference + order id.
2. Content (uniform across providers): credit-note id (`CN-<orderCode>-<seq>`), link to original receipt id (and e-invoice number/series where issued), line items reversed (full or partial with remaining-balance math shown), `amount_vnd` returned, provider + gateway refund reference, timestamps (original grant + refund execution), and reason code (`buyer_request|duplicate_charge|service_failure|fraud_prevention|dispute_lost|other`).
3. Presentation and delivery: buyer-visible on the billing history page appended to the original receipt (never a standalone edit of history), print-friendly via the shared print CSS, and emailed through the receipts channel. Partial refunds show running remaining balance across multiple notes; the chain stays readable no matter how many partials occur.
4. E-invoice linkage: where a VAT e-invoice exists for the order, the credit note records the corresponding adjustment/replacement document number from the e-invoice flow (same business event, both artifacts cross-referenced) — issuance itself stays in `invoices/vat-einvoices.md`.
5. States: `issued` (terminal, immutable) plus `voided` only via a superseding correction (voids reference their replacement; nothing is ever deleted). Immutability enforced at the application layer + append-only table (no UPDATE path in code).
6. Retention follows order retention; credit notes persist as long as their originals.

## Data / API touches

- New table `credit_notes{id, order_id, seq, amount_vnd, reason, gateway_ref UNIQUE?, einvoice_adjust_ref?, issued_at}` (append-only: INSERT + SELECT only) with standard `user_id` + RLS. Emission is a queued side-effect of each settlement refund handler. All PG via `worker/db/*`.
- Endpoints (owner-scoped reads): surfaced through `GET /api/billing/history` and `GET /api/billing/receipts/:orderCode` (credit notes embedded in the receipt view); no standalone create endpoint — creation happens only via refund handlers and audited ops actions.
- Reason codes enumerated in code (closed set); `other` requires a free-text note stored alongside for audit.

## Acceptance criteria

- Refund drill on three rails (one redirect, one wallet/card, one bank rail): exactly one credit note each, correct links/amounts/references, emails delivered, history chains readable.
- Duplicate-refund-event drill: single credit note, retries are no-ops (gateway-ref + order uniqueness).
- Partial-refund drill (2+ partials): running remaining balance correct on every note; chain order unambiguous.
- E-invoice drill (where enabled): credit note and adjustment document cross-reference each other; originals untouched.
- Immutability probe: no code path UPDATEs or DELETEs a credit note (grep + migration-review gate); voiding only via superseding correction.
- Auth probe: cross-user credit-note access → 403; RLS denies cross-user rows.

## Out of scope

- Refund execution and provider reversal mechanics (each provider's settlement doc); VAT issuance and adjustments (`invoices/vat-einvoices.md`); receipts and history UX (`billing/invoices-receipts.md`); proformas (`invoices/proforma-invoices.md`).
- Accounting/revenue reports; dispute evidence packs (card settlement docs); P4/P5.

## Open questions

- Q1: Buyer-initiated refund requests ("request refund" button creating a pending case) in v1, or ops-only intake?
- Q2: Credit-note localization — same bilingual rule as receipts (their Q1), decided once for both?
