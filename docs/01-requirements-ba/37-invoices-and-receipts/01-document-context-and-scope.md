# Invoices and Receipts - Document Context and Scope (feature 37)

Phase 1 BA · Feature 37 of 53 — monetize 6/9: the proof-of-payment layer — receipts, billing history, proformas, and the recorded VAT/credit-note seams. Sources: `monetize/billing/invoices-receipts.md`, `monetize/invoices/{proforma-invoices,vat-einvoices,credit-notes}.md`. Implemented by `docs/02-architecture-techlead/37-invoices-and-receipts/`.

## Context

Money truth already lives in one place (32-36); this feature proves it to the buyer. Receipts render on demand from order, ledger, and provider-reference rows — no receipt table exists (legacy's own v1 decision). Proformas let buyers produce paperwork before money moves, binding bank-transfer payments to a pre-registered expectation. VAT e-invoices stay design-only until their legal prerequisites sign off; credit-note issuance belongs to feature 38, and this feature only renders them.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One trustworthy billing history: every grant and refund visible with stable ids, matching amounts, and provider references |
| OBJ-02 | Receipt email on every paid grant within the delivery budget; re-downloadable anytime by owner or guest magic link |
| OBJ-03 | Uniform receipt content across all eight rails; zero card/bank data ever appears |
| OBJ-04 | Print path = HTML + print CSS + `window.print()` A4; no server PDF engine exists |
| OBJ-05 | Proformas: quote → pay → convert with locked amounts, one open document per scope, audited expiry |
| OBJ-06 | Honest paperwork: the non-VAT disclaimer is verbatim on every artifact; proformas are never payment demands |
| OBJ-07 | Guest receipts without accounts: magic-link read, anti-enumeration resend, no guest history |
| OBJ-08 | Append-only corrections: credit notes link to originals; nothing is ever edited (38's execution, this feature's rendering) |

## Scope

In scope (SC-01..SC-08): receipt rendering + email, billing history composition, guest magic-link access + resend, proformas (issue/list/print/convert/expire), seller block config, amounts-in-words formatter, bilingual EN+VI rendering, the frozen disclaimer copy.

Out of scope: refund execution and credit-note issuance (38); coupons on proformas (39 activates the seam); rail settlement and unmatched-transfer flows (40); VAT e-invoice issuance (gated design, below); marketing campaigns (gated major); accounting/revenue reports; any server-side PDF generation.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer (account) | R: own history + receipts + proformas; C: proformas | One trustworthy history; re-download or print anytime |
| Guest buyer | R: own receipt via magic link only | No account, no history page; the email link is the key |
| Finance/ops | R: seller + validity config | Sets the legal entity block and proforma validity; no receipt edits exist |
| Support | R: cite receipt/credit-note ids | One artifact per dispute instead of eight dashboards |
| System (grant/refund paths) | C: receipt/credit-note emails | Enqueue-only; never blocks money transitions |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Receipt localization (receipts Q1) | Bilingual EN+VI on every artifact (receipts, proformas, emails); one template, two stacked language blocks |
| Q-02 | Guest receipts (receipts Q2) | Magic-link read in v1 (30-day signed link, no account needed); guest history stays out; owners need no token |
| Q-03 | Proforma validity (proformas Q1) | `ops_config` `billing.proforma.validityDays`, default 7, validated 1..30 |
| Q-04 | Proforma PDF delivery (proformas Q2) | Print-CSS only, same as receipts; no email attachments exist |
| Q-05 | Proforma scope | Plans and products both price from existing seams (35's active term row; 22's `checkout-context` freeze); coupon line activates with 39 (recorded) |
| Q-06 | Refund intake button (credit-notes Q1) | Out of 37; 38 defines intake; this feature only renders resulting credit notes |
| Q-07 | Credit-note localization (credit-notes Q2) | Same bilingual rule as receipts (Q-01), decided once here |
| Q-08 | VAT prerequisites (vat Q1/Q2) | Design-only recording: issuance stays gated until tax registration + contracted solution + series registration sign off; no endpoints or tables ship before that |
| Q-09 | Seller block | `ops_config` `billing.seller` {legalName, address, taxCode?, email}; absent -> proforma issuance refused (409), never a placeholder letterhead |
| Q-10 | Amounts in words | One tested formatter shared by receipts and proformas; VND integers only |

## Constraints carried

Receipt ids stable and non-sequential (`RCPT-<orderCode>`; order codes are 32's non-sequential int64); reads owner-scoped via RLS; guest tokens are the only token surface; no marketing sends from this feature; retention follows orders (02's roster); print artifacts never cache (`no-store`).
