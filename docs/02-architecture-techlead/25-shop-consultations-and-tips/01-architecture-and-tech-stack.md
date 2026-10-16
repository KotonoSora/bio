# Consultations and Tips - Architecture and Tech Stack (feature 25)

Phase 2 TL · Feature 25 of 53 — shop 4/5; extends `shop-service` (port 8087, `shop_db`, Redis db 9) — no new service. Implements `docs/01-requirements-ba/25-shop-consultations-and-tips/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; prior shop files: `docs/02-architecture-techlead/22-shop-catalog/` through `docs/02-architecture-techlead/24-shop-courses/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | A bookable offering is a `service` product (payload `{durationMin, bufferBeforeMin, bufferAfterMin, meetingLink}`); availability is creator-level (windows + overrides), not per product. | One catalog row per sellable thing (feature 22); multiple session types = multiple products; availability naturally belongs to the person. |
| D-2 | Double-booking is a DB property: partial unique index on `(user_id, slot_start)` where status `held|confirmed`; hold (TTL 10 min) → checkout with `bookingId` → grant confirms the same row. Expired holds go `expired` and free the slot; a post-expiry grant auto-refunds (`hold_expired`) via the standard refund path. | The legacy "impossible by construction" wording maps exactly; races collapse to 409 `slot_taken`. |
| D-3 | Policy is code, not copy: the cancel/reschedule matrix (`cancel_ge_24h`, `cancel_window_closed`, `creator_cancel`, `hold_expired`) is enforced server-side; the pre-payment acknowledgment is recorded on the order extras; courtesy-refunds are audited owner actions. | One platform-wide policy (legacy) with auditable money reasons (32-40 executes). |
| D-4 | Reschedule/cancel links are stateless HMAC tokens (`bookingId|action|exp`), valid without login until slot + 24 h; sessions work too. No token table. | Legacy link drill honored; nothing to purge on token expiry. |
| D-5 | Reminders are DB-swept: a 5-min sweep selects `confirmed` bookings crossing T-24 h / T-1 h with sent-flags, then enqueues transactional sends through the platform notification pipeline (forward seam with 32-40; class fixed: transactional, never marketing-gated, never unsubscribable while active). | No Redis scheduler needed; flags make sweeps idempotent. |
| D-6 | Guest tips ship: the tip checkout path accepts sessionless payers with a mandatory receipt email (hashed at rest), Turnstile + throttles; guest bookings do not exist (BR-16). | The legacy guest drill is honored without importing the claim machinery features 23-24 rejected for access delivery. |
| D-7 | Tip messages: sanitized on write (plain text, links/markup stripped), `pending` until owner moderation; the wall renders approved rows only via `GET /internal/v1/tips/{productId}/supporters`; moderation emits `shop.tip_wall_changed.v1` purging the product-page key (feature 10 row). | Wall safety + freshness ≤ 60 s; no social posting ever in v1. |
| D-8 | Money boundaries: booking/tip rows never store amounts; the order snapshots price (feature 22 rule); refund reasons fixed (BR-05/14); tip finality lives in the 32-40 refund router branching on the product kind already snapshotted on the order. | Zero invented finance values; the money services remain the only setters. |
| D-9 | Purge: seller scope deletes windows/overrides/bookings-as-owner/tip messages + objects; buyer/guest scope deletes their bookings and scrubs message authorship — approved anonymous pins stay anonymous, named approved messages lose the name and show `Supporter`. | Privacy default (Q4) with a defined scrub semantic (BR-20). |

## Component map

| Piece | Home | Note |
|---|---|---|
| Availability + slots | `shop-service` | windows/overrides → slot computation (15-min grid, 2 h notice, 60-day horizon) |
| Holds + bookings | `shop-service` | partial unique index; grant/refund consumers (forward 32-40) |
| Signed tokens | `shop-service` | HMAC, no table; expiry slot + 24 h |
| Reminder sweep | `shop-service` | 5-min cadence, sent-flags, transactional enqueue (forward) |
| Tip checkout surface | `shop-service` + gateway | Turnstile + throttle; money via 33 |
| Moderation + wall | `shop-service` | owner queue; internal approved-only read for public-site |
| Product-page wall row | `public-site-service` | supporters read + `shop.tip_wall_changed.v1` purge (feature 10 cross-edit) |

## Boundaries

Owns: availability, slots, holds/bookings, policy enforcement, tokens, reminders scheduling, no-show records, tip jar config, tip messages, moderation, wall data, purge. Does not own: products/listing/pricing (22), delivery/courses (23-24), fees (26), checkout/refunds/receipts/ledger/disputes (32-40), email delivery pipeline (forward), SSR rendering (10).

## Consumers

| Consumer | Contract |
|---|---|
| checkout (33/34) | order carries `bookingId`; guest tip orders carry receipt email; refund router consumes reason codes |
| public-site | approved-only supporters read for the tip product page |
| notification pipeline (forward) | confirmation + reminders + cancellation notices (transactional class) |
| creator dashboard | booking list, moderation queue, tip feed |
