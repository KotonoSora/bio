# Profile Analytics (queued views + clicks)

PLAN ref: P1d-1, P1d-2. Public page rendering lives in `public-page/share-seo.md`.

## Purpose

Define link.me-parity analytics for Link-to-Bio pages without ever writing to Postgres per hit. A lightweight beacon feeds a queue; an hourly consumer rolls up aggregates the dashboard reads. This keeps viral traffic cheap (cache + queue) instead of scaling the database with page views.

## Users

- Creator: *As a Creator, I want to see views, clicks, engagement, and per-link and per-period comparisons so that I learn from sources and cities without scaling the database with page views.* Sees views, clicks, engagement, per-link and per-period comparisons, sources/cities.
- Pro creator: *As a Pro creator, I want a realtime last-30-minutes window in addition so that I can watch current activity beyond historical aggregates.* Additionally sees a realtime last-30-minutes window.
- Visitor: *As a Visitor, I want to fire the beacon transparently so that my visit is counted with no login and no consent beyond the platform privacy notice.* Fires the beacon transparently; no login, no consent beyond the platform privacy notice.

## Functional requirements

1. Beacon: `POST /api/t/:username` on page view and link click (payload: block id for clicks, referrer, coarse client hints). Handler responds fast, performs zero PG writes, and is Turnstile + rate-limit protected.
2. Dedupe: KV-based 10-minute dedupe per (visitor, page/block) before enqueue, so refreshes and double-taps do not inflate counts.
3. Pipeline: beacon → `BEACON_Q` → hourly consumer upserts `profile_views{day, views, interactions}` + `link_clicks{day, count}`. Raw hit logs archive to R2 NDJSON with 90-day TTL; PG holds aggregates only.
4. Dashboard metrics: totals + period-over-period compare, per-link clicks, engagement (clicks/views), source (Referer) and city (CF `cf-ipcountry`), daily series. Realtime-30-minutes panel is pro-gated via a KV sliding window, not PG.
5. QR scans need no separate instrumentation: QR encodes the same `/:username` URL, so scans arrive as ordinary views.
6. Privacy: city-level geo only, no raw IP storage in PG; raw logs age out with the R2 TTL.

## Data / API touches

- New tables (all with standard `user_id TEXT NOT NULL` + index + RLS): `profile_views{profile_id, day, views, interactions}`, `link_clicks{profile_id, block_id, day, count}`. Raw NDJSON lives in R2, never PG.
- Endpoints: `POST /api/t/:username` (public, throttled), `GET /api/v1/analytics/overview?range=`, `GET /api/v1/analytics/links?range=`, `GET /api/v1/analytics/realtime` (pro, KV-backed). Dashboard reads scope `WHERE user_id = session.userId`.
- Queue/consumer: `BEACON_Q` batch consumer runs hourly; lag target <1 h at gate. Consumer upserts are idempotent on `(profile_id[, block_id], day)` so replays do not double-count.

## Acceptance criteria

- Load test: sustained hits produce zero per-hit PG writes (verified via query logs); consumer lag <1 h; aggregates match archived raw counts within dedupe tolerance. *GWT: Given sustained hits; When query logs, consumer lag, and aggregates are checked; Then zero per-hit PG writes holds, lag is <1 h, and aggregates match archived raw counts within dedupe tolerance.*
- Dashboard shows views, clicks, engagement, period compare, per-link table, source/city breakdown; free plan excludes the realtime-30-min panel (403 via direct API call). *GWT: Given a free plan calling the realtime-30-min endpoint directly; When the request is evaluated; Then it returns 403 and the dashboard shows views, clicks, engagement, period compare, per-link table, and source/city breakdown without that panel.*
- Replay of the same queued message: single aggregate increment (idempotent upsert). *GWT: Given the same queued message replayed; When the consumer upserts; Then a single aggregate increment results.*
- Abuse probe: rapid beacon bursts throttled (Turnstile + WAF/rate rules); tampered `username`/block ids rejected without writes. *GWT: Given rapid beacon bursts or tampered `username`/block ids; When the beacon handler evaluates them; Then bursts are throttled and tampered ids are rejected without writes.*
- IDOR probe: user A analytics reads for user B profile return 403; RLS denies cross-user rows at DB. *GWT: Given user A requesting user B profile analytics reads; When evaluated; Then the API returns 403 and RLS denies cross-user rows at DB.*
- QR scan of `/:username` records exactly one deduped view like any direct visit. *GWT: Given a QR scan of `/:username`; When the beacon pipeline processes it; Then exactly one deduped view is recorded like any direct visit.*

## Out of scope

- Public page SSR/SEO/cache (see `public-page/share-seo.md`); block authoring (see `blocks/link-blocks.md`).
- Leads capture/CSV and campaign stats (separate majors / P4 broadcast); billing and plan-gate internals beyond the realtime check (P3); agency leaderboard views (P4); replicas and ISR (P5).

## Open questions

- Q1: Bot/crawler filtering — exclude known crawlers from views v1, or count all hits?
- Q2: Attribution depth — UTM passthrough stored per click v1, or source/city only?
