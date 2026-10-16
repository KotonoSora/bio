# Invoices and Receipts - Workflow and User Journey (feature 37)

Phase 1 BA · End-to-end evidence paths. Rules: `docs/01-requirements-ba/37-invoices-and-receipts/02-user-stories-and-business-rules.md`.

## W-1: Grant to receipt email and history

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (34's grant) | Payment verifies; grant commits | Post-commit: `order.granted.v1` publishes and the receipt enqueue fires (idempotent key) | BR-05 |
| 2 | System (pipeline) | Send runs | Receipt email (bilingual) with receipt id, amount, provider ref, print link | BR-01, BR-06, BR-09 |
| 3 | Buyer | Opens print link | HTML receipt in A4 print view; browser print produces the PDF client-side | BR-09; 34's receiptId |
| 4 | Buyer | Opens the history page | 34's order list + per-row receipt links + need-help prefilled with the receipt id | BR-11 |
| 5 | System (pipeline) | Send failed | Backoff retries; backlog visible in ops metrics; grant unaffected | BR-05 |

## W-2: Guest receipt round trip

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Guest buyer | Receives receipt email after product purchase | Link carries the 30-day signed token (no email in the URL) | BR-07 |
| 2 | Guest buyer | Opens the link | Receipt renders; no account required; page is `no-store` | BR-07 |
| 3 | Guest buyer | Token expired (month has passed) | 401 `receipt_link_expired` page with the resend form | BR-07 |
| 4 | Guest buyer | Submits email + Turnstile | Always 202 with a generic "if that matches, we sent it"; match -> fresh link; mismatch -> silent no-op | BR-08 |
| 5 | Guest buyer | Attempts history browsing | No guest history exists; only per-order links | Q-02 |

## W-3: Refund annotation (38 seam, recorded)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (38) | Refund executes | Credit note `CN-<orderCode>-<seq>` issues; credit-note email enqueues; the receipt view gains the linked note section | BR-21; 38 owns |
| 2 | Buyer | Opens the original receipt | Append-only chain renders: original charge + notes + running remaining balance | BR-04, BR-21 |

## W-4: Proforma issue, pay, convert

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Requests a quote (plan+term or product) | Server prices from the active term row / checkout-context freeze; mints `PF-...` with 7-day validity; returns print link | BR-12, BR-13 |
| 2 | Buyer | Downloads and routes the document | Print view shows seller block, line items, amount in figures + words, validity, payment instructions, ref callout, disclaimers | BR-09, BR-17, BR-18 |
| 3 | Buyer | Pays via a rail within validity (quotes `PF-...`) | Settlement re-links by ref (40's flows); online return path carries `proformaRef` at checkout | BR-15; 40 recorded |
| 4 | System (checkout create) | Ref resolves: open + amount equal | Order links the proforma; charge continues | BR-15 |
| 5 | System (34's grant) | Payment verifies | Same transaction flips the proforma to `converted` + `proforma.converted` audit | BR-15 |
| 6 | Buyer | Requests a second quote for the same scope while one is open | The existing open document is returned (200); no duplicate | BR-14 |

## W-5: Price drift and expiry

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Catalog price changes while a proforma is open | Nothing touches the proforma yet; the amount stays locked to its catalog version | BR-12 |
| 2 | Buyer | Checks out with `proformaRef` after the change | Amounts differ -> proforma superseded at create, `proformaRepriced=true`, checkout charges the live amount with a visible notice | BR-15 |
| 3 | Buyer | Reissues | One click issues a fresh proforma at the current price (old one stays as history) | BR-14 |
| 4 | System (job) | `expires_at` passes with status still `open` | Daily audited pass flips `open -> expired`; converts nothing | BR-13 |
| 5 | Buyer/support | Reads a lapsed proforma | Readable history; "expired" state; reissue path offered | BR-13 |
