# Consultations and Tips - API Contracts and Payloads (feature 25)

Phase 2 TL · Endpoints in `shop-service`; forward money seams to 32-40; security: `docs/02-architecture-techlead/25-shop-consultations-and-tips/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `PUT /api/v1/services/availability` | session + CSRF (owner) | `AvailabilitySave` (windows + overrides, full save) | 200 `AvailabilityView` | 422 `availability_invalid`; 429 |
| `GET /api/v1/services/{productId}/slots?from=&to=` | public | - | 200 list `SlotView` (open only; 60 s cache) | 404; 422 `slot_invalid` (range); 429 |
| `POST /api/v1/services/{productId}/holds` | session + CSRF | `HoldRequest` | 201 `HoldResponse` (`bookingId`, `holdExpiresAt`) | 404; 409 `slot_taken`; 422 `slot_invalid`; 429 |
| `POST /api/v1/services/bookings/{bookingId}/cancel` | session (buyer/owner) or signed token | `{reason?}` | 200 `BookingView` (refund reason when due) | 401 `booking_token_expired`; 403; 404; 422 `cancel_window_closed`; 409 `transition_conflict`; 429 |
| `POST /api/v1/services/bookings/{bookingId}/reschedule` | session (buyer) or signed token | `{newSlotStart}` | 200 `BookingView` | 401 token; 403; 404; 409 `slot_taken`; 422 `reschedule_window_closed`, `slot_invalid`; 429 |
| `GET /api/v1/services/bookings` | session | `?status=&cursor=` | 200 `BookingPage` (actor-scoped: owner list or own list) | 401 |
| `POST /api/v1/services/bookings/{bookingId}/no-show` | session + CSRF (owner) | - | 200 `BookingView` | 404; 422 `window_closed` (7-day mark window); 409; 429 |
| `GET /api/v1/tips/messages` | session (owner) | `?status=` | 200 `TipMessagePage` | 401 |
| `POST /api/v1/tips/messages/{tipOrderId}/moderate` | session + CSRF (owner) | `{status: approved|rejected}` | 200 `TipMessageView` | 404; 409 `transition_conflict`; 429 |
| `GET /api/v1/tips/{productId}/wall` | public | - | 200 list `WallEntry` (approved only) | 404 |
| `POST /api/v1/tips/{productId}/checkout` | session **or guest** + Turnstile | `TipCheckoutRequest` | 201 `{checkoutUrl, orderRef}` | 404; 422 `amount_out_of_bounds`, `email_required`, `message_too_long`; 429 |
| `GET /internal/v1/tips/{productId}/supporters` | service JWT | - | 200 list `WallEntry` (approved only) | 401 |
| `GET /internal/v1/services/{productId}/booking-context` | service JWT | - | 200 `{policyVersion, meetingLinkValid}` | 401; 404 |

## Kotlin DTOs

```kotlin
data class AvailabilitySave(val tz: String, val windows: List<WindowInput>, val overrides: List<OverrideInput>)
data class WindowInput(val weekday: Int, val start: LocalTime, val end: LocalTime)
data class OverrideInput(val date: LocalDate, val kind: String, val start: LocalTime? = null, val end: LocalTime? = null)
data class SlotView(val startUtc: Instant, val endUtc: Instant, val buyerLocal: String, val creatorLocal: String)
data class HoldResponse(val bookingId: String, val holdExpiresAt: Instant, val priceVnd: Long)
data class BookingView(val id: String, val productId: String, val status: String, val slotStart: Instant,
  val slotEnd: Instant, val meetingLink: String?, val cancelReason: String?,
  val viewerRole: String)
data class BookingPage(val items: List<BookingView>, val nextCursor: String?)
data class TipCheckoutRequest(val amountVnd: Long, val message: String? = null,
  val displayName: String? = null, val anonymous: Boolean = false, val email: String? = null)
data class TipMessageView(val tipOrderId: String, val displayName: String?, val message: String?,
  val status: String, val createdAt: Instant)
data class TipMessagePage(val items: List<TipMessageView>, val nextCursor: String?)
data class WallEntry(val displayName: String?, val message: String, val tipAt: Instant)
```

## Rules

- Hold → checkout: the client passes `bookingId` as order extras (33/34 forward); grant (`order.granted.v1`) confirms exactly that row via CAS (`held → confirmed`); the `policyVersion` recorded at hold time is enforced thereafter.
- Cancel/reschedule matrix (D-3) is computed server-side with the creator timezone; refund reasons fixed: `cancel_ge_24h`, `creator_cancel`, `hold_expired`; courtesy-refunds are owner actions with audit; a post-expiry grant auto-refunds (`hold_expired`).
- Tokens: HMAC `bookingId|action|exp`; exp = slot + 24 h; invalid/expired → 401 `booking_token_expired`; the token is not a session and grants only its action.
- Tip checkout: bounds enforced before the money call; guest requires `email` (hashed into `buyer_email_hash`); Turnstile always; the order kind `tip` routes future refunds to `tip_final`.
- Moderation: `pending → approved|rejected` only (CAS); approval emits `shop.tip_wall_changed.v1` (`{username, productId, at}`).
- One jar per profile: creating/publishing a second live `tip` product → 409 `tip_jar_exists` (app check + audit).

## Events

| Direction | Event | Notes |
|---|---|---|
| consumes | `order.granted.v1` | forward, 32-40; confirm booking / settle tip (tip needs no row beyond messages) |
| consumes | `order.refunded.v1` | forward, 38; booking cancel states + notices |
| emits | `shop.tip_wall_changed.v1` | `{username, productId, at}`; public-site purges the product-page key (feature 10 row) |

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| order extras `bookingId` | 33/34 | carried through to `order.granted.v1` |
| guest tip orders | 33/34 | `kind=tip` + receipt email; no account created |
| refund reasons + tip finality | 38 | reason codes above; `tip` refuses with `tip_final` except provider disputes |
| transactional sends | 32-40 (notification scope) | confirmation + reminders + notices; never marketing-gated |
| price-at-booking snapshot | 32-40 | booking rows store no amounts |

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/22-shop-catalog/` | registry note (`tip`, `service` payloads); publish precondition extended |
| `docs/02-architecture-techlead/10-share-and-seo/` | supporters internal row; `shop.tip_wall_changed.v1` consumer row; read-path note |

## Governance

- Codes appended to the shop catalog; every forward seam above is a recorded contract, not a stub.
- Contract tests: unique-index race, expiry+grant, policy matrix, token expiry, bounds/guest matrix, moderation CAS, wall purge.
