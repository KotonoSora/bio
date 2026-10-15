# Realtime Global Tier (live clicks, visitors, locations, sales)

PLAN ref: P1d-2 (pro-gated realtime-30-min via KV window). Live sales figures depend on P2–P3 order data and render only where that data exists — no shop stubs are built here.

## Purpose

Show what is happening right now: live clicks, current visitors, their locations, and — once shop/monetize data exists — live sales performance. Realtime is a pro-gated operational view (is my launch working this minute?), not a second counting system; billed or reported totals always come from the hourly aggregates, never from this feed.

## Users

- Pro creator: *As a Pro creator, I want to watch launches, drops, and live events minute-by-minute so that I know whether a launch is working right now.* Watches launches, drops, and live events minute-by-minute (now-line, top active links, live location map, sales tick where available).
- Free creator: *As a Free creator, I want the realtime panel as a gated teaser with an upgrade path so that I retain full historical analytics while seeing what live detail exists.* Sees the realtime panel as a gated teaser (locked state with upgrade path), retaining full historical analytics.
- Visitor: *As a Visitor, I want the live feed to read counters the beacon path already maintains so that no extra visitor-side work slows my visit.* Unaffected — the live feed reads KV counters the beacon path already maintains; no extra visitor-side work.

## Functional requirements

1. Live window: rolling last-30-minutes feed — current visitors (KV-deduped active keys), clicks-per-minute now-line, top active links, live city/country list. Refresh via short-poll (15–30 s cadence); no websocket in v1 (edge-cacheable polling beats stateful sockets at our scale).
2. KV sliding-window mechanics: beacon consumer increments per-profile minute-buckets in `RATE_LIMIT`-adjacent KV counters with short TTL; reads merge the last 30 buckets. PG is never touched on read or write in this path — realtime degrades to "temporarily unavailable" rather than loading the database.
3. Live locations: country/city of active visitors from the same coarse signals as historical geo (CF `cf-ipcountry` + city); same privacy ceiling (city-level, no IPs, no cross-profile stitching).
4. Live sales performance (conditional): where P2–P3 order aggregates exist for the profile, the panel adds revenue-now-line + units-per-link ticks sourced from order rollups, clearly labeled with their lag (order pipeline lag, not beacon lag). Where no order data exists, the sales strip is absent — never zero-filled in a way that implies no sales.
5. Pro gating + fair use: endpoint 403s for free plans (with the locked-panel UX); per-profile poll throttling prevents dashboard-tab farms from becoming a KV DDoS vector; global rollups (platform-wide live totals) are ops-only, never creator-visible.
6. Authority rule: any discrepancy between live feed and hourly aggregates resolves in favor of the aggregates; the UI states this once in the panel footnote so screenshots of the now-line never become billing disputes.

## Data / API touches

- No new PG tables. KV minute-buckets `rt:{profile_id}:{minute}` (views, clicks, per-link top-N, geo top-N; TTL ~1 h) written by the existing beacon consumer path and read by `GET /api/v1/analytics/realtime` (pro, throttled, `WHERE user_id = session.userId` equivalent owner check). Sales ticks read existing order rollups owned by shop/monetize docs — referenced, not redefined.
- Poll contract: `GET /api/v1/analytics/realtime?profile=` returns window snapshot `{generated_at, window_s:1800, visitors, cpm_series, top_links, geo, sales?}` with `Cache-Control: private, max-age=10`.
- Degradation: KV outage or consumer lag returns `status:"degraded"` with last-good timestamp instead of erroring the whole dashboard.

## Acceptance criteria

- Liveness drill: scripted visits appear in the feed within two poll cycles; minute-buckets expire so a quiet profile reads zero, not stale highs. *GWT: Given scripted visits and then a quiet profile; When the feed is polled; Then visits appear within two poll cycles and expired minute-buckets read zero, not stale highs.*
- Gating drill: free-plan direct API call returns 403; global-rollup endpoint refuses creator credentials. *GWT: Given a free-plan direct API call and a creator credential on the global-rollup endpoint; When evaluated; Then the realtime call returns 403 and the global rollup refuses creator credentials.*
- Load drill: 10x poll burst throttled without KV error-rate movement; PG write count unchanged (zero) with realtime on vs off. *GWT: Given a 10x poll burst with realtime on; When throttling, KV error rate, and PG writes are measured; Then the burst is throttled with no KV error-rate movement and PG writes stay zero.*
- Sales-conditional drill: profile without order data shows no sales strip; profile with data shows ticks matching order rollups within stated pipeline lag. *GWT: Given a profile with and without order data; When the panel renders; Then the profile without data shows no sales strip and the profile with data shows ticks matching order rollups within stated pipeline lag.*
- Authority drill: Reds-vs-Blues fixture where live and aggregate briefly differ — panel footnote and support runbook both cite aggregates as truth. *GWT: Given a Reds-vs-Blues fixture where live and aggregate briefly differ; When the panel footnote and support runbook are read; Then both cite aggregates as truth.*

## Out of scope

- Historical/audience analytics (see `analytics/profile-analytics.md` and `analytics/audience-insights.md`); earnings + engagement unification (see `analytics/earnings-dashboard.md`); mobile/PWA delivery (see `analytics/mobile-pwa.md`).
- Order/billing tables and grant mechanics (their own majors — consumed here, never redefined); native apps and push alerts (PWA + deferred); agency/global leaderboards (P4).

## Open questions

- Q1: Alert thresholds (e.g. "notify me at 1k live visitors") — v2 realtime feature, or P4 automation territory?
- Q2: Poll vs SSE at scale — revisit server-sent events only when polling cost evidence demands it?
