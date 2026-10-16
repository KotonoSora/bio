# Consultations and Tips - User Stories and Business Rules (feature 25)

Phase 1 BA · Realized by `docs/02-architecture-techlead/25-shop-consultations-and-tips/` (BR-01..BR-20).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator/expert | define session types, open availability, and supply a meeting link | 1-on-1 time sells | Must |
| US-02 | Buyer | pick a slot, pay upfront, and receive confirmation + reminders | I can join via the link | Must |
| US-03 | Creator or buyer | reschedule or cancel under one published policy | refunds have no ambiguity | Must |
| US-04 | Fan | tip with presets or a custom amount and an optional message | support takes two taps | Must |
| US-05 | Creator | configure presets, read messages, and optionally show a wall | goodwill converts to earnings | Must |
| US-06 | Moderator (owner) | screen messages before any public display | the wall stays safe | Must |
| US-07 | Guest fan | tip with an email receipt | support needs no account | Should |

## Business rules

- BR-01 Session products: `service` kind, price > 0, payload `{durationMin 15-240, bufferBeforeMin 0-120, bufferAfterMin 0-120, meetingLink https:}`, `policyVersion` pinned at booking; multiple session products per creator allowed.
- BR-02 Availability: creator-level weekly windows + date overrides (`closed` or extra `window`) in the creator's timezone (default `Asia/Ho_Chi_Minh`); slots compute server-side = windows minus buffers minus active bookings; 15-min granularity; minimum notice 2 h; horizon 60 days.
- BR-03 Double-booking impossible: partial unique index on `(creator, slot_start)` where status is `held|confirmed`; concurrent holds → exactly one survives (409 `slot_taken` for the loser).
- BR-04 Booking flow: hold (TTL 10 min) → checkout (33/34; the order carries `bookingId`) → grant (`order.granted.v1`, forward) confirms the same hold; unpaid holds expire (`expired`) and free the slot; a grant arriving after expiry changes nothing and auto-refunds via the standard refund path (reason `hold_expired`) with re-book guidance.
- BR-05 Policy (single, platform-wide, v1): buyer reschedule free until 24 h before (re-anchors to the new slot; unique-index races → 409); buyer cancel ≥ 24 h → full refund (reason `cancel_ge_24h`); < 24 h or no-show → no refund (422 with policy citation; creator courtesy-refund audited); creator cancel anytime → automatic full refund (reason `creator_cancel`) + buyer notice. Policy text shown pre-payment with an explicit acknowledgment (422 `policy_ack_required` without it).
- BR-06 Confirmation + tokens: confirmation carries slot in creator timezone and buyer-local time, meeting link, and signed reschedule/cancel links that work without login and expire 24 h after the slot.
- BR-07 Reminders: confirmation immediately; 24 h and 1 h before; transactional class (never marketing-gated, never unsubscribable while active); canceled/rescheduled bookings re-schedule or drop reminders.
- BR-08 No-show/records: creator marks no-show within 7 days after slot end; otherwise bookings auto-complete 72 h after slot end; both sides retain history; no penalty rail.
- BR-09 Meeting links: `https:` only, validated on save (422 `bad_url_scheme`); re-validated at confirmation; the platform hosts no call media in v1.
- BR-10 Tip jar: one per profile (second live `tip` product → 409 `tip_jar_exists`); payload `{presets[] 3-6 entries, floor, ceiling, wallEnabled}`; presets and custom amounts are VND integers within floor ≤ amount ≤ ceiling (422 `amount_out_of_bounds` with bounds stated); defaults floor 10 000, ceiling 5 000 000, presets 10k/20k/50k.
- BR-11 Tip checkout: standard rail path, idempotent grant, settle instantly with no fulfillment hook; receipt to the payer email for guests (mandatory for guests — 422 `email_required`); Turnstile + throttles on the public surface.
- BR-12 Tip messages: optional, ≤ 280 chars, links/markup stripped (plain text), display-name choice (named/anonymous); every message starts `pending`; only `approved` renders publicly; `rejected` stays private to creator + tipper; moderation is owner-only.
- BR-13 Wall: `wallEnabled` defaults off; renders approved messages only on the tip product page; freshness ≤ 60 s after a moderation change; no social auto-posting ever in v1.
- BR-14 Tip finality: tips are final by policy — the refund path refuses tip orders (reason `tip_final`) except unauthorized-payment disputes, which route to the provider flow (32-40), never a custom path.
- BR-15 Price freeze: booking rows never store amounts; the order snapshots price-at-booking (feature 22 rule); price changes never alter existing bookings.
- BR-16 Escalation edges: guest bookings are account-bound in v1 (no guest booking — checkout for `service` is session-bound); guest tips are the only guest surface (O-6).
- BR-17 Ownership: creator scope by `user_id`, buyer scope by `buyer_user_id`, tokens for session-free access; no cross-user path (IDOR drill).
- BR-18 Audit: booking confirm/cancel/reschedule, no-show marks, courtesy-refunds, moderation decisions, jar config changes append-only (ids only).
- BR-19 Rate limits: holds 20/h per buyer; reschedules 10/h; tip checkout 30/h per IP; moderation 200/h; slots query 120/h per IP.
- BR-20 Purge: seller purge deletes windows/overrides/session data + bookings as owner + tip messages; buyer/guest purge deletes their bookings and tip-message authorship (message text nulled when authored by the purged party unless approved-and-anonymous, then pinned to anonymous).
