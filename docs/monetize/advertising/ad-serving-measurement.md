# Ad Serving + Measurement (decisioning, counting, fraud filters)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `advertising/ad-inventory.md` for placement). Inventory and safety there; creator earnings in `advertising/creator-revenue-share.md`.

## Purpose

Define how an ad gets chosen for a slot and how its performance is counted honestly: a fast, cacheable decision path plus an impression/click pipeline that is fraud-filtered, idempotent, and fully separated from creator analytics. Advertisers pay (or evaluate) on these numbers and creators earn on them — so counting must be defensible under dispute, not approximate.

## Users

- Visitor: loads a page with an ad slot; the slot resolves in milliseconds or collapses gracefully.
- Advertiser: reads flight reports (impressions, clicks, CTR) they can trust enough to renew.
- Dispute reviewer: replays any billed event from stored evidence.

## Functional requirements

1. Decisioning (per slot render, server-side, cached): eligible flights = live window + placement match + creator opt-in + category compatibility both directions + cap headroom; selection = weighted by flight priority/caps with deterministic tie-breaks (no real-time bidding in v1). Decision cached per (placement, coarse segment) with short TTL; empty result collapses the slot (no blank boxes, no layout shift).
2. Measurement pipeline (mirrors the analytics beacon discipline, separate tables): ad-view pings and ad-click redirects flow through a dedicated endpoint into a queue (never direct PG writes), KV-deduped (10-min window per visitor/slot/creative, same pattern as `link-to-bio` beacons), then rolled into `ad_impressions{day, flight, placement, segment}` + `ad_clicks{day, flight, placement}` aggregates; raw logs to R2 NDJSON with the same TTL discipline. Creator `profile_views`/`link_clicks` tables are never touched — enforced by separate code paths, verified by the separation drill.
3. Fraud filtering (v1 essentials, stated plainly): bot/crawler filtering (at minimum the same exclusion posture decided for analytics — see that Q1; ad counting defaults to excluding known bots), impression visibility minimum (slot in viewport per IntersectionObserver signal — unverified views don't count), click anti-double (dedupe window + post-click landing validation), and velocity anomaly flags (single-source floods quarantined from billing aggregates pending review).
4. Evidence bundle per billed event (creative, flight, placement, segment, timestamps, dedupe outcome) retained for dispute replay; aggregates recomputed only from filtered events, with filter counts themselves reported (advertisers see what was excluded and why — transparency renews contracts).
5. Failure behavior: decisioning timeout/error → slot collapses (page never waits on ads); measurement outage → events queue with bounded retry, then the same sweep-and-repair discipline as money paths (no silent loss, no double count via the dedupe keys).
6. Performance budget: ad decision + render adds a fixed millisecond budget to page SSR (see Q2); measurement pings are fire-and-forget beacons, never render-blocking.

## Data / API touches

- New tables `ad_impressions`/`ad_clicks` aggregates (mirroring the analytics aggregate shape) + raw R2 NDJSON; decision config cached in KV/CDN. Click redirect endpoint (public, throttled, signed creative reference — tampered refs rejected). All PG via `worker/db/*`; zero per-hit PG writes.
- Endpoints: internal decide API (server-to-server), public `GET /api/a/:ref` click redirect (throttled), ops `GET /api/v1/ops/ad-reports/:flight` (aggregates + filter transparency). Advertiser report reads scoped to own flights.
- Consent posture follows the analytics model (first-party only, no cross-site identity); declined-consent visitors see contextual-only ads or collapsed slots per config, never blocked pages.

## Acceptance criteria

- Decision drill: eligibility matrix (window/placement/opt-in/category/caps) verified per combination; empty inventory collapses slots with zero layout shift.
- Counting drill: refresh/double-click storms dedupe to one; bot traffic excluded per the decided posture; below-visibility impressions uncounted; click floods quarantined with billing aggregates clean.
- Separation drill (under load): zero ad rows in creator analytics; zero creator rows in ad aggregates.
- Dispute drill: any billed impression/click replayed from its evidence bundle; filter-exclusion report matches raw-minus-aggregate math.
- Outage drill: measurement downtime recovers via queue + sweep with no loss and no doubles.
- Auth probe: advertiser A reading advertiser B reports → 403; RLS denies cross-scope rows.

## Out of scope

- Inventory, formats, and brand safety (see `advertising/ad-inventory.md`); creator earnings and payouts (see `advertising/creator-revenue-share.md`).
- Creator analytics (`docs/link-to-bio/`); affiliate tracking (`affiliate/` — separate evidence chains); third-party ad-tech integrations (deferred); P5.

## Open questions

- Q1: Bot-exclusion posture — shared single decision with creator analytics, or stricter for billed ad traffic?
- Q2: SSR time budget for ad decisioning — fixed cap (e.g. 50 ms with collapse-on-timeout) confirmed?
