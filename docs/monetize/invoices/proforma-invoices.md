# Proforma Invoices (pre-payment quotes)

PLAN ref: P3-1 evidence trail (precedes checkout; converts into `orders`). Post-payment proof lives in `billing/invoices-receipts.md`; tax invoices in `invoices/vat-einvoices.md`.

## Purpose

Let buyers produce paperwork BEFORE money moves: a proforma quote (plan/product, exact VND amount, validity window, payment instructions, order reference) they can route through internal approval, then pay against. On bank-transfer rails especially, buyers who need a document to initiate payment otherwise stall at checkout — the proforma removes that stall while binding the eventual transfer to a pre-registered expectation, which also improves match rates in the rail settlement flows.

## Users

- Buyer (especially team/company buyers): downloads a quote, gets approval, then pays the quoted amount within its validity.
- Finance requester: needs vendor details, amounts, and a reference number on company letterhead-style output.
- Support/ops: traces a paid transfer back to its proforma when contents differ slightly.

## Functional requirements

1. Proforma issuance (authenticated, owner-scoped): buyer selects plan/product (+ optional coupon code, priced from the active catalog row per `subscription/plan-catalog.md`) and requests a quote. Server mints `proforma{id, user_id, plan_term_version, amount_vnd, expires_at (default 7 days, see Q1), status: open}` and renders a printable quote (seller details, line items, amount in figures + words, validity, payment instructions per eligible providers, and the `proforma_ref` to quote in transfer contents where rails allow).
2. Conversion to order: paying through any checkout path within validity links the resulting `orders` row to the proforma (`proforma_id`); the quoted amount must equal the charged amount or checkout re-prices and marks the proforma superseded (new proforma issuable in one click). Expired proformas convert nothing and are never grantable.
3. One open proforma per plan/product scope per user (configurable): a second request while one is open returns the existing document with its remaining validity instead of minting duplicates.
4. Proforma is explicitly NOT a tax invoice and NOT a payment demand: carries the same non-VAT disclaimer family as receipts, plus "valid for payment until <date>, prices locked to catalog version <n>".
5. Company-buyer fields (optional at issuance, required before VAT e-invoice): company name, tax code, address — captured once, reused for the later e-invoice (see `invoices/vat-einvoices.md`), validated for format (not for tax-authority truth, which happens at e-invoice issuance).
6. Expiry job: lapsed proformas flip `open → expired` (no money involved, so no settlement interaction); history retained for support lookup.

## Data / API touches

- New table `proformas{id, user_id, ref UNIQUE, plan_term_version, coupon?, amount_vnd, expires_at, status, converted_order_id?}` with standard `user_id` + RLS. `orders` gains nullable `proforma_id`. All PG via `worker/db/*`.
- Endpoints (owner-scoped): `POST /api/billing/proformas`, `GET /api/billing/proformas`, `GET /api/billing/proformas/:ref` (print HTML + print CSS, same pattern as receipts). No provider calls at issuance — provider handoff happens only at conversion checkout.
- Amounts integer VND throughout; amounts-in-words rendered by a single tested formatter (one implementation, shared with receipts).

## Acceptance criteria

- Quote-to-payment drill: issue proforma → pay full quoted amount within validity via two different rails → both orders link the same proforma, single grant each, amounts match to the dong.
- Re-price drill: catalog price changes mid-validity → checkout charges the new amount, proforma marked superseded, one-click reissue offered; no silent over/under-charge.
- Expiry drill: lapsed proforma converts nothing; payment against its ref follows the rail's normal unmatched-transfer flow.
- Duplicate-request drill: second open-scope request returns the existing proforma, no duplicate rows.
- IDOR probe: user A reading user B proformas → 403; RLS denies cross-user rows.

## Out of scope

- Payment confirmations and history (`billing/invoices-receipts.md`); VAT e-invoices (`invoices/vat-einvoices.md`); credit notes (`invoices/credit-notes.md`).
- Catalog versioning itself (`subscription/plan-catalog.md`); provider checkout/settlement mechanics (their own minors); P4/P5.

## Open questions

- Q1: Proforma validity — 7-day default acceptable, or shorter/longer per segment?
- Q2: Proforma PDF delivery — print-CSS only like receipts, or attached PDF via email for approvers outside the account?
