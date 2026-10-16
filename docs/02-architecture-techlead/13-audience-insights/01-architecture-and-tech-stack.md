# Audience Insights - Architecture and Tech Stack (feature 13)

Phase 2 TL · BA sources: `docs/01-requirements-ba/13-audience-insights/` (7 files). Base pipeline: `docs/02-architecture-techlead/12-profile-analytics/01-architecture-and-tech-stack.md`. Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary: in-service read-side extension

No new service, container, queue, or endpoint surface for ingestion. Feature 13 extends `analytics-service` (12) inside the same compose slice.

| Component | Change | Notes |
|---|---|---|
| Intake handler (12) | Adds returning check + 3 additive queue fields | One extra cache lookup; no new port, no PG write |
| `RollupConsumer` (12) | Calls `SegmentRollup` in the same transaction | Same batch window, same idempotency semantics |
| `AudienceController` (new class) | Two owner reads | Same gateway route namespace `/api/v1/analytics/*` |
| `AggregateRetentionJob` (new class) | Nightly 02:30, base + audience tables | Runs on the scheduler replica (`ROLLUP_ENABLED=true`, 12 D-7) |
| `analytics_db` | Flyway V2 (two tables) | Same database, database-per-service intact |
| Compose / build.gradle.kts | Unchanged — no new deps (scheduling, JPA, Redis already present) | - |

## Key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | In-service extension, not a separate insights service (legacy verdict "monolith read-side extension" is satisfied by construction) | Re-consuming the beacon stream would create dual-lag/dual-replay semantics for a read-only projection; no split trigger fires |
| D-2 | Returning check at intake: `seen:{profileId}:{visitorKey}` TTL 30d; HMAC uses `RETURNING_SALT_MONTHLY`, separate from the daily dedupe salt | Match span must exceed the daily-salt rotation; after rotation, boundary visits count `new` (privacy-first, documented) |
| D-3 | Queue vocabulary extended additively: `utm`, `browser`, `returning`; `v` stays 1; absent fields fall back (utm chain, `browser=other`, `returning=new`) | Feature 12's governance rule; atomic compose deploy makes absent fields a replay-only concern |
| D-4 | CTR stored per-mille integer (`ctr_x1000`), window sums at read, round-half-up | One formula everywhere (BR-05, BR-15); integer avoids float drift |
| D-5 | No new scheduler flag: retention joins the existing scheduler replica | One job replica platform-wide per service, 12 D-7 |
| D-6 | `dataStart` computed per read from indexed `min(day)` | Overlay enablement needs no watermark cache to invalidate |
| D-7 | Entitlement resolved per request via the platform snapshot; plan-change invalidation; max staleness 30 s stated | Downgrade re-clamps immediately (BR-07); 09's snapshot pattern reused |

## Reuse map

- Pipeline, dedupe, queue, lag honesty: `docs/02-architecture-techlead/12-profile-analytics/`.
- Beacon body (`utm`, hints) already reserved by 12's contracts; micro-script is unchanged.
- Device read = feature 11's UA-mix banner eligibility; CTR = feature 15's earnings reuse.
- Retention numbers (free 90d / pro 365d) pinned by 12's DB file, executed here.
