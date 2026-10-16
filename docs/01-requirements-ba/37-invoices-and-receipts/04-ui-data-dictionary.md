# Invoices and Receipts - UI Data Dictionary (feature 37)

Phase 1 BA · Surfaces: billing history page, receipt print view, proforma print view + issue dialog, guest expired-link page. All values server-rendered; nothing here is client-computed.

## Billing history page (`/app/billing/history`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `rows` | List | Yes | Composed from 34's order list; newest first; cursor-paginated | Empty state: "No payments yet" + pricing link |
| `date` | Text | Yes | UTC instant rendered local | - |
| `title` | Text | Yes | Plan term or product title from the line items | - |
| `amountVnd` | Text | Yes | Integer VND with grouping; no decimals exist | - |
| `status` | Badge | Yes | Verbatim 34 status values (`pending`..`chargeback_reversed`) | - |
| `receiptLink` | Link | When money-confirmed | Opens the receipt print view; absent otherwise | `receipt_unavailable` states render as pending copy |
| `creditNotes` | Inline list | When 38 ships | Notes render inside the receipt, not on the row | - |
| `needHelp` | Link | Yes | Prefills receipt id into support | - |

## Receipt print view (HTML, `no-store`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `receiptId` | Heading | Yes | `RCPT-<orderCode>` frozen | Always shown |
| `buyerIdentifier` | Text | Yes | Owner email or guest `buyerEmail`; masked on shared prints? No - it is the buyer's own copy | - |
| `lineItems` | Table | Yes | Title, term, quantity, amount | - |
| `amountVnd` | Figure + words | Yes | Integer + formatter output (EN+VI blocks) | - |
| `providerRef` | Text | When captured | Rail txn reference; empty section hidden | - |
| `grantAt` | Timestamp | Yes | Canonical grant audit time | - |
| `disclaimer` | Footer | Yes | Frozen verbatim EN+VI non-VAT copy | Never editable |
| `creditNoteSection` | Table | When present (38) | Linked notes + running remaining balance | Append-only |
| `printButton` | Button | Yes | `window.print()`; A4 CSS | Hidden in print media |

## Proforma print view (HTML, `no-store`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `ref` | Heading | Yes | `PF-` + 8 unambiguous chars; transfer-memo safe | Shown with a copy button |
| `sellerBlock` | Card | Yes | From `billing.seller` config; absent config = page never renders (409 at issuance) | - |
| `buyerBlock` | Card | Yes | Account name/email; optional company fields when provided | - |
| `lineItems` / `amountVnd` | Table + words | Yes | Locked amount + catalog version note ("prices locked to catalog version {n}") | - |
| `validity` | Banner | Yes | "Valid for payment until {expires_at}"; expired state replaces with "expired - reissue" | - |
| `payInstructions` | Block | Yes | Eligible provider instructions per 33's methods read; ref callout emphasized | - |
| `disclaimers` | Footer | Yes | Not-a-tax-invoice + not-a-payment-demand copy, EN+VI | - |

## Proforma issue dialog

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `target` | Selector | Yes | Plan + term list (from active terms) or one of the buyer's purchasable products | - |
| `issueButton` | Primary button | Yes | 201 -> print view offered; 200 (existing open) -> opens that document; 409 `seller_profile_absent` -> honest unavailable copy | - |
| `openNotice` | Notice | When an open doc exists | "You already have an open quote - showing it" | - |

## Guest expired-link page

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `message` | Text | Yes | Neutral copy; never confirms order existence beyond a valid token | - |
| `emailInput` | Email input | Yes | Format-validated client+server | - |
| `turnstile` | Widget | Yes | `challenge_failed` renders inline | - |
| `resendSubmit` | Primary button | Yes | Always-202 generic confirmation | - |
