# Advanced Insights (audience + click depth)

PLAN ref: P1d-2 (extends the pipeline in `analytics/profile-analytics.md`; no new ingestion — same beacon, same queue, deeper reads).

## Purpose

Turn raw views and clicks into audience understanding: who returns, what devices they use, where they come from, and which links earn their taps. Basic analytics answers "how many"; this tier answers "who and why" — without new tracking endpoints or per-hit database writes.

## Users

- Creator: segments visitors (new vs returning, device, city/country, referrer, UTM campaign), compares periods, ranks links by click-through rate.
- Pro creator: longer history windows and UTM-level breakdowns (gating below).
- Visitor: counted exactly as before — no additional beacons, cookies, or fingerprinting beyond the base pipeline.

## Functional requirements

1. Audience splits (all derived from archived beacon fields + KV dedupe keys, never new PII): new vs returning (dedupe-key seen-before within window), device class + browser family (coarse client hints only), top countries/cities (CF `cf-ipcountry` + city where present), referrer domains grouped (social/search/direct/QR-scan class).
2. Click insights: per-link CTR (clicks ÷ profile views in-window), per-link rank with delta vs previous period, top-link Movers table (biggest gainers/fallers). CTR defined once here and reused by the earnings dashboard — one formula everywhere.
3. UTM attribution: `utm_source/medium/campaign` captured on the beacon (query passthrough, stored in the archived hit only) and rolled into per-campaign aggregates by the hourly consumer. Links without UTMs still attribute to referrer-group + scan-class fallbacks — no "unknown" dead-ends where a fallback exists.
4. History windows by plan: free 90 days of daily aggregates, pro 365 days + hourly granularity for the last 7 days. Enforcement is server-side on the read endpoints (range clamping), not UI-only.
5. Period compare: every metric supports previous-period and year-ago overlays with absolute + percentage deltas; compare windows align on weekday where day counts differ (documented in-UI so Monday spikes are not misread).
6. Privacy ceiling: city-level geo only, no raw IPs in PG, no cross-profile visitor stitching (a visitor to two profiles is two strangers). Raw hits age out with the R2 90-day TTL per the base doc.

## Data / API touches

- New aggregate tables (standard `user_id TEXT NOT NULL` + index + RLS; idempotent consumer upserts like the base doc): `traffic_segments{profile_id, day, dimension (device|geo|referrer|utm), key, views}`, `link_perf{profile_id, block_id, day, clicks, ctr_x1000}`. No beacon or queue changes — the hourly consumer computes these from the same messages.
- Endpoints: `GET /api/v1/analytics/audience?range=&dimension=`, `GET /api/v1/analytics/links/performance?range=` (range clamped by plan). Dashboard reads scope `WHERE user_id = session.userId`.
- Retention job: aggregates older than the plan window roll off PG on schedule (pro-to-free downgrade re-clamps reads immediately; aged-out rows are not resurrected on re-upgrade).

## Acceptance criteria

- Parity drill: seeded traffic reproduces hand-computed new/returning, device, geo, UTM, and CTR figures exactly within dedupe tolerance.
- Gating drill: free account requesting 365-day or hourly-granularity reads gets clamped (206/422 with the allowed range stated); direct API call cannot bypass.
- No-regression drill: beacon payload size and queue throughput unchanged vs the base pipeline; zero per-hit PG writes still holds under load.
- Privacy drill: no raw IP in any PG row; cross-profile stitching query returns nothing by construction (spot-checked in review).
- Period-compare drill: weekday-aligned overlays verified against fixture data with known spikes.

## Out of scope

- Beacon, dedupe, and queue mechanics (see `analytics/profile-analytics.md`); live/real-time views (see `analytics/realtime-global.md`); earnings + unified dashboard (see `analytics/earnings-dashboard.md`); mobile/PWA delivery (see `analytics/mobile-pwa.md`).
- Leads capture/CSV, campaign stats (separate majors / P4); billing internals beyond range gating (P3); agency leaderboards (P4).

## Open questions

- Q1: Hourly granularity for free (last 24 h) — conversion teaser, or pro-only entirely?
- Q2: UTM allowlist — freeform values, or creator-defined campaign registry to prevent typo-split campaigns?
