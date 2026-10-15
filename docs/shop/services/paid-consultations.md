# Paid Consultations (bookable calls, paid upfront)

PLAN ref: P2-2 (shop page surface). The `service` kind is a flagged addition to the PLAN product-kind enum (see `products/product-catalog.md` Q1). No video-call infrastructure is built — calls run on the meeting link the creator supplies.

## Purpose

Sell 1-on-1 time: creator offers bookable consultation slots, buyer pays upfront, both receive the meeting details, and the booking survives reschedules and cancellations with exact money semantics. The platform handles booking + payment + reminders; the conversation itself happens wherever the meeting link points.

## Users

- Creator/expert: defines session types (duration, price, buffer times), opens availability, supplies a standing or per-booking meeting link.
- Buyer: picks a slot, pays upfront, receives confirmation + reminders, joins via the link.
- Both: reschedule or cancel under one published policy with no ambiguity about refunds.

## Functional requirements

1. Session types: `{title, duration_min, price_vnd (>0), buffer_before/after_min, meeting_link_template, policy_ref}` — multiple types per creator (e.g. 30-min intro / 60-min deep-dive). Meeting links are creator-supplied URLs (Meet/Zoom/Zalo — validated `https:`); the platform never hosts call media in v1.
2. Availability: weekly recurring windows + date overrides (holidays, travel); slots computed server-side from windows minus buffers minus existing bookings (double-booking impossible by construction — unique constraint on `(creator, slot_start)`). Timezone stored as Asia/Ho_Chi_Minh default with explicit buyer-local rendering.
3. Booking flow: slot hold (short TTL lock, ~10 min) → standard PayOS-first checkout → grant confirms booking; unpaid holds expire and free the slot without residue. Confirmation (email/page) carries slot in both timezones + meeting link + cancel/reschedule links (signed tokens, no login required).
4. Reschedule/cancel policy (single, platform-wide v1): buyer reschedule free until 24 h before; buyer cancel ≥24 h → full refund via standard refund path; <24 h or no-show → no refund (creator may courtesy-refund, audited). Creator cancel anytime → automatic full refund + buyer notice. Policy text shown pre-payment with explicit checkbox.
5. Reminders: confirmation at booking, reminder at 24 h and 1 h (transactional class per the consent model — see `monetize/marketing/consent-preferences.md` — never marketing-gated, never unsubscribable while the booking is active).
6. No-show and records: creator marks no-show (booking history only, no penalty rail in v1); both sides retain booking history; reviews/testimonials named v2 (would need moderation tooling first).

## Data / API touches

- New tables: `session_types{product_ref?, profile_id, duration_min, price_vnd, buffers, link_template, policy_version}` + `availability_windows{profile_id, weekday, start, end}` + overrides + `bookings{booking_id, session_type_id, buyer_key, slot_start UNIQUE-per-creator, status (held|confirmed|cancelled|completed|no_show), order_id, meeting_link_final}` (standard RLS/claim scoping; slot uniqueness enforced at DB level). All PG via `worker/db/*`.
- Endpoints: session-type + availability CRUD (owner), public slot query (cacheable short TTL, only live types), hold → checkout → grant-confirm chain, signed-token reschedule/cancel, creator no-show mark. Reminder sends via the transactional queue, not ad-hoc.
- Money: all movement through standard checkout/refund endpoints; booking rows reference `order_id` and never store amounts (price-at-booking snapshotted on the order, per the catalog freeze rule).

## Acceptance criteria

- Double-book drill: concurrent holds on the same slot yield exactly one confirmable booking (DB unique constraint verified under race).
- Hold-expiry drill: unpaid hold frees the slot within TTL; no orphan locks; grant-after-expiry correctly fails with re-book guidance.
- Policy drill: ≥24 h cancel refunds in full; <24 h cancel and no-show refuse with policy citation; creator cancel auto-refunds with buyer notice.
- Link drill: non-`https:` meeting links rejected; signed reschedule/cancel tokens work without login and expire after the slot passes.
- IDOR drill: buyer A booking operations against buyer B ids return 403; creator A cannot touch creator B availability.

## Out of scope

- Catalog/listing (see `products/product-catalog.md`); fees on service revenue (see `pricing/sales-fees.md`, same schedule).
- Checkout/refund/receipt internals (`monetize/payment/`, `monetize/billing/`); call hosting/recording (never in v1); reviews, packages/multi-session bundles (v2).

## Open questions

- Q1: Creator-side calendar sync (Google Calendar block-out) — v1 integration or manual availability only?
- Q2: Late-arrival grace (e.g. 15-min buyer lateness shortens vs reschedules) — codify in v1 policy or leave to creator discretion?
