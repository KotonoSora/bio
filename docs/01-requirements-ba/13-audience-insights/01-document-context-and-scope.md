# Audience Insights - Context and Scope (feature 13)

Phase 1 BA · Feature 13 of 53 (link-to-bio, analytics) · Implemented by `docs/02-architecture-techlead/13-audience-insights/` (5 files). Extends `analytics-service` (feature 12) — no new service, database, queue, or beacon. Feeds feature 11's mobile arrangement (device-mix read) and feature 15's CTR reuse.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Answer "who and why" beyond "how many": new vs returning, device, geo, referrer, UTM, CTR, rank, Movers | Parity drill reproduces hand-computed figures within dedupe tolerance (AC-01) |
| BO-2 | Zero new tracking: same beacon, same queue; no additional beacons, cookies, or fingerprinting for visitors | No-regression drill: payload and throughput unchanged; zero per-hit PG writes hold (AC-03) |
| BO-3 | One CTR formula platform-wide (`ctr_x1000`, per-mille integer), reused by the earnings dashboard | Single-formula drill; mismatched-window division fails review (AC-08) |
| BO-4 | Plan-correct history: free 90d, pro 365d; enforcement server-side; downgrade re-clamps immediately; aged rows never resurrect | Gating + retention drills green (AC-02, AC-06) |
| BO-5 | Privacy is structural: no IP/visitor-id columns exist; cross-profile stitching is unqueryable | Privacy drill: column-list assertion + stitching query returns nothing (AC-04) |

## In scope

- `traffic_segments` + `link_perf` tables (Flyway V2 in `analytics_db`); consumer projections (segment rollup, CTR) inside the existing hourly rollup.
- Reads: `GET /api/v1/analytics/audience?range=&dimension=` and `GET /api/v1/analytics/links/performance?range=`, owner-scoped, plan-clamped.
- `AggregateRetentionJob` covering base tables (feature 12) and these tables: free 90d, pro 365d; dry-run metric first.
- Overview wiring: feature 12's `sources[]`/`countries[]` arrays are now populated from segment tables (contract unchanged).
- Dashboard audience tab: dimension tabs, CTR/rank/Movers, compare overlays, clamp-back, cardinality handling, honesty states.

## Out of scope

- Beacon, dedupe, queue mechanics (feature 12); live/realtime (feature 14); earnings unification (feature 15); PWA delivery (feature 11).
- Mobile/PWA delivery, leads capture/campaign stats (P4 majors), billing internals beyond range gating, agency leaderboards (45+), UTM campaign registry (deferred, see Q-02).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Visitor (no account) | Create (counted exactly as before) | No new beacons, cookies, or fingerprints follow the visit |
| Creator (free) | Read own audience/perf within 90d | All dimensions visible inside the window; clamp-back UX on plan edges |
| Pro creator | Read own audience/perf within 365d | Longer history; hourly granularity lives in feature 14 |
| Ops / platform | Read metrics/dashboards only | Watches retention, cardinality, rollup health |

## Resolved open questions

| # | Question | v1 resolution | Revisit trigger |
|---|---|---|---|
| Q-01 | Free hourly teaser (last 24h) | Pro-only; hourly granularity is feature 14's pipeline; free keeps feature 12's locked realtime teaser | after 14 ships |
| Q-02 | UTM registry vs freeform | Freeform with normalization + caps (BR-10) and a cardinality alert; registry deferred to the campaigns major | campaigns registry ships |
| Q-03 | `Other` row threshold | Fixed top-25 + server-side `Other (n)` | creator demand for adjustable depth |
| Q-04 | Low-traffic CTR floor | Fixed `not enough data` under 30 views (not tiered) | plan-tiered honesty request |

Notes: "scan-class" referrers do not exist — QR codes encode the bare bio URL (feature 12 BR-11), so scans count as `direct`; no QR instrumentation is added. Year-ago overlay is deferred with a visible reason (BR-06, DEVIATION FLAGGED there).

Related specs: base pipeline `docs/01-requirements-ba/12-profile-analytics/`; beacon/mobile contract `docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`; earnings reuse `docs/01-requirements-ba/15-earnings-dashboard/`; platform pins `docs/02-architecture-techlead/00-platform-baseline/`.
