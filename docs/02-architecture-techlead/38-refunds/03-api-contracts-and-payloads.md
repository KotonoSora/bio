# Refunds - API Contracts and Payloads (feature 38)

Phase 2 TL · Browser + ops + internal contracts. Auth/rate rules: `docs/02-architecture-techlead/38-refunds/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. Registry rows land in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/billing/orders/{orderCode}/refund-request` | session owner + CSRF + Turnstile + `Idempotency-Key` | `RefundRequestCreate` | 201 `RefundView` (200 existing open) | 403 (foreign parity); 404 `order_not_found`; 409 `refund_ineligible`, `tip_final`, `refund_conflict`; 422 `challenge_failed`, `validation_failed`, `idempotency_key_reuse`; 429 |
| `GET /api/v1/billing/refunds?cursor=` | session | - | 200 `RefundPage` | 401; 429 |
| `GET /api/v1/billing/refunds/{id}` | session owner | - | 200 `RefundView` | 403/404 `refund_absent` parity; 429 |
| `GET /api/v1/ops/refunds?status=&cursor=` | ops role | - | 200 `RefundPage` | 403 `billing_ops_required`; 429 |
| `POST /api/v1/ops/refunds/{id}/approve` | ops role + CSRF | `{}` | 200 `RefundView` | 403 `billing_ops_required`; 404 `refund_absent`; 409 `refund_not_executable`; 429 |
| `POST /api/v1/ops/refunds/{id}/reject` | ops role + CSRF | `RejectRefundRequest` | 200 `RefundView` | 403; 404; 409 `refund_not_executable`; 429 |
| `POST /api/v1/ops/refunds/{id}/execute` | ops role + CSRF | `ExecuteRefundRequest` | 202 `RefundView` (`executing`; `reversed` when the rail completes inline) | 403 `billing_ops_required`, `two_person_required`; 404; 409 `refund_not_executable`; 422 `evidence_required`; 502 `refund_psp_failed`; 429 |
| `POST /internal/v1/billing/refunds` | service JWT | `InternalRefundCreate` | 201 `RefundView` (approved; auto-executed on API rails) | 401; 404 `order_not_found`; 409 `refund_conflict`; 422 `validation_failed` (non-allowlisted reason, bad idempotency key) |

Consumed (no edits): 34's `OrderDetailView` (eligibility input), 32's `POST /internal/v1/notifications` (`billing.credit_note`), 37's render contract for the credit-note chain.

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class RefundRequestCreate(val reason: String, val note: String? = null)
data class RejectRefundRequest(val reason: String)
data class ExecuteRefundRequest(val evidenceRef: String? = null)   // required for manual rails
data class InternalRefundCreate(val orderCode: Long, val reasonCode: String,
  val sourceRef: String, val idempotencyKey: String)
data class RefundView(val id: String, val orderCode: Long, val status: String,
  val amountVnd: Long, val provider: String, val reasonCode: String, val initiatedBy: String,
  val requestedAt: Instant, val pspRef: String?, val creditNoteRef: String?)
data class RefundPage(val items: List<RefundView>, val nextCursor: String?)
```

## Rules

- Eligibility: `paid` only; refusals carry the terminal state in `detail`; `tip` kind -> `tip_final`; `provider='promo'` orders (0 VND) -> `refund_ineligible` (nothing charged; no rail exists to reverse); `disputed|chargeback_reversed` -> dispute copy, no row; one row per order ever (`rejected` is terminal — later requests `refund_conflict`; ops routes handle true exceptions out of band).
- Request idempotency: the client `Idempotency-Key` is stored `UNIQUE`; the same key returns the same row; a different key while an open row exists returns 200 with that row.
- Approval: `requested → approved|rejected` under row lock; actor recorded; reject requires a reason; nothing touches the order at approval.
- Execution: dispatch on the order's stored `provider` (no default; unknown -> reject + alert). Manual rails require `evidenceRef` (422 without) and executor ≠ approver (403 otherwise). `approved → executing` commits first; the strategy runs; success lands the final transaction: order `paid → refunded` through 34's guard, row `reversed` + `psp_ref` + `executed_at`, credit-note insert, revocation (D-6), `refund.emitted` marker. Failure -> `failed` + `failure_reason` + alert + backoff retry (≤ 5 attempts / ≤ 24 h); rails without PSP idempotency keys query-first.
- Amount: asserted `== orders.amount_vnd` before `executing` and inside the final transaction; mismatch = alert, no transition.
- Revocation: plan orders — origin/extension match closes the row `expired` + free floor + `plan.refunded`; no match = no change. Product orders — event only. Re-grant of a `refunded` order is refused by 34's guard (terminal).
- Event: `order.refunded.v1` published post-commit to `billing.events` with 32's frozen shape `{orderId, refundId, refundedAt}`; `refund.emitted` audit marker; reconciliation re-emits marker-less `reversed` rows once; consumers 23/24/25 dedupe by refundId.
- Credit note: `note_ref = CN-<orderCode>-1`; email `billing.credit_note` enqueued post-commit (key `cn:{refundId}`, transactional class, recipient per 37's rule); 37 renders it inside the receipt.
- Internal route: allowlisted reasons only (`cancel_ge_24h|creator_cancel|hold_expired`); rows enter `approved` (`initiated_by = system`, `approved_by = system`); API rails auto-execute; manual rails queue for ops with evidence (two-person still applies); `sourceRef` stores the booking/policy id for support.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `refund_ineligible` | 409 | Terminal-state copy on the order detail |
| `tip_final` | 409 | "Tips are final" copy (25's line) |
| `refund_conflict` | 409 | Existing decision notice; support link |
| `refund_absent` | 404 | Back to the refund list |
| `refund_not_executable` | 409 | Ops row refreshes to current state |
| `evidence_required` | 422 | Ops evidence field flagged |
| `two_person_required` | 403 | Ops pair notice |
| `refund_psp_failed` | 502 | Tracker shows "processing — retrying" |

Reused: `order_not_found` (34), `billing_ops_required` (35), `challenge_failed` (06), `idempotency_key_reuse` (33), `validation_failed`, `throttled`.

```json
{ "type": "https://api.example.com/problems/tip_final", "title": "Tips are final",
  "status": 409, "code": "tip_final", "detail": "Tips are final — thanks for supporting creators." }
```
