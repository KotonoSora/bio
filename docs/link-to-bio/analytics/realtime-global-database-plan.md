# Realtime Global Database Plan (ephemeral buckets — no PG tables)

PLAN ref: P1d-2 (pro-gated realtime-30min via ephemeral window; aggregates remain the billed truth) + privacy ceiling. BA source: `docs/link-to-bio/analytics/realtime-global.md`. Consumed by the realtime backend plan (bucket writer + short-poll reads; splits next to this file). Historical tables live in the profile/audience database plans. No migration — PG gains zero tables from this slice.

## Purpose

Document the ephemeral state that powers the live window: bucket key format, dimensions, TTLs, what happens on eviction or outage, and why none of it belongs in PG. A PG table for realtime would put per-minute writes on the hottest path — this file exists to forbid that with the reasoning attached.

## Users

- Backend developer: writes/reads buckets only through the cache layer with the exact key contract below.
- QA: asserts expiry (quiet profiles read zero, not stale highs) and degraded mode.
- Ops: monitors bucket write pressure (the P4 split signal lives here).

## Functional requirements

1. Bucket contract: key `rt:{profile_id}:{yyyyMMddHHmm}` (UTC minute), value `{views, clicks, links:{blockId:count} (top-N truncated at write), geo:{country:city:count} (top-N), salesTick?}` — all counters, all coarse-grained. TTL ≈ 1h (covers the 30-min window plus clock skew plus one missed consumer tick). No IPs, no visitor keys, no cross-profile keys — a bucket answers one profile's last minutes and nothing else.
2. Write path: the beacon consumer path increments the current minute bucket per accepted (post-dedupe, post-bot-filter) hit — same process that archives raw logs, so realtime costs one cache increment per already-accepted hit, never a new ingestion. Bot-filtered and throttled hits never increment (live numbers inherit the bot decision automatically).
3. Read path: short-poll merges the last 30 buckets in-request (`O(30)` cache reads, bounded, no PG touched on read or write). Missing buckets (quiet minutes, evictions, cold start) count as zero — never backfilled from aggregates (different grain, different truth; mixing them would corrupt both).
4. Eviction/outage semantics: expired buckets vanish (quiet profile reads zero — Acceptance drill asserts this, the most common realtime bug class); cache-layer outage or consumer lag returns `status:"degraded"` + `lastGoodAt` instead of erroring the dashboard or touching PG as fallback (PG fallback under cache outage is how outages cascade — forbidden explicitly).
5. Sales ticks: conditional strip reading order rollups owned by shop/monetize (referenced, never redefined, never copied into buckets). No order data → no `sales` key (absent, never zero-filled in a way that implies measured zero sales).
6. Split signal (recorded, not executed): sustained minute-bucket write pressure or consumer backlog firing ≥2 framework triggers from `docs/platform/architecture/architecture-decision.md` makes beacon ingest the P4 first-split candidate. The metric to watch is defined here: `rt.write_pressure` (increments/sec + error rate) alongside `consumer_lag_seconds`.

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for realtime state anywhere under `docs/link-to-bio/analytics/` (review greps it). Ephemeral keys: `rt:{profile_id}:{minute}` (~1h TTL); dimensions views/clicks/top-links/top-geo/(conditional sales refs). All state evaporates by design.
- Cache sizing: buckets per active profile per minute ≈ small JSON; even at 100k live profiles the working set fits the cache layer with headroom — the bound is active-profiles × 60, never total traffic (dedupe + minute-grain collapse hits before they reach buckets).

## Acceptance criteria

- Expiry drill: scripted visits then silence → feed decays to zero within the window + TTL (no stale highs).
- Outage drill: cache blackout → `degraded` + `lastGoodAt`, dashboard intact, PG write count unchanged.
- Isolation drill: bucket for profile A unreadable from profile B's poll (key includes profile id; owner check precedes merge).
- Pressure drill: `rt.write_pressure` dashboarded; P4 split proposal cites these numbers or does not happen.
- Violation drill: any PG table or per-hit PG write for realtime fails review.

## Out of scope

- Bucket writer and poll endpoint (backend plan); now-line panel (frontend plan); historical aggregates (profile/audience database plans); order rollup mechanics (shop/monetize majors); push/alerts (v2).

## Open questions

- Q1: 30-minute window fixed, or creator-selectable 10/30/60 on pro?
- Q2: Bucket TTL 1h — hold, or shorter for memory at 1M-concurrent scale?
