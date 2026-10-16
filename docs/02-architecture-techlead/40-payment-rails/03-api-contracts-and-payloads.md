# Payment Rails - API Contracts and Payloads (feature 40)

Phase 2 TL · Rail-facing intake, wallet charge, and ops contracts. Auth/rate rules: `docs/02-architecture-techlead/40-payment-rails/05-security-logging-and-rate-limiting.md`. Ops problems are RFC 9457; rails get frozen contract values (below). Registry rows land in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`.

## Intake endpoints (rail-facing; verified; nothing read pre-verification)

| Method & path | Verify (D-1) | Success (after durable) | Validation-final refusal | Transient |
|---|---|---|---|---|
| `POST /webhooks/payos` | HMAC-SHA256 over canonical field string | `200 {success:true}` | `200` durable no-op + metric | `5xx` |
| `POST /webhooks/sepay` | HMAC-SHA256 required (other modes refused) | `200` | `200` + exception row | `5xx` |
| `GET /webhooks/vnpay` | HMAC-SHA512 over the query (`vnp_SecureHash`) | `RspCode 00` | `01` unknown, `04` amount, `02` duplicate no-op | `99` |
| `POST /webhooks/zalopay` | HMAC-SHA256 key2 over raw `data` | `return_code 1` | `2` | `3` |
| `POST /webhooks/napas` | short-lived bearer token (server-refreshed) | `200 {"code":"00"}` | `200 {"code":"00"}` + exception row | `5xx` |
| `POST /webhooks/stripe` | `Stripe-Signature` t+v1 over exact raw bytes; tolerance enforced | `200` | `200` durable no-op + metric | `5xx` |
| `POST /webhooks/googlepay`, `/webhooks/applepay` | PSP webhook scheme (per PSP docs) | `200` | `200` durable no-op + metric | `5xx` |

Verification failures: payos/sepay/napas `401`; stripe `400`; vnpay `RspCode 97`; zalopay `return_code -1`. Refusal reasons never leak to rails or buyers (BR-24).

## Wallet charge endpoints (buyer; session + CSRF)

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `POST /api/v1/billing/applepay/validate-merchant` | `AppleMerchantSessionRequest` | `200 {merchantSession}` (single-use, minutes TTL) | 401; 502 `charge_unavailable`; 429 |
| `POST /api/v1/billing/googlepay/authorize`, `/applepay/authorize` | `WalletAuthorizeRequest` | `200 WalletAuthorizeView` (`paid` on PSP confirmation; `authorizing` on ambiguous timeout) | 404; 409 `order_not_chargeable`; 422 `wallet_token_invalid`; 502 `charge_unavailable`; 429 |

`orderCode` is the PSP idempotency key; charges serialize per order (row lock). A repeat authorize while `authorizing` returns the current view — never a second charge; recovery is the PSP status query (D-6).

## Ops endpoints (restricted role; reason + audit on every mutation)

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `POST /api/v1/ops/rails/{provider}/verify` | `{orderCode}` | `200 VerifyResultView` | 404; 409 `order_not_verifiable`; 502 `verify_unavailable`; 403; 429 |
| `GET /api/v1/ops/rails/exceptions`, `/disputes` (`?status=&cursor=`) | - | `200 ExceptionPage` / `DisputePage` | 403; 429 |
| `POST /api/v1/ops/rails/exceptions/{id}/link` | `ExceptionLinkRequest` | `200 ExceptionView` | 404 `exception_not_found`; 409 `exception_conflict`, `grace_expired`; 422 `validation_failed`; 403; 429 |
| `POST /api/v1/ops/rails/exceptions/{id}/refund-record` | `ExceptionRefundRequest` | `200 ExceptionView` | 404; 409 `exception_conflict`; 422; 403; 429 |
| `POST /api/v1/ops/rails/exceptions/{id}/dismiss` | `{reason}` | `200 ExceptionView` | 404; 409; 403; 429 |
| `POST /api/v1/ops/rails/disputes/{id}/evidence` | `EvidenceSubmitRequest` | `200 DisputeView` | 404 `dispute_not_found`; 409 `dispute_conflict`; 422 `evidence_required`; 403; 429 |

Kill-switch flips reuse 32's config route; sweeps have no route (scheduler only).

```kotlin
data class AppleMerchantSessionRequest(val validationUrl: String)
data class WalletAuthorizeRequest(val orderCode: Long, val paymentToken: JsonNode,
  val idempotencyKey: String? = null)
data class WalletAuthorizeView(val orderCode: Long, val status: String) // paid|authorizing|failed
data class VerifyResultView(val outcome: String, val orderStatus: String)
data class ExceptionView(val id: String, val provider: String, val railTxnRef: String,
  val amountVnd: Long, val reason: String, val status: String, val linkedOrderCode: Long?,
  val graceEndsAt: Instant?, val openedAt: Instant)
data class ExceptionLinkRequest(val orderCode: Long, val reason: String)
data class ExceptionRefundRequest(val resolutionRef: String, val secondOperator: String, val reason: String)
data class DisputeView(val id: String, val provider: String, val orderCode: Long, val status: String,
  val deadlineAt: Instant?, val lossVnd: Long?, val evidenceRef: String?)
data class EvidenceSubmitRequest(val evidenceRef: String)
```

## Rules

- Intake pipeline order is fixed: verify → insert event → resolve order → assert amount → guard edge → respond per contract; bank-rail binding failures open exception rows instead of refusing (the rail must not retry what only ops can fix).
- Redirect rails: return/browser paths are UX-only; only IPN/callback settles. Wallet rails: the synchronous authorize verdict grants; PSP webhooks confirm/audit and are duplicate-safe (Q-05).
- Disputes: every provider dispute event maps through the case machine; loss applies the guard edge + 38's revocation helper and emits `order.chargeback_reversed.v1` post-commit; win restores `disputed → paid`; freeze blocks further grants while disputed.
- Late grace: `link` allowed while `now ≤ expiry + billing.rails.lateTransferGraceHours`; the guard call carries ops actor + reason; beyond grace only `refund-record`/`dismiss`.
- Sweeps: 15-min tick, stale > `sweepStaleMinutes`, per-rail batch ≤ 200; payos link info, vnpay queryDr, zalopay order query, stripe retrieve, napas check-transaction, wallet PSP status; zero state changes outside the guard; reruns idempotent.
- Kill switch: delists new checkouts (33) and refuses new wallet charges; intake, sweeps, verifies, and refunds continue (BR-13).
- Events: `order.chargeback_reversed.v1` frozen `{orderId, disputeRef, reversedAt}` (32's catalog row); emitted once behind the case status guard; consumers 23/24 invalidate via their refund path.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `order_not_chargeable` | 409 | Sheet failure; no retry loop |
| `wallet_token_invalid` | 422 | Sheet failure; one retry allowed |
| `charge_unavailable` | 502 | Honest failure; order stays recoverable |
| `order_not_verifiable` | 409 | Ops: nothing to verify (promo, never handed off) |
| `verify_unavailable` | 502 | Ops: rail unreachable — retry |
| `exception_not_found` / `dispute_not_found` | 404 | Console refresh |
| `exception_conflict` / `dispute_conflict` | 409 | Already resolved — refresh |
| `grace_expired` | 409 | Grace elapsed — refund path only |
| `evidence_required` | 422 | Evidence reference required |

Reused: `throttled`, `billing_ops_required`, `validation_failed`, `amount_changed` (33), `unknown_provider` (33).
