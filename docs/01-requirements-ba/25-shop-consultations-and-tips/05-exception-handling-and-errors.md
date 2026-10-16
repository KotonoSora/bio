# Consultations and Tips - Exception Handling and Errors (feature 25)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Slot already held/booked (race) | API | 409 `slot_taken`; client refreshes slots | "This slot was just taken — pick another." |
| Past slot / outside window / under 2 h notice | API | 422 `slot_invalid` | "That time isn't available." |
| Hold expired before payment | API | 409 `hold_expired`; re-book guidance | "Your hold expired — pick a slot again." |
| Grant after hold expiry | System | no confirmation; auto-refund (`hold_expired`) via standard path | email copy + re-book link |
| Meeting link not https | API | 422 `bad_url_scheme` | "Use a secure https:// meeting link." |
| Policy checkbox missing | API | 422 `policy_ack_required` | "Please accept the booking policy." |
| Cancel < 24 h | API | 422 `cancel_window_closed` with policy citation | "Cancellations within 24 hours aren't refundable." |
| Reschedule < 24 h | API | 422 `reschedule_window_closed` | "Rescheduling closes 24 hours before the session." |
| Reschedule race on the new slot | API | 409 `slot_taken` | "That slot was just taken — pick another." |
| Expired signed token (post-slot) | API | 401 `booking_token_expired` | "This link has expired — open your booking page." |
| IDOR (foreign booking/availability) | API | 403 + RLS denies | "You don't have access to this." |
| Tip below floor / above ceiling | API | 422 `amount_out_of_bounds` with bounds stated | "Tips range from 10 000 to 5 000 000 ₫." |
| Guest tip without email | API | 422 `email_required` (receipt-mandatory) | "Enter an email for your receipt." |
| Tip message over 280 chars | API | 422 `message_too_long` | "Messages allow up to 280 characters." |
| Second tip jar on one profile | API | 409 `tip_jar_exists` | "You already have a tip jar." |
| Tip refund request | API | 422 `tip_final` with policy reason | "Tips are final — see the policy." |
| Unauthorized-payment claim on a tip | Provider flow | routed to 32-40 dispute path, never custom | provider-copy |
| Rapid-fire tips / holds | Gateway | 429 + `Retry-After`; Turnstile on the tip surface | "Too many requests — try again shortly." |
| Public wall with no approved messages | Render | section hidden entirely | - |
