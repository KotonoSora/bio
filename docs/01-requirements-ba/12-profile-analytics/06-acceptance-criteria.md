# Profile Analytics - Acceptance Criteria (feature 12)

Phase 1 BA · BDD. Drills run against `docs/02-architecture-techlead/12-profile-analytics/06-step-by-step-implementation-plan.md`; beacon/rollup drills use seeded traffic fixtures.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Zero-write soak | Sustained beacon load (10x baseline rate) | Query logs, lag, and aggregates inspected | Zero per-hit PG writes; lag < 1 h; aggregates match archived raw counts within dedupe tolerance |
| AC-02 | Idempotent replay | Same hourly batch delivered twice | Consumer upserts run | Counters increment exactly once (natural-key `ON CONFLICT` semantics, not app dedupe) |
| AC-03 | Lag honesty | Consumer lag forced beyond 90 min | Dashboard opened | Stale banner + chip shown; numbers visible and labelled; nothing fabricated fresh |
| AC-04 | Dashboard parity | Seeded traffic across days/sources/countries/blocks | Overview + links compared to fixtures | Views, clicks, engagement, deltas, series, per-link share, sources, countries all reproduced |
| AC-05 | QR scan | Venue QR encoding `/:username` | Scan processed | Exactly one deduped view recorded like any direct visit; no QR-specific path exists |
| AC-06 | Abuse and bots | Burst probes, tampered ids, crawler UAs | Intake evaluates | Bursts `429` + `Retry-After`; tampered targets dropped post-`202` without writes; crawlers counted, never stored |
| AC-07 | IDOR + RLS | User A requests user B profile analytics | Read evaluated | `403 foreign_profile`; direct DB probe with foreign `app.user_id` reads zero rows |
| AC-08 | Plan clamp | Free account selects 365d; realtime card viewed | Responses render | `422 range_clamped` -> picker snaps back with plan text; zero realtime fetches while locked |
| AC-09 | Deleted-block history | Block deleted after clicks were recorded | Links table + DB inspected | Rows survive; UI shows `Deleted link` with historical counts intact |
| AC-10 | Load gate | k6 beacon-soak: 10x beacon rate, banner payload rows included | Soak runs | Beacon p95 <= 200 ms; PG write rate flat; zero per-hit writes; rollback drill unaffected |
| AC-11 | Same numbers | Desktop + mobile browser + installed PWA, one `generatedAt` | All three read | Identical figures and clamps (feature 11 guarantee) |
| AC-12 | Rename attribution | Beacons arrive for an old username after a rename | Resolver follows `username_redirects` | Counts attribute to the current profile; unknown targets dropped silently and counted |
