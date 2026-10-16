# Realtime Global - Exception Handling and Errors (feature 14)

Phase 1 BA · This slice adds no public intake surface, so visitor-facing errors do not exist; dashboard messages are exact user-visible text. `(inherited)` marks feature 12 handling.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Free plan polls realtime | Reads | `403 realtime_forbidden`; teaser issues zero fetches; stable code | (teaser) "Realtime is a Pro feature" |
| Foreign profile param | Reads | `403 foreign_profile` (inherited); owner check precedes merge | "You do not have access to this profile" |
| Cache merge failure | Reads | `status:"degraded"` + `lastGoodAt`; no PG fallback; poll stretches to 60 s | "Live data delayed - last good {time}" |
| Bucket write failure at intake | Intake | Hit already answered 202; `rt.write_error` increments; window may undercount; alert fires; never blocks intake | (visitor invisible) |
| Quiet profile (no visits) | Reads | `ok` + zeros; expiry guarantees zero, never stale highs; not degraded | "No visits in the last 30 minutes" |
| Poll throttle exceeded | Reads | `429 rate_limited` + `Retry-After`; client backs off 15 -> 30 -> 60 s | "Too many requests - retry in {n}s" |
| 3 consecutive poll errors | Reads | Polling stops; manual `Reconnect` only; no infinite retry | "Reconnect" |
| Plan demotion mid-session | Reads | Next poll `403 realtime_forbidden`; panel swaps to teaser without error copy | (teaser) "Realtime is a Pro feature" |
| Sales data absent | Reads | `sales` omitted; no strip, no zero-fill | (nothing) |
| Expired bucket read (window edge) | Reads | Treated as zero; TTL 1 h covers window + skew; no backfill from aggregates | (nothing) |
| Sustained write pressure | Ops | `rt.write_pressure` alert; P4 split signal recorded for review | (ops alert) |
| Creator requests platform-wide totals | Ops/reads | No creator-facing global endpoint exists; ops surface refuses creator credentials | "You do not have access to this view" |
| Session expired | Reads | Redirect to SSO; return to the same panel position | (auth-owned) |
