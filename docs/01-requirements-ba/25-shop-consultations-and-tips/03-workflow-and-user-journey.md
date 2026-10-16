# Consultations and Tips - Workflow and User Journey (feature 25)

Phase 1 BA · System calls cite owning features; forward contracts cite 32-40.

## Creator: availability → sessions live

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | defines weekly windows + overrides; sets timezone | windows stored (default `Asia/Ho_Chi_Minh`); overrides win over windows | BR-02 |
| 2 | Creator | creates `service` products (duration, buffers, https meeting link, price) | payload validated; link scheme checked | BR-01/09 |
| 3 | Creator | publishes | precondition: ≥ 1 window + valid link, else 409 `publish_precondition_unmet` | BR-01 |
| 4 | System | slots queried publicly | server computes open slots ≤ 60 days ahead, ≥ 2 h notice | BR-02 |

## Buyer: hold → pay → join

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | picks a slot | hold created (10 min TTL); unique index admits exactly one | BR-03/04 |
| 2 | Buyer | pays via standard checkout with `bookingId` | order snapshots price; acknowledgment checkbox recorded | BR-05/15 |
| 3 | System | grant (`order.granted.v1`, forward) | booking `confirmed`; confirmation email with both timezones + link + tokens | BR-06 |
| 4 | System | T-24 h and T-1 h | transactional reminders fire | BR-07 |
| 5 | Buyer | joins | via the creator's meeting link; platform hosts nothing | BR-09 |
| 6 | System | hold unpaid past TTL | booking `expired`; slot freed; no residue | BR-04 |

## Reschedule / cancel matrix

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | reschedule ≥ 24 h before | slot re-checks uniqueness; 409 `slot_taken` on race; reminders re-anchored | BR-05 |
| 2 | Buyer | cancel ≥ 24 h before | full refund via standard refund path (`cancel_ge_24h`) | BR-05 |
| 3 | Buyer | cancel < 24 h | 422 `cancel_window_closed` with policy citation; creator may courtesy-refund (audited) | BR-05 |
| 4 | Creator | cancels any time | automatic full refund (`creator_cancel`) + buyer notice | BR-05 |
| 5 | Creator | marks no-show within 7 days | history-only record; auto-complete at 72 h otherwise | BR-08 |
| 6 | System | grant after hold expiry | no confirmation; auto-refund (`hold_expired`) + re-book guidance | BR-04 |

## Fan: tip

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Fan | opens the jar, picks a preset or custom amount | bounds enforced inline; presets settle one-tap | BR-10 |
| 2 | Fan | adds an optional message + name choice | ≤ 280 chars; links/markup stripped; message `pending` | BR-12 |
| 3 | Fan (guest) | pays with email | receipt emailed; order settles instantly; zero fulfillment hooks | BR-11/14 |
| 4 | Creator | reviews the feed | approve → wall (if enabled); reject → private; thank mark optional | BR-12/13 |
| 5 | Public | opens the tip product page (wall enabled) | approved messages only; cache ≤ 60 s stale | BR-13 |

## Support / purge

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | requests a tip refund | refused `tip_final`; unauthorized-payment → provider flow (32-40) | BR-14 |
| 2 | Purge runner | buyer/guest deletion | their bookings + message authorship handled; anonymous pins stay anonymous | BR-20 |
| 3 | Purge runner | creator deletion | windows, overrides, session/booking ownership, tip messages deleted with counts | BR-20 |
