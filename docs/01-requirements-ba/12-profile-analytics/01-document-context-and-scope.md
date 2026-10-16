# Profile Analytics - Context and Scope (feature 12)

Phase 1 BA · Feature 12 of 53 (link-to-bio; opens the analytics major) · Implemented by `docs/02-architecture-techlead/12-profile-analytics/` (6 files). New service: `analytics-service` (owns 12-15). Beacon transport defined here closes feature 11's forward contract (banner/install events).

DEVIATION FLAGGED (first use): the legacy verdict "monolith with async in-process pipeline" is superseded — the mandated microservices stack places intake, rollup, and reads in a dedicated `analytics-service` with its own database (`analytics_db`). The legacy extraction trigger (beacon ingest extracts at P4) is met by construction. Rationale: stack mandate, database-per-service, RLS retained.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Link.me-parity analytics: views, clicks, engagement, per-link and period comparisons, sources, countries | Seeded-traffic drill reproduces every figure (AC-04) |
| BO-2 | Viral traffic stays cheap: PG write volume scales with profiles x days, never with page views | k6 soak at 10x beacon rate shows flat PG write rate, zero per-hit writes (AC-01, AC-11) |
| BO-3 | Honest freshness: hourly aggregates are labelled as such everywhere | Lag < 1 h at gate; stale-chip drill green (AC-03) |
| BO-4 | Transparent visitor counting: no login, no consent beyond the platform privacy notice, no raw IPs at rest | Privacy drill + code audit (AC-07) |
| BO-5 | Plan-correct access: free 7/30/90d ranges + 90d retention; pro extends; clamp UX never dead-ends | Clamp drill green (AC-08) |

## In scope

- Public beacon `POST /api/t/:username` (view / click / banner / prompt events): edge challenge + rate rules, 10-minute dedupe, crawler denylist, zero PG writes in the handler.
- Queue + hourly rollup into `profile_views` and `link_clicks`; raw NDJSON archive (90-day TTL) in blob storage.
- Owner reads: `GET /api/v1/analytics/overview|links?range=` with plan clamping, period deltas, daily series, per-link table, source/country groups.
- Dashboard overview tab (`/app/analytics/overview`): widgets, range picker, lag honesty, empty/degraded/error states, locked realtime teaser.
- Ops surfaces: lag, abuse, rejection metrics; k6 soak gate.

## Out of scope

- Audience depth (new/returning, devices, UTM grain, city detail) — feature 13; realtime window + panel — feature 14; unified earnings — feature 15.
- Public SSR/cache (feature 10), block/theme authoring (08, 09), leads/billing/agency internals, P5 replicas.

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Visitor (no account) | Create (beacon only, fire-and-forget) | Visit counted transparently; no login, no UI |
| Creator (free) | Read own overview/links; ranges 7/30/90d | Learns from sources and countries; realtime shown as a locked teaser |
| Pro creator | Read extended ranges (to 365d) + fuller retention | Same endpoints, wider window |
| Ops / platform | Read ops dashboards + metrics only; no customer data reads | Watches lag, abuse, rejection reasons |

## Open questions (v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Crawler filtering | denylist default-on, counted (`beacon.rejected{bot}`) not stored; no UI toggle | provenance demand from creators |
| Q-02 | UTM depth | captured and forwarded to audience tables (feature 13); not stored in 12 | 13's UTM grain ships |
| Q-03 | Raw archive write timing | batched at the hour drain (one object per drain) | forensic latency need < 1 h |
| Q-04 | Geo grain | country only, from the edge header; the legacy "city" label read `cf-ipcountry`, a country header — grain now stated honestly as country | geo enrichment provider contracted |

Related specs: beacon event types `docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`; public envelope `docs/02-architecture-techlead/10-share-and-seo/01-architecture-and-tech-stack.md`; resolve + blocks internal reads `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`, `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md`; plan windows and prune job `docs/01-requirements-ba/13-audience-insights/`.
