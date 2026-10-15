# Retargeting + Reach (first-party segments, real-time)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P4-growth beside `monetize/advertising/` (which owns inventory, serving/decisioning, and revenue share — consumed here, never redefined); needs a plan update before build. No third-party pixels, no platform-audience uploads, no data-sharing deals in v1.

## Purpose

Let attention compound: visitors who viewed a product but did not buy, readers who finished a paywalled preview but did not unlock, carts left at checkout — each becomes a real-time segment the platform can reach again on its own surfaces (onsite ad slots, owned email where consented). Business-grade means measurable and consented, not creepy: every segment states its source events, its lookback, and its consent basis, and nothing leaves the platform in v1.

## Users

- Creator/advertiser (direct deal): builds "viewed X, didn't buy in 7 days," watches segment size live, aims inventory or consented email at it, reads lift.
- Visitor: sees relevant onsite placements or an opted-in reminder — never cross-site pursuit, never unexplained targeting.
- Privacy reviewer: verifies each segment's consent basis and first-party boundary before it serves.

## Functional requirements

1. Segment definitions (closed v1 event set): page/profile viewers, product viewers, preview-finishers, checkout abandoners (started, never granted), past buyers (for upsell/exclude logic). Each segment declares source events + lookback window (e.g. 7/30 days) + membership cap; definitions are versioned (a running segment never silently changes meaning).
2. Real-time evaluation: segment membership updates from the existing event pipelines (beacon aggregates, order events) on a minutes-scale consumer — "abandoned 10 minutes ago" is reachable, with the lag labeled honestly in-UI. No per-hit synchronous writes; the evaluator is a scheduled/queue consumer like all pipelines. Segment-size counts are exact-ish (dedupe-tolerant) and shown with `as of` timestamps.
3. Reach surfaces (v1, first-party only): onsite ad inventory (decisioning from `advertising/ad-serving-measurement.md` gains segment-targeting as a targeting dimension) + owned email via the campaign path (segment → eligible sends only where the consent model allows — abandon-cart reminders are lifecycle class; promo nudges require marketing opt-in). Off-platform audience export (custom-audience uploads to social ad platforms) is explicitly deferred to a reviewed v2 (see Q2).
4. Consent gating per segment: behavioral membership never overrides consent class — a segment of abandoners with only transactional consent receives the lifecycle reminder, never the promo variant; the send-eligibility function arbitrates per recipient at send time (per the consent doc). Segments spanning creators are impossible (per-creator membership only; no platform-wide pooling).
5. Frequency + fatigue: per-segment contact caps (email + onsite impression caps per recipient per week, creator-tunable within platform ceilings); exclusion segments (past buyers excluded from acquisition nudges by default); every retargeted message carries its cause ("You left this in checkout") and one-click stop.
6. Measurement: holdout-first reporting (each retargeting flight reserves a no-contact control slice; lift reported vs control, not vs zero); segment reports show size, reach, conversion, and suppression counts. No lift claim without its control slice.

## Data / API touches

- New tables: `segments{segment_id, profile_id, definition_version, event_set, lookback_days, consent_basis}` + `segment_membership{segment_id, member_ref, entered_at, expires_at}` (TTL'd rows — expiry is the privacy mechanism, not a cleanup chore) + `retarget_flights{flight_id, segment_id, surface (onsite|email), control_pct, status}`. Standard `user_id` + RLS; all PG via `worker/db/*`.
- Evaluator: consumer over beacon/order event streams (minutes cadence); serving reads membership at decision time (KV-cached with short TTL, PG as truth). Endpoints: segment CRUD + preview-size, flight launch/pause, lift report (owner-scoped; ops sees platform aggregates only).
- Separation: membership refs are opaque per-creator keys (no cross-creator joinable identity); raw-hit browsing restricted to ops dispute work per the analytics posture.

## Acceptance criteria

- Segment drill: fixture event streams produce exact membership (entry on qualifying event, expiry at lookback end, exclusion logic verified).
- Consent drill: marketing-opted-out abandoner receives lifecycle reminder only; promo variant refused by eligibility with the reason logged.
- Export-refusal drill: custom-audience upload attempted in v1 → 404/422 (surface absent, not silently stubbed).
- Fatigue drill: over-cap recipients suppressed across surfaces; past-buyer exclusion holds; cause + one-click stop present on every message.
- Lift drill: flight with 10% holdout reports conversion vs control; report reconciles to underlying events within dedupe tolerance.

## Out of scope

- Inventory, decisioning, impression counting, revenue share (`monetize/advertising/ad-inventory.md`, `ad-serving-measurement.md`, `creator-revenue-share.md`); broadcast mechanics (`monetize/marketing/campaign-broadcasts.md`); consent classes (`monetize/marketing/consent-preferences.md`).
- Lead capture/search (`docs/leads/`); third-party pixels, data-broker integrations, cross-site tracking (never in v1 — contradiction with the privacy posture, flagged if ever requested).

## Open questions

- Q1: Real-time bar — minutes-scale confirmed, or is hourly aggregation enough for v1 retargeting value?
- Q2: Off-platform audience export (v2) — hashed-email uploads under explicit per-use consent, or never (first-party-only as permanent posture)?
