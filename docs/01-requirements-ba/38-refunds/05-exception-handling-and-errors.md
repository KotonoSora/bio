# Refunds - Exception Handling and Errors (feature 38)

Phase 1 BA · Failure and edge behavior. Rules: `docs/01-requirements-ba/38-refunds/02-user-stories-and-business-rules.md`. Codes follow RFC 9457; exact JSON in `docs/02-architecture-techlead/38-refunds/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Request for a non-`paid` order | Error | 409 `refund_ineligible`; detail names the terminal state | "This order can't be refunded ({state})." |
| Request for a `tip` order | Error | 409 `tip_final` | "Tips are final — thanks for supporting creators." |
| Refund request for a promo-granted order (`provider='promo'`, 0 VND) | Error | 409 `refund_ineligible`; nothing charged, no rail exists to reverse; coupon unwinding stays 39's design seam (BR-22) | "Promotional credits can't be refunded — nothing was charged." |
| Request for a `disputed` order | Error | Routed to dispute copy; no row minted | "A dispute is in progress for this order." |
| Second request while one is open | Happy edge | 200 with the existing row | - (tracker opens) |
| New request after a terminal row | Error | 409 `refund_conflict` | "A refund decision already exists for this order." |
| Double-click / retry with the same key | Edge | Durable no-op; same row returned | - |
| Approve/reject/execute on a wrong-state row | Error | 409 `refund_not_executable` | "That action isn't available for this refund." |
| Execute a manual rail without evidence | Error | 422 `evidence_required`; nothing transitions | "Record the reversal transfer reference first." |
| Executor equals approver on a manual rail | Error | 403 `two_person_required`; audit records the attempt | "A second person must execute this refund." |
| PSP refund call declines (rail's API) | Degrade | `executing → failed` + alert; backoff retry ≤ 5 attempts / ≤ 24 h | "Processing — we're retrying with the payment provider." |
| PSP timeout after the reversal possibly succeeded | Edge | Query-first on retry (rails with query APIs); PSP idempotency key where supported; never a blind second call | - |
| Amount mismatch at final assertion | Error | No transition; alert; row `failed` with `amount_mismatch` detail | "Processing — support is verifying." |
| Concurrent execute attempts | Edge | Row lock + status guard: one wins, others no-op | - |
| Event publish fails after reversal commits | Degrade | Marker-less row re-emitted by reconciliation once; consumers idempotent | - (claims heal on redelivery) |
| Plan row already closed (e.g., refund after expiry) | Edge | Revocation matcher finds no current row; audit notes no-op; credit note still issues | - |
| Refund of a superseded/older plan grant | Edge | No entitlement change (BR-11); buyer keeps current service | - |
| Internal auto-refund with a non-allowlisted reason | Error | 422 `validation_failed`; nothing minted | - (service caller logs) |
| Internal auto-refund duplicate key | Edge | Idempotent: same row returned | - |
| Ops queue read by non-ops role | Error | 403 `billing_ops_required` | "You don't have access to refund operations." |
| Cross-user refund read/request (IDOR) | Error | 403/404 parity with 34; RLS denies | "We couldn't find that refund." |
| Refund request burst | Error | 429 `throttled` + `Retry-After` | "Too many requests — try again later." |
