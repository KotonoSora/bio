# Invoices and Receipts - User Stories and Business Rules (feature 37)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/37-invoices-and-receipts/`. Scope: `docs/01-requirements-ba/37-invoices-and-receipts/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | receive a receipt email on every paid grant and re-download it anytime | I have one trustworthy billing history for reimbursement or tax filing | High |
| US-02 | Guest buyer | open my receipt from the email link without creating an account | proof of payment is never locked behind signup | High |
| US-03 | Support/ops | cite one receipt id per dispute | I stop reconstructing events across eight dashboards | High |
| US-04 | Team/company buyer | download a quote, get approval, then pay the quoted amount | paperwork can precede money | Medium |
| US-05 | Finance/ops | control the seller block and quote validity from config | issued paperwork is always truthful | Medium |
| US-06 | Finance | book reversals against originals via linked credit notes | chains stay readable across partial refunds | Medium |
| US-07 | Reviewer | prove no card data, no placeholder letterhead, and verbatim disclaimers | every artifact is checkable | High |

## Business rules

- **BR-01 Receipt identity.** `receiptId = RCPT-<orderCode>`, stable across renders, uniform across all eight rails; order codes are non-sequential int64 (32's kernel). No second identifier exists.
- **BR-02 Receipt content.** Buyer identifier (account email for owners, `buyerEmail` for guests), line items (plan term or product title), `amount_vnd` integer, provider name + provider reference (rail txn ref where captured), grant timestamp (34's canonical grant audit), support contact, and amounts-in-words. No card, bank, or PAN data ever appears.
- **BR-03 Receipt exists only for money-confirmed states.** `paid|refunded|disputed|chargeback_reversed` render; anything else -> 409 `receipt_unavailable` (34's `receiptId` rule).
- **BR-04 No receipt table.** Receipts render on demand from orders + ledger rows + provider references (+ 38's credit notes once shipped); reads are owner-scoped; nothing about a receipt is mutable.
- **BR-05 Email at grant and refund.** The grant path enqueues the receipt email post-commit (`rcpt:{orderCode}` idempotency key); refunds enqueue a credit-note email when 38 ships. Sends are `transactional` class: unsubscribed-from-marketing buyers still receive them. Enqueue failures never block or roll back money transitions.
- **BR-06 Recipient resolution.** Account owners via feature 01's internal email read; guests via the order's `buyerEmail`; unresolved recipient = skip + metric, never a guessed address.
- **BR-07 Guest magic link.** The receipt email carries a 30-day signed link (no email address inside the URL); possession of a valid token grants read of exactly that order's receipt. Expired/invalid token -> 401 `receipt_link_expired` with a request-new-link path. Owners use their session and need no token.
- **BR-08 Resend flow.** `POST .../resend` takes an email + Turnstile and always returns 202; a send happens only when the email matches the order's buyer email (case-insensitive); rate limits apply. No response ever reveals whether a match occurred.
- **BR-09 Print model.** Receipt and proforma views are HTML + print CSS; the client's `window.print()` produces A4; no server PDF exists. Pages are `no-store`, bilingual EN+VI, and carry the frozen disclaimer verbatim: "Payment confirmation - not a VAT invoice (hóa đơn GTGT)." / "Xác nhận thanh toán - không phải hóa đơn GTGT."
- **BR-10 Amounts-in-words.** One formatter (tested table: 0, 15, 100, 1 000, 1 000 000, 999 999 999 + random) renders VND words in both languages; receipts and proformas share it.
- **BR-11 History.** The billing history page composes 34's owner order list with per-row receipt links and "need help" (receipt id prefilled); statuses render verbatim from 34; refund annotations appear inside receipts once 38 ships.
- **BR-12 Proforma issuance.** Authenticated owner requests a quote for a plan+term (priced from the active term row) or a product (priced via 22's `checkout-context` freeze); the amount is snapshotted and immutable; `ref = PF-` + 8 unambiguous chars (no 0/O/1/I, transfer-memo safe).
- **BR-13 Validity.** `expires_at = issued + validityDays` (config, default 7, 1..30); a daily audited pass flips `open -> expired` after lapse; expired proformas convert nothing and stay readable for support.
- **BR-14 One open per scope.** One open proforma per (user, plan, term) and per (user, product); a second request returns the existing open document (200), never a duplicate.
- **BR-15 Conversion.** Checkout create with `proformaRef`: open + unexpired + amount equal -> the order links it; amount drift -> the proforma is superseded at create and the response flags the reprice (the charged amount is always the live catalog amount - no silent over/under-charge); foreign/absent/expired refs are ignored with no enumeration. The grant transaction flips the linked proforma to `converted` (first grant wins; replays are no-ops).
- **BR-16 Multiple orders may reference one proforma** (rail-switch attempts): each order grants on its own; exactly one conversion exists; later orders proceed normally.
- **BR-17 Disclaimers.** A proforma states "not a tax invoice, not a payment demand", its validity window, and the catalog version the amount was locked to; receipts carry the Q-09 disclaimer. Company-buyer fields (name, tax code, address) are optional on issuance and feed the gated VAT flow later.
- **BR-18 Seller block.** Rendered from `billing.seller` config only; when absent, issuance returns 409 `seller_profile_absent` (no placeholder letterhead ever renders).
- **BR-19 Coupons** on proformas activate with 39 (recorded seam; no coupon field exists yet).
- **BR-20 VAT e-invoices are design-only**: prerequisites (operating-entity tax registration, contracted solution, series registration) must sign off first; no endpoints or tables ship until then; the receipts' non-VAT footer is the v1 contract.
- **BR-21 Credit notes** are issued by 38 only; this feature renders them inside receipts additively; nothing in 37 mints them.
- **BR-22 Retention.** Receipts follow order retention; proformas persist for support lookup; PII scrubbed per 02's roster; `no-store` on every render.
