# Realtime Global - Context and Scope (feature 14)

Phase 1 BA · Feature 14 of 53 (link-to-bio, analytics) · Implemented by `docs/02-architecture-techlead/14-realtime-global/` (5 files). Extends `analytics-service` (features 12-13): ephemeral counter path, no new service, no PG tables. Closes feature 12's BR-13 forward contract (endpoint + 403 enforcement) and feature 13's Q-01 pointer.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Pro creators watch launches minute-by-minute: rolling 30-minute visitors, clicks-per-minute, top links, countries | Liveness drill: scripted visits surface within two poll cycles (AC-01) |
| BO-2 | Realtime is an operational view, never a second counting system: billed/reported totals always come from hourly aggregates | Authority drill: live/aggregate divergence fixture cited to aggregates (AC-05) |
| BO-3 | No database cost: every realtime read and write touches the cache layer only | Load drill: 10x poll burst, PG write count unchanged with realtime on vs off (AC-03) |
| BO-4 | Free plans keep full historical analytics and see a locked teaser with zero fetches | Teaser drill: zero realtime requests across desktop/mobile/PWA (AC-02) |
| BO-5 | Honest degradation: cache trouble shows a last-good banner, never fabricated freshness, never a PG fallback | Degraded drill green (AC-08) |

## In scope

- Rolling last-30-minutes window as UTC minute-bucket counters (`rt:{profile_id}:{minute}`, TTL 1h), incremented on accepted (post-dedupe, post-bot) hits by the existing beacon path.
- `GET /api/v1/analytics/realtime?profile=`: pro-gated, owner-checked, throttled short-poll with `status`/`lastGoodAt` degraded mode and `Cache-Control: private, max-age=10`.
- Now-line panel atop `/app/analytics/overview` (pro) and the locked teaser (free); poll lifecycle, backoff, hidden-tab discipline, aggregates footnote.
- Observability: `rt.write_pressure`, `rt.write_error`, `realtime.poll_throttled`, `rt.last_bucket_age`; P4 split signal recorded.

## Out of scope

- Historical/audience analytics (12-13); earnings unification (15); order/billing tables and grant mechanics (shop 22-26, monetize 32-40 - referenced only); native apps and push alerts (v2); agency/global leaderboards (45+); SSE/websockets (v1 is short-poll).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Pro creator | Read own realtime snapshot (throttled) | Watches launches, drops, live events minute-by-minute |
| Free creator | Read nothing realtime; locked teaser only | Retains full historical analytics; upgrade path visible |
| Visitor | None (no additional visitor-side work) | Live feed reads counters the beacon path already maintains |
| Ops / platform | Read ops metrics incl. platform-wide live totals | Global rollups are ops-only and never creator-visible; no creator-facing global endpoint exists |

## Resolved open questions

| # | Question | v1 resolution | Revisit trigger |
|---|---|---|---|
| Q-01 | Alert thresholds ("notify at 1k live visitors") | Out of v1; automation major (P4) territory | automation batch |
| Q-02 | Poll vs SSE/websockets | Short-poll only; SSE revisited solely on polling-cost evidence | polling-cost evidence |
| Q-03 | Window selectable (10/30/60 min)? | Fixed 1800 s window (bounded O(30) merge) | pro demand spike |
| Q-04 | Bucket TTL 1h? | Hold: covers window + skew + one missed tick | memory pressure at 1M-concurrent scale |
| Q-05 | Jitter ±5 s widen? | Hold; widen only with scale evidence | 1M-concurrent scale |
| Q-06 | Reconnect after 3 errors | Manual `Reconnect` button only; no auto-resume loops | usability feedback |

Boundary notes: DEVIATION FLAGGED - feature 13's files state "hourly granularity lives in feature 14" (RBAC row and Q-01). Feature 14's sources define no historical hourly storage: the live minute-grain window (TTL 1 h) is the only sub-daily grain that exists, so a historical hourly read over 7 days is deferred out of v1 and 13's pointer resolves here as the live 30-minute window. In this architecture the bucket writer rides intake, so the rollup consumer's lag does not gate the live window (it gates hourly aggregates only, feature 12's stale banner). Geo remains country-grain (DEVIATION CARRIED from feature 12 Q-04): the legacy "city" field has no source; labels say countries.

Related specs: base pipeline `docs/01-requirements-ba/12-profile-analytics/`; segment pipeline `docs/01-requirements-ba/13-audience-insights/`; teaser contract `docs/02-architecture-techlead/12-profile-analytics/04-api-contracts-and-payloads-part-2.md`; PWA same-numbers `docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`; platform pins `docs/02-architecture-techlead/00-platform-baseline/`.
