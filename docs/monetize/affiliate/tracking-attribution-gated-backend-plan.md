# Affiliate Tracking Attribution Gated Backend Plan (links + precedence + windows — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/tracking-attribution.md`. Storage contract in the tracking database plan above (clicks + attributions; nothing created now). Stats screens in the frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for dispute-proof attribution: signed referral links with server-side click records, closed precedence, window enforcement to the second, anti-gaming at ingest, and evidence bundles per credit. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships redirect + stamping + windows + gaming filters in package `com.app.aff.tracking` (GATED).
- QA (now): asserts absence; (future) precedence/window/reversal/stuffing/privacy drills.
- Referred buyer/affiliate/reviewer (future): friction-free tracking, near-real-time stats, replayable credits.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith tracking (package `com.app.aff.tracking`, GATED). Monolith pros: redirect + click-log + checkout-stamping share one transaction baseline with the order insert (attribution stamped at creation, pre-payment, in the same unit of work); precedence evaluated in-process. Microservices cons avoided: a tracking service would split stamping from order creation (the re-pointing risk this design forbids). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: redirect is cache-fronted affiliate lookup + signed-cookie set (fast, anonymous, throttled); click floods rejected at ingest before PG (dashboards can't be skewed by traffic that never lands).
2. Step 1 — referral redirect (GATED): `GET /r/:code` (`<affiliate_slug>-<random>`, unguessable suffix, no sequential ids; slug vanity-stable, suffix rotatable on leak) → fast cached affiliate lookup → set signed `aff_id` cookie (`HttpOnly`+`Secure`+`SameSite=Lax`, 30d) → 302 to deep-link target (profile/checkout preserved). No affiliate-writable redirect fields exist (destination allowlist enforced — open-redirect via affiliate links would be phishing infrastructure).
3. Step 2 — stamping at checkout (GATED): read cookie and/or promo code → apply closed precedence (code > most-recent-cookie > unattributed) → stamp `affiliate_id + source + evidence refs` on the `orders` row at CREATION (fixed pre-payment, never re-pointed — no update path exists, asserted by absence). Attribution rate-limits mirror checkout budgets (click floods can't skew dashboards OR force credits).
4. Step 3 — windows + states (GATED): qualifying purchase within click-timestamp + window (to the second, server-side timestamps — cookie expiry is UX, server math is truth) → `pending` at creation → `confirmed` past refund window post-grant → `reversed` on refund/dispute-loss (proportional on partials). Non-qualifying (trials/0-VND/cancelled/expired/self-purchase) stays `pending`-visible-but-non-payable (stored for audit, excluded from computation by status, not by deletion).
5. Step 4 — anti-gaming at ingest (GATED): stuffing signals (unattributed drops, iframe-hidden sets) rejected before logging-as-valid (rejection rows feed the terms ladder); self-referral voids + flags (identity/signal match at stamping); flood dashboards stay clean because floods never become rows (ingest-time rejection, load-tested).
6. Step 5 — privacy (GATED): first-party only; consent classes honored (declined → unattributed checkout, never blocked); affiliate stats aggregates + own referrals, zero buyer PII (stats queries exclude buyer columns structurally).
7. Step 6 — gate enforcement NOW: zero tracking tables/redirects/stamping/writers in current images (grep + migration audit fails the tag on any `affiliate_clicks|order_attributions|/r/:code|aff_id` surface). Note: `/r/` alias resolution for SHORT LINKS (url-shortener plans) is a different system sharing only the prefix letter — its existence does not satisfy this gate (affiliate `/r/:code` with slug-random shape stays absent).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): public `GET /r/:code` redirect (throttled); checkout stamping (inside order creation, no new endpoint); affiliate `GET /api/v1/affiliate/stats` (clicks/conversions/pending/confirmed). No affiliate-writable attribution fields exist (absence asserted).

## Acceptance criteria

- Absence drill (now): zero tracking surface pre-update (including disambiguation from short-link `/r/` aliases).
- Precedence drill (at build): code > recent-cookie > unattributed, reproducible from bundles.
- Window drill (at build): to-the-second enforcement both sides of the boundary.
- Reversal drill (at build): full/proportional flips; self-purchase voids + flag.
- Stuffing drill (at build): ingest rejection, clean dashboards, ladder warned.
- Privacy drill (at build): declined-consent normal checkout, zero attribution, PII-free stats.

## Out of scope

- Click/attribution contracts (database plan above); stats screens (frontend plan); enrollment/tiers (program plans); computation/payouts (commission plans); influencer batches (consumed as codes); referral credits.

## Open questions

- Q1: Window length (see database plan Q1)?
- Q2: Click-log retention (see database plan Q2)?
