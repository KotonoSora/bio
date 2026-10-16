# Invoices and Receipts - Security, Logging, and Rate Limiting (feature 37)

Phase 2 TL · BA sources: `docs/01-requirements-ba/37-invoices-and-receipts/` (NFR-04..NFR-16, BR-07/08). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: receipts/proformas resolve from the session's `user_id` (RLS + server-set); guest tokens are the only token surface; cross-user probes return 403/404 parity with 34, never existence | AC-03; RLS probes |
| S-2 | Token crypto: HMAC-SHA256 over `orderCode|exp`, key from env/vault only (`RECEIPT_LINK_KEY`), constant-time verify, 30-day expiry, single-purpose (no other route accepts `t`); tokens never logged, never echoed in errors | Tamper/expiry drills; log grep |
| S-3 | Render hygiene: `no-store` on both HTML surfaces; CSP with a nonce for the sole print-button script, no other scripts/styles beyond inline print CSS; amounts and refs escaped as text; PII limited to the buyer's own document | Header tests; XSS probes |
| S-4 | Anti-enumeration resend: Turnstile required, always 202, generic copy, match-only send, constant-work comparison so timing reveals nothing; mismatches metered | AC-04; timing probe |
| S-5 | No sensitive data: zero card/bank/PAN fields exist anywhere in the domain; templates grep clean each build; provider references are transaction ids only, never instrument data | AC-12; CI grep |
| S-6 | Config integrity: `billing.seller` validated (non-empty legalName/address; taxCode format-loose); `validityDays` 1..30; flips via 32's audited ops route; invalid flips refused with last-good retained | Flip refusal tests |
| S-7 | Proforma refs: crypto-random, unambiguous alphabet, unique-checked; unguessable and non-sequential; transfer-memo quoting never grants state by itself (matching is a 40-concern with order-side checks) | Ref drills; 40's matching recorded |
| S-8 | Job safety: expiry pass advisory-locked, batched, per-row transactions, status-guarded; a failing row skips + meters and retries next run | Rerun drill |
| S-9 | Email integrity: sends ride 32's pipeline (transactional class); recipient only from 01's read or the order's own `buyerEmail`; masked logging; `marketing_not_accepted` boundary untouched | Pipeline contract tests |
| S-10 | Secret hygiene: `RECEIPT_LINK_KEY` and any seller-email credentials via environment/vault; rotating the key invalidates old links (documented; resend mints new) | Rotation drill |

## Rate limits (gateway rules; Redis-backed counters)

| Route | Limit | On breach |
|---|---|---|
| `POST /api/v1/billing/receipts/{orderCode}/resend` | 3/h/order + 5/h/IP | 429 `throttled` + `Retry-After` |
| `GET /api/v1/billing/receipts/{orderCode}` | 60/h/user (owner); 30/h/token | 429 `throttled` |
| `POST /api/v1/billing/proformas` | 10/h/user + 40/day/user | 429 `throttled` |
| `GET /api/v1/billing/proformas*` | 120/h/user | 429 `throttled` |
| Proforma expiry pass | pw. job pacing (<= 500 rows/run) | - |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Email pipeline down | Enqueues retry with backoff; backlog metric; grants and renders unaffected |
| Feature 01 email seam down | Owner receipts skip + metric; guest receipts (order's own email) still send; print links remain valid |
| Product-price seam (22) down | Product proforma issuance fails closed with the seam's passthrough error; no document is minted; plan proformas unaffected |
| `billing.seller` unset | Issuance 409; no placeholder ever renders |
| Expiry job down | Proformas stay `open` past `expires_at` but conversion checks expiry by timestamp, so none converts; next run heals statuses |
| Key rotation | Old tokens 401 -> resend path; owners unaffected |
| `billing-service` down | All surfaces 503; history page's data also unavailable (34's reads) |

## Audit and observability

Audits (24-month spine): `proforma.created`, `proforma.superseded`, `proforma.converted`, `proforma.expired`; receipt sends logged per-send (masked recipient, template, orderCode, result). Metrics: `receipt_sent`, `receipt_notify_failed`, `receipt_token_rejected`, `resend_mismatch`, `proforma.created/superseded/converted/expired`, `proforma_expiry_run` duration. No tokens, no full emails, no amounts in log labels.

## Support answers

"Which receipt covers this dispute?" -> `RCPT-<orderCode>` on the order trace (32's trace includes the receipt list). "Why did this quote not convert?" -> proforma status + `proforma.superseded/expired` audit with the catalog version. "Why no receipt email?" -> pipeline per-send log + `receipt_notify_failed` metric.
