# Realtime Global - Database Schema Design: Zero-Schema Posture (feature 14)

Phase 2 TL · BA sources: `docs/01-requirements-ba/14-realtime-global/02-user-stories-and-business-rules.md` (BR-02, BR-03, BR-04), `docs/01-requirements-ba/14-realtime-global/04-ui-data-dictionary.md`. Base tables: `docs/02-architecture-techlead/12-profile-analytics/02-database-schema-design.md`; audience tables: `docs/02-architecture-techlead/13-audience-insights/02-database-schema-design.md`.

## Assertion: zero PG surface

- `analytics_db` keeps migrations V1-V2 exactly as shipped by 12-13. No V3; no `CREATE TABLE`, index, or RLS policy for feature 14 - review greps the migration set for DDL and fails any hit.
- Rationale: a PG table for realtime puts per-minute writes on the hottest path (one statement per accepted hit). The cache layer answers the window in bounded O(30) reads, and it is allowed to lose data - expiry reads as a correct zero. Anything that must survive is already hourly-aggregated by 12-13.

## Ephemeral key contract (the only schema)

| Key | Type | Fields | TTL | Written by |
|---|---|---|---|---|
| `rt:{profileId}:{yyyyMMddHHmm}` (UTC) | hash | `v` accepted views; `c` accepted clicks; `l:{blockId}` clicks per block (cap 16); `g:{CC}` views per country (cap 10) | 3600 s, refreshed per write | Intake, post-accept only |
| `rtpoll:{profileId}:{userId}` | string | last accepted poll instant (ms) | 60 s | Read throttle |
| `rtgood:{profileId}` | string | last successful merge instant | 86400 s | Successful merge |

Rules:

- Accept-only increments: dedupe collapses, bot filters, throttle rejections, unknown targets, and unknown blocks never increment - buckets inherit 12's decision; no second filter exists.
- Trim-on-new-field: a new `l:*` field above 16 (or `g:*` above 10) triggers a small `HGETALL` of the bucket; the lowest-count field is deleted (ties: lexicographic smallest key). Cost runs only when a new distinct key appears in a minute, and bounds every bucket to <= 28 fields.
- Missing, expired, evicted, or cold-start buckets count as zero; never backfilled from aggregates (different grain, different truth).

## Sizing and eviction

- Working set bound = active profiles x 60 buckets x <= 4 KB (28 fields worst case); the bound tracks active profiles, never total traffic - dedupe and minute-grain collapse hits before they reach buckets.
- Cache loss and eviction are non-events by design: the next read recounts from surviving buckets; nothing is reconstructed from PG.

## Drills (DB-side gates)

| Drill | Assertion |
|---|---|
| Violation | Grep: zero realtime DDL anywhere; query log: zero PG statements on realtime read/write paths |
| Expiry | Scripted visits then silence -> reads decay to zero within window + TTL; no stale highs |
| Isolation | Profile A's buckets unreadable from profile B's poll; key embeds profile id; owner check precedes merge |
| Sizing | Load run: bucket field count <= 28, key size <= 4 KB on a max-block (250-block) profile |
| Cache-down | Merge failure -> `degraded` + `lastGoodAt`; PG write counter unchanged with realtime on vs off |

## Out of scope

- Bucket writer, trim logic, poll endpoint (`03-api-contracts-and-payloads.md`, `04-step-by-step-implementation-plan.md`); panel UI; hourly aggregates (12-13); order rollup storage (shop/monetize majors - referenced, never redefined).
