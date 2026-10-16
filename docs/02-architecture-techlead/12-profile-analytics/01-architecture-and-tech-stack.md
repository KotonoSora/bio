# Profile Analytics - Architecture and Tech Stack (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/` (7 files). Stack pins: `docs/02-architecture-techlead/00-platform-baseline/` (Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1, PG 18.6, React 19.3, Compose, Redis; files on the uploads volume served by Nginx). New service boundary for the analytics major.

## Service boundary

| Component | Owns | Runtime | Notes |
|---|---|---|---|
| `analytics-service` (new) | Beacon intake, dedupe, queue, hourly rollup, owner reads, dashboard data | Boot 4.1.1 / Kotlin 2.4.21, port 8085 | Owns features 12-15; stateless intake replicas + one scheduler replica |
| `analytics_db` | `profile_views`, `link_clicks` (Flyway V1) | PostgreSQL 18.6 | Database-per-service; RLS on; no cross-service FKs |
| Redis db 6 | Streams `beacon:q` + `links.hits`, dedupe keys, resolver cache, rate tokens | Platform Redis | Queue + cache substrate per stack mandate |
| Uploads namespace `analytics/raw` | Raw NDJSON files (90-day retention sweep) | Platform uploads volume | Never queried by dashboards; ops forensics only |

Compose delta: one service container (8085) + env (DB URL, Redis db 6, uploads mount, `ROLLUP_ENABLED`). No new infrastructure containers — Redis and the Nginx edge already exist in the platform compose slice.

## Topology and key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Microservices translation of the legacy "monolith + in-process pipeline" verdict (DEVIATION FLAGGED in BA 01): intake, rollup, reads all live in `analytics-service` | Stack mandate; database-per-service; the legacy extraction trigger is met by construction |
| D-2 | Queue is a Redis stream `beacon:q` with a consumer group; durability per platform baseline (AOF) | Redis is the mandated queue substrate; at-least-once + idempotent upserts = exactly-once counts |
| D-3 | Raw NDJSON flush is batched at the hour drain (one file per drain), not per hit | Keeps intake O(1) Redis ops; storage-volume outage cannot touch intake |
| D-4 | Geo comes from an edge-set country header (`X-Geo-Country`); no IP-derived city in v1 | BA Q-04; honest grain, no IP at rest |
| D-5 | Prompt/install outcomes ride the beacon for metrics counting only (no aggregate table v1) | Feature 11's contract; avoids schema creep without a read surface |
| D-6 | Block titles resolve live via the profile-service windowed blocks read, cached 300 s; deleted blocks return `title: null` | Titles are not duplicated into analytics; history survives deletions |
| D-7 | One image; scheduler replica runs with `ROLLUP_ENABLED=true`, intake replicas `false` | No session affinity; horizontal intake scaling |
| D-8 | Hikari sized for hourly batch upserts (never per-hit connections); 5 s statement timeout | Load design is structural: PG sees ~1 write batch/hour |

## Build and integration

- `build.gradle.kts`: starters web, validation, data-jpa, security, scheduling, cache, actuator, micrometer + Flyway, spring-data-redis; Kotlin DSL, wrapper-only `9.8.1`.
- Gateway routes: `/api/t/*` public (edge rate rules + optional challenge token claim); `/api/v1/analytics/*` authenticated (OAuth2 session -> internal JWT with server-set `user_id`).
- Dashboard integration: `/app/analytics/overview` uses the platform SPA fetch conventions; analytics chunk route-code-split (feature 11's responsive rules apply).
- Internal calls: `GET /internal/v1/profiles/{username}` (resolve, redirects followed) and the blocks windowed read, both cached in Redis 300 s.

## Reuse map

- Public beacon caller: feature 11 micro-script (`docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`).
- Resolve + blocks internal reads: features 06 / 08.
- Plan windows + prune job: feature 13 (`docs/01-requirements-ba/13-audience-insights/` pending); realtime endpoint: feature 14; earnings reads: feature 15.
- Edge challenge, rate rules, session->JWT: `00-platform-baseline`.
