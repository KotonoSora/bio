# Consultations and Tips - Context and Scope (feature 25)

Phase 1 BA · Feature 25 of 53 — shop 4/5. Legacy sources: `shop/services/paid-consultations.md` and `shop/tips/tip-jar.md` (folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/25-shop-consultations-and-tips/`; extends shop-service (features 22-24).

## Purpose

Two goodwill-adjacent money surfaces: bookable 1-on-1 time (creator offers session types, buyer pays upfront, one published reschedule/cancel policy, reminders; no call media is hosted — calls run on the creator's meeting link) and two-tap tipping (presets or custom amounts, optional moderated message, optional public supporter wall; no fulfillment tail).

## Objectives

| # | Objective |
|---|---|
| O-1 | Session products + creator availability render server-computed slots; double-booking is impossible by construction. |
| O-2 | Hold → checkout → grant confirms a booking; unpaid holds expire and free the slot without residue; money moves only through the standard rails. |
| O-3 | One platform-wide policy: reschedule free until 24 h before; buyer cancel ≥ 24 h full refund; < 24 h / no-show no refund; creator cancel auto-refunds. |
| O-4 | Tips: presets + custom within floor/ceiling, optional 280-char message held for owner approval, approved-only public wall, receipt for every tip. |
| O-5 | Reminders (confirmation, 24 h, 1 h) are transactional-class: never marketing-gated, never unsubscribable while the booking is live. |
| O-6 | Guest tips (email receipt required, Turnstile + throttles) are v1's single session-free money surface. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator/expert | C/R/U/D own availability + session products; R own bookings; mark no-show | defines session types, opens availability, cancels with auto-refund |
| Buyer/booker | C own holds/bookings; U own reschedule/cancel | picks a slot, pays upfront, holds receipts |
| Fan (possibly guest) | C own tip orders | tips with presets/custom, optional message |
| Moderator (= owner) | U tip message status (approve/reject) | screens messages before any public display |
| Anonymous attacker | none | deny-by-default; Turnstile on the tip write surface |

## In scope

Session products (feature 22 `service` kind) + availability windows/overrides; slot computation + holds; booking lifecycle + signed reschedule/cancel tokens; reminders; no-show marking; policy enforcement; tip jar config; tip checkout (guest-capable); message moderation + wall; purge participant (owner + buyer/guest scrub).

## Out of scope

Catalog/listing/pricing mechanics (22); fulfillment/delivery (23); courses (24); fees (26); checkout/refunds/receipts/ledger internals (32-40); call hosting/recording (never in v1); calendar sync (Q2, amendment); reviews/testimonials (v2); recurring tips (v2 — would collide with 32-40 subscription ownership); social auto-posting (41-44 territory).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 launch numbers | Tip floor 10 000 VND, ceiling 5 000 000 VND, default presets 10k/20k/50k (custom within bounds). Booking: hold TTL 10 min; minimum notice 2 h; horizon 60 days; 15-min slot granularity; buffers 0-120 min per session product. |
| Q2 calendar sync | Manual availability only in v1 (weekly windows + date overrides); calendar integration is a later amendment. |
| Q3 late arrival | Policy copy may state creator discretion; no platform enforcement and no penalty rail in v1. |
| Q4 wall default | Public supporter wall defaults OFF (privacy default); the owner opts in per jar. |
| Q5 guest tips | Ship as the single session-free money surface (legacy guest drill honored: email receipt mandatory). Features 23-24 stay account-bound because those were access-delivery problems, not receipt-only payments. |
| Q6 recurring tips | v2, named not promised; the UI makes no recurring promise. |
| Q7 no-show records | History only; no penalty rail in v1. |
