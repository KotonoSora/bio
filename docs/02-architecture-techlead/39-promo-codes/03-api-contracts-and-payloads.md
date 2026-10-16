# Promo Codes - API Contracts and Payloads (feature 39)

Phase 2 TL · Buyer + ops contracts. Auth/rate rules: `docs/02-architecture-techlead/39-promo-codes/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. Registry rows land in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/billing/promo/validate` | session or guest + Turnstile | `PromoValidateRequest` | 200 `PromoQuoteView` | 422 `coupon_invalid`, `coupon_expired`, `challenge_failed`, `validation_failed`; 429 |
| `GET /api/v1/billing/credits` | session | - | 200 `CreditsView` | 401; 429 |
| `GET /api/v1/ops/promo-campaigns?status=&cursor=` | ops role | - | 200 `CampaignPage` | 403 `billing_ops_required`; 429 |
| `POST /api/v1/ops/promo-campaigns` | ops role + CSRF | `CampaignCreate` | 201 `CampaignView` | 422 `campaign_invalid`; 403; 429 |
| `POST /api/v1/ops/promo-campaigns/{id}/launch\|pause\|resume\|end` | ops role + CSRF | `{reason?}` | 200 `CampaignView` | 409 `campaign_conflict`; 404; 403; 429 |
| `GET /api/v1/ops/promo-campaigns/{id}/burndown` | ops role | - | 200 `BurndownView` | 404; 403; 429 |
| `POST /api/v1/ops/coupon-batches` | ops role + CSRF | `BatchCreate` | 201 `BatchExportView` (plaintext, once) | 422 `batch_invalid`, `campaign_invalid`; 403; 429 |
| `POST /api/v1/ops/coupon-batches/{id}/ack-export` | ops role + CSRF | `{}` | 200 `{ackedAt}` | 409 `export_acked`; 404; 403; 429 |
| `POST /api/v1/ops/coupon-batches/{id}/void-unredeemed` | ops role + CSRF | `{reason}` | 200 `{voidedCount}` | 404; 403; 429 |
| `GET /api/v1/ops/coupon-batches/{id}/stats` | ops role | - | 200 `BatchStatsView` | 404; 403; 429 |
| `POST /api/v1/ops/credits/goodwill` | ops role + CSRF | `GoodwillIssue` | 201 `{ledgerId, days}` | 409 `promo_config_absent`; 422 `validation_failed`; 403; 429 |
| `GET /api/v1/ops/credits?userId=&cursor=` | ops role | - | 200 `CreditLedgerPage` | 403; 429 |

Consumed (no edits): 33's create (calls the pricing service), 34's grant transaction (coupon confirmation hook), 36's job (three passes), 37's receipt rendering (promo orders render unchanged).

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class PromoValidateRequest(val code: String, val planId: String? = null, val productId: String? = null)
data class PromoQuoteView(val amountVnd: Long, val discountVnd: Long, val kind: String,
  val days: Int? = null, val expiresAt: Instant? = null)
data class CreditsView(val unconsumedDays: Int)
data class CampaignCreate(val name: String, val kindScope: String, val value: Int,
  val budgetCapVnd: Long, val maxRedemptions: Int, val perUserLimit: Int = 1,
  val target: String, val startsAt: Instant, val endsAt: Instant)
data class CampaignView(val id: String, val name: String, val kindScope: String, val value: Int,
  val status: String, val budgetCapVnd: Long, val redeemedVnd: Long, val maxRedemptions: Int,
  val redemptionCount: Int, val startsAt: Instant, val endsAt: Instant)
data class BurndownView(val budgetCapVnd: Long, val redeemedVnd: Long, val remainingVnd: Long,
  val maxRedemptions: Int, val redemptionCount: Int, val burnPct: Int)
data class BatchCreate(val campaignId: String, val channelRef: String? = null, val count: Int,
  val useLimit: Int = 1, val perUserLimit: Int = 1, val expiresAt: Instant, val prefix: String,
  val inviteUserIds: List<String>? = null)
data class BatchExportView(val batchId: String, val prefix: String, val count: Int,
  val codes: List<String>)                       // plaintext exists exactly here, once
data class BatchStatsView(val generated: Int, val active: Int, val redeemed: Int,
  val voided: Int, val expired: Int)
data class GoodwillIssue(val userId: String, val days: Int, val reason: String)
```

## Rules

- Validate: normalize code (uppercase, trim), HMAC lookup, then one evaluation path for window, status, uses, budget, targeting, per-user, invite binding; every failure returns the same neutral `coupon_invalid` except expiry (`coupon_expired`); timing spread <= 50 ms (constant work, no early exits). Quotes are never reservations.
- Price: `percent` -> `floor(amount × value ÷ 100)`; `fixed_vnd` -> `min(value, amount)`; final `max(0, amount − discount)`; `term_extend` -> no discount, `days = value`. The service returns the math; 33 charges the final amount.
- 0-VND: create mints `provider='promo'` + handoff `none` + immediate grant via the canonical amount-checked path (34's guard); clients sending `provider='promo'` get `unknown_provider`; receipts render normally; `order.granted.v1` fires normally.
- Budget: create decrements behind the guarded update (D-4); terminal-without-grant restores exactly once via the order's terminal transition; 100% auto-pauses (system audit); 80% alerts.
- In-flight: a verify of the frozen quoted amount always grants — pause, void, or expiry mid-flight audits `coupon.in_flight_honored`; new validates/creates read status fresh and block within <= 5 s of a pause.
- Grant hook (34's transaction): re-check eligibility; guarded `uses` update (reached limit flips `redeemed`); `term_extend` inserts the `credits_ledger` row; subscription `source='coupon'` + `source_ref = campaign_id` stamped; audits `coupon.redeemed` / `coupon.in_flight_honored`.
- Stacking: one coupon per order (replacement is client-side; server sees exactly one); coupon + trial/credits allowed; renewals re-price and the coupon applies only if its window still covers.
- Ops lifecycle: create validates window/percent/value bounds; `launch` from `draft|scheduled`, `pause` from `live`, `resume` from `paused`, `end` from `live|paused|scheduled`; illegal moves 409 `campaign_conflict`; `ended` terminal; every move audited with actor + reason.
- Batches: atomic generation (100k max), prefix uniqueness among non-ended campaigns, `invite_only` requires `inviteUserIds.size == count` (single-use enforced by constraint); plaintext only in the 201; ack audited; void flips `active` -> `voided` only, audited with count.
- Credits: goodwill issuance validates `1..goodwillMaxDays` from `billing.promo` (absent -> 409); `referralDays` reserved for the gated referral surface; application lives in 36's job (D-8), never in this feature's request path.
- Config: `billing.promo` flips via 32's audited path; <= 5 s effect.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `coupon_invalid` | 422 | One neutral inline message (all non-expired classes) |
| `coupon_expired` | 422 | "This code has expired." |
| `campaign_invalid` | 422 | Ops form validation (bounds, window) |
| `campaign_conflict` | 409 | Ops console refreshes state; no partial write |
| `batch_invalid` | 422 | Ops form validation (prefix, count, invites) |
| `export_acked` | 409 | Ops notice; hashes-only thereafter |
| `promo_config_absent` | 409 | Ops form disabled with policy copy |

Reused: `billing_ops_required` (35), `unknown_provider` (33), `challenge_failed` (06), `validation_failed`, `throttled`.

```json
{ "type": "https://api.example.com/problems/coupon_invalid", "title": "Code can't be applied",
  "status": 422, "code": "coupon_invalid", "detail": "This code can't be applied." }
```
