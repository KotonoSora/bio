# Realtime Global - Acceptance Criteria (feature 14)

Phase 1 BA · BDD. Drills run against `docs/02-architecture-techlead/14-realtime-global/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Liveness drill | Scripted launch traffic (clicks, several countries, 3 active links) | Pro panel polls | Within two poll cycles visitors, cpm series, top links, and countries match the fixture within dedupe tolerance |
| AC-02 | Teaser drill | Free account on desktop, mobile, and installed PWA | Any realtime surface renders | Zero realtime fetches fire; a direct API call returns `403 realtime_forbidden`; teaser shows upgrade path |
| AC-03 | No-DB drill | Sustained 10x poll burst with realtime on vs off | Polls served | PG write count identical (zero from this path); cache error rate flat; excess polls throttled |
| AC-04 | Expiry drill | Profile quiet for 2 h; a previously hot minute bucket | Panel polls; bucket keys inspected in Redis | Reads return zeros (never stale highs); buckets past TTL are gone; no backfill from aggregates |
| AC-05 | Authority drill | Divergence fixture: live feed vs hourly aggregates disagree | Support compares both | Resolution favors aggregates per footnote + runbook; no billing/reporting path reads realtime (grep clean) |
| AC-06 | Throttle drill | Second poll within 10 s | Endpoint called | `429 rate_limited` + `Retry-After`; client backs off 15 -> 30 -> 60 s; throttle spike fires ops alert |
| AC-07 | Hidden-tab drill | Launch sim while tab is backgrounded | Tab hidden 5 min then restored | Zero requests from hidden tab (access log asserted); polling resumes immediately on visible |
| AC-08 | Degraded drill | Cache fault injected during polling | Merge fails then recovers | `degraded` + `lastGoodAt` + banner; no PG fallback in traces; silent `ok` restore on next success; quiet profile never marks degraded |
| AC-09 | Isolation drill | Worker traffic on two profiles | Both panels poll | Bucket keys embed profile id; owner check precedes merge; foreign param -> `403`; no cross-profile figures appear |
| AC-10 | Reconnect drill | Three consecutive failures (endpoint unreachable) | Polls fail thrice | Polling stops; `Reconnect` renders; manual click resumes; request count proves no retry loop ran |
| AC-11 | Same-numbers drill | Browser + installed PWA, one `generatedAt` | Both fetched | Identical figures, lifecycle rules, and footnote (feature 11 guarantee) |
| AC-12 | Sales-conditional drill | Profile without order rollups; fixture with rollup payload | Panel renders | Absent rollup: no strip, no placeholder, no zero-fill. Present fixture: strip renders with lag label (contract-shape test only; no shop tables built) |
| AC-13 | Privacy drill | Bucket dump; payload capture | Inspected | Counters and country codes only; no IPs, no visitor keys, no identifiers in buckets or payloads; no cross-profile keys |
| AC-14 | Globals drill | Creator credentials against ops surface; route inventory | Access attempted; inventory grepped | Creator refused on ops surface; no creator-facing global endpoint exists in the route inventory |
