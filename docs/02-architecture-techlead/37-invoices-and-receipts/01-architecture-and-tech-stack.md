# Invoices and Receipts - Architecture and Tech Stack (feature 37)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/37-invoices-and-receipts/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| Receipt + proforma rendering, guest tokens + resend, proforma lifecycle (issue/list/convert/expire), seller/validity config keys, amounts-in-words, the receipts email enqueue | Orders + provider refs + grant timestamps -> 34; ledger/term titles -> 35; plan pricing -> 35's active rows; product price freeze -> 22's `checkout-context`; transactional send -> 32's pipeline; recipient email -> 01's internal read; credit notes (rendering only) -> 38 (recorded) |

No new service. V4 is the chain's fourth migration; refunds/promos/rails shift to V5/V6/V7 (doc-level renumber — nothing beyond V3 has landed; `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md` rows updated with this feature).

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Receipts are views, not rows: composed live from `orders` + `subscriptions`/`plan_terms` + provider references; no `receipts` table exists (single-DDL-owner grep holds) | Legacy's own v1 call; rows would duplicate money truth and drift |
| D-2 | `V4__proformas.sql`: `proformas` (target plan|product, locked `amount_vnd`, `ref` unique, statuses `open|converted|superseded|expired`) + `orders.proforma_id`; RLS owner/ops; grants SELECT/INSERT/UPDATE | The only new persistence in this feature |
| D-3 | Print surface: server-rendered HTML + A4 print CSS + a nonce'd inline print button; `Cache-Control: no-store`; EN+VI stacked; frozen disclaimer module (one source constant) | Platform rule: PDF is always `window.print()`; no server PDF engine exists |
| D-4 | Email wiring: 34's post-commit emission also enqueues the receipt (in-process, idempotency key `rcpt:{orderCode}`, template `billing.receipt`); 38 will enqueue `billing.credit_note` on refunds; sends ride 32's pipeline (backoff, backlog metrics); money paths never wait | Evidence delivery is a side effect, never a money dependency |
| D-5 | Guest access: `t = hex(HMAC-SHA256(RECEIPT_LINK_KEY, orderCode + "|" + exp))` with `exp` in the query; 30-day validity; constant-time verify; no email inside the URL. Resend: `Turnstile` + always-202 + only-on-match send; owner sessions bypass tokens | Unguessable links without accounts; anti-enumeration by construction |
| D-6 | History page reuses 34's owner list (`GET /api/v1/billing/orders`) + per-row receipt links; no duplicate history endpoint is built | Same data, one owner; duplication would fork status rendering |
| D-7 | Proforma pricing: plans from the active `plan_terms` row (35); products from 22's `checkout-context` freeze; amount snapshotted; one open per (user, plan, term) / (user, product); second request returns the existing document | Locked amounts; no provider calls exist at issuance |
| D-8 | Conversion: `proformaRef` on 33's create links when open + unexpired + amount equal; drift supersedes + flags; 34's grant transaction flips `open -> converted` behind the status guard (first grant wins, replays no-op) | Money truth stays with the money path; conversion is an atomic side-check of the grant |
| D-9 | Proforma expiry: daily `02:20 UTC` pass, own advisory lock, batched, per-row audits (`proforma.expired`), rerun-safe | Mirrors 36's job posture; no coupling to lifecycle state |
| D-10 | VAT e-invoices stay design-only (prerequisites unsigned; no endpoints/tables until an amendment); credit notes = 38's issuance, additive rendering only here; coupons on proformas activate with 39 (recorded seams) | Gated-scope doctrine: never stub unbuilt scope |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/33-checkout-session/02-database-schema-design.md` | Chain forecast renumber (V4 proformas 37; V5 refunds; V6 promos; V7 rails) |
| `docs/02-architecture-techlead/33-checkout-session/03-api-contracts-and-payloads.md` | `CheckoutCreateRequest.proformaRef`; `CheckoutView.proformaRepriced`; rules bullet |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/01-architecture-and-tech-stack.md` | D-3 proforma conversion; D-4 receipt enqueue in the post-commit step |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/02-database-schema-design.md` | Audit-contract line gains `proforma.converted` |
| `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md` | V4 row inserted + renumber; config surfaces: `billing.catalog`, `billing.lifecycle`, `billing.proforma`, `billing.seller`; purge-duty row includes 37 |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | Registry rows: receipts get/resend, proforma routes |
