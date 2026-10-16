# Agency Seat Billing - API Contracts and Payloads (feature 46)

Phase 2 TL · Contact/owner + ops + internal contracts. Auth/rate rules: `docs/02-architecture-techlead/46-agency-seat-billing/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/agency/billing/overview?orgId=` | session (contact/owner) | - | `200 AgencyBillingOverview` (prices nullable when catalog incomplete) | 403; 404; 429 |
| `POST /api/v1/agency/billing/subscribe` | session + CSRF (contact/owner) | `AgencySubscribeRequest` | `201 {subscriptionId, checkoutUrl}` (33's front door) | 409 `catalog_incomplete`, `already_subscribed`; 422 `provider_required`; 403; 429 |
| `POST /api/v1/agency/billing/cancel` | session + CSRF (contact/owner) | `{orgId, confirm: true}` | `200 {cancelAt}` (cycle end; no-hostage copy) | 409 `not_subscribed`; 403; 429 |
| `GET /api/v1/agency/billing/seats/history?orgId=&cursor=` | session (contact/owner) | - | `200 SeatHistoryPage` (dated snapshots + by-role) | 403; 429 |
| `GET /api/v1/agency/billing/invoices?orgId=&cursor=` | session (contact/owner) | - | `200` (37's receipts filtered to the agency subscription) | 403; 429 |
| `POST /api/v1/agency/billing/contact/transfer` | session + CSRF (contact/owner) | `{orgId, toUserId}` | `201 TransferView` (`pending`) | 422 `validation_failed` (target class); 409 `transfer_conflict`; 403; 429 |
| `POST /api/v1/agency/billing/contact/transfer/{id}/confirm-outgoing`, `/confirm-incoming` | session + CSRF (each side) | - | `200 TransferView` | 409 `transfer_expired`, `transfer_conflict`; 403; 429 |
| `POST /api/v1/agency/billing/contact/transfer/{id}/cancel` | session + CSRF (either side) | - | `200 TransferView` (`cancelled`) | 409; 403; 429 |
| `GET /api/v1/ops/billing/agency/reconciliation` | ops role | - | `200` mismatches (org, billed, roster, day) | 403; 429 |
| `POST /api/v1/ops/billing/agency/reconcile` | ops role | `{orgId, reason}` | `200 {corrected: true}` (open-day only, audited) | 409 `reconciliation_conflict`; 403; 429 |
| `GET /internal/v1/billing/agency/entitlement?orgId=` | service JWT | - | `200 {active: Boolean, state: String}` | 401 |

```kotlin
data class AgencySubscribeRequest(val orgId: String, val provider: String) // explicit; no default rail
data class AgencyBillingOverview(val seats: Int, val seatPriceVnd: Long?, val basePriceVnd: Long?,
  val projectedTotalVnd: Long?, val trueUpVnd: Long?, val state: String, val catalogVersion: String?,
  val cycleStart: Instant?, val cycleEnd: Instant?)
data class SeatHistoryPage(val rows: List<SeatHistoryRow>, val nextCursor: String?)
data class SeatHistoryRow(val day: String, val seats: Int, val byRole: Map<String, Int>, val source: String)
data class TransferView(val id: String, val fromUserId: String, val toUserId: String,
  val state: String, val outgoingAt: Instant?, val incomingAt: Instant?, val expiresAt: Instant)
```

## Events (billing-service stream `billing.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `agency.subscription_changed.v1` | `{orgId, subscriptionId, state}` | activation/lapse/cancel transitions | 45 (entitlement cache refresh), notification |

Post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Seats are derived from the roster (45) via the daily snapshot — no billing endpoint mutates seat counts; additions/removals reach the bill through snapshots + true-up only.
- Amounts: base + seat price from the catalog (`billing.catalog`); overview returns null prices with a refusal note when the catalog lacks agency values; subscribe refuses `catalog_incomplete`; every line stamps the catalog version.
- Money flow: subscribe delegates to 33's front door (explicit `provider`, eight equal rails); grants, renewal, dunning, grace ride 34/35/36; invoices 37; refunds 38; credits 39 — zero parallel money paths exist.
- True-up: computed at renewal from snapshots; positive attaches to the next order metadata; negative applies as account credit; closed periods are never rewritten.
- Transfer: dual confirmation (outgoing + incoming), 7-day expiry, one pending per org; effective only on both; single-sided attempts inert; audited.
- Entitlement read: service-JWT only; `active` while subscription ∈ active/grace (36 semantics); 45 consumes it at staff invites + Act-as-user starts; clients never consult it.
- Ops: reconciliation reads + open-day corrections only (reason mandatory, audited); closed days have no correction path.
- No instrument data, tokens, or provider payloads in any DTO, event, or problem detail.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `catalog_incomplete` | 409 | "Agency pricing isn't configured yet." |
| `provider_required` | 422 | Provider picker, no default |
| `already_subscribed` | 409 | Overview link |
| `not_subscribed` | 409 | Subscribe path |
| `transfer_conflict` | 409 | Pending transfer shown |
| `transfer_expired` | 409 | Restart flow |
| `reconciliation_conflict` | 409 | Open-day-only explanation |

Reused: `throttled`, `validation_failed`, `grace_expired` (38-style reuse).
