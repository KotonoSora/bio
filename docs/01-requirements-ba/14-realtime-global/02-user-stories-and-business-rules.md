# Realtime Global - User Stories and Business Rules (feature 14)

Phase 1 BA · Rules `BR-xx` are local to this feature. Base pipeline rules: `docs/01-requirements-ba/12-profile-analytics/02-user-stories-and-business-rules.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Pro creator | to watch launch traffic minute-by-minute (visitors, clicks/min, top links, countries) | I know whether a launch is working right now | Must |
| US-02 | Pro creator | an as-of timestamp on every number | I never mistake the feed for a live meter | Must |
| US-03 | Pro creator | a last-good banner during cache trouble | the dashboard survives incidents | Must |
| US-04 | Free creator | the realtime panel as a locked teaser | I keep full history and see the upgrade path | Must |
| US-05 | Pro creator | throttling with clear backoff copy | fast polling never breaks my session | Should |
| US-06 | Visitor | no extra visitor-side work | my visit stays as fast as before | Must |
| US-07 | Ops | write-pressure and poll-throttle metrics | I catch abuse and record the P4 split signal | Must |
| US-08 | Pro creator | conditional sales ticks when order data exists | I watch revenue live where the platform can support it | Could (v1 absent) |

## Business rules

- **BR-01 Pro-gated operational view.** `GET /api/v1/analytics/realtime` is pro-only: free plans get `403 realtime_forbidden` (stable code) and the locked teaser issues zero fetches. Realtime is never the billed truth.
- **BR-02 Window mechanics.** Rolling 1800 s window over UTC minute buckets: each accepted (post-dedupe, post-bot-filter) hit increments `rt:{profile_id}:{minute}`; reads merge the last 30 buckets in-request (bounded O(30) cache reads). PG is never read or written in this path - degraded mode exists precisely so no PG fallback is tempting.
- **BR-03 Bucket contract.** Hash keyed by profile+UTC minute, TTL 3600 s, counters only: views, clicks, top-8 links, top-5 countries; no IPs, no visitor keys, no cross-profile keys. Missing minutes are zero; buckets are never backfilled from aggregates (different grain, different truth).
- **BR-04 Degraded honesty.** `status:"degraded"` + `lastGoodAt` appears when the merge fails (cache layer) - scoped to cache health, not profile quietness: a profile with no visits legitimately reads zero and stays `ok`. Rollup-consumer lag does not apply here (bucket writes ride intake); it degrades hourly aggregates only (feature 12). Recovery is automatic on the next successful merge.
- **BR-05 Authority rule.** Any discrepancy between the live feed and hourly aggregates resolves in favor of aggregates; the panel footnote and support runbook both state this once, so now-line screenshots never become billing disputes.
- **BR-06 Throttle and fair use.** Per-profile-per-user token bucket: 1 poll per 10 s; excess -> `429 rate_limited` + `Retry-After`. 10x poll bursts throttle without cache error-rate movement. Clients do not coordinate across tabs - the server throttle is the backstop (stated so nobody "fixes" it client-side). Global poll-rate alert fires on throttle spikes.
- **BR-07 Privacy ceiling.** Country-grain geo only (DEVIATION CARRIED from feature 12 Q-04); buckets hold counters, never identifiers; bucket isolation is structural (key includes profile id) plus the owner check before merge.
- **BR-08 Sales conditional.** The `sales` block (revenue series + units-per-link + pipeline-lag label) is present only when the platform exposes an order rollup for the profile (owned by shop 22-26 / monetize 32-40; forward contract, never redefined here). Absent means absent: no strip, no placeholder, no zero-filled implication of zero sales.
- **BR-09 Poll UX discipline.** Pro panel: immediate fetch on mount, then 15-30 s cadence with +/-5 s jitter; `document.hidden` pauses (zero requests from background tabs); unmount clears timers. `429` -> exponential backoff 15 -> 30 -> 60 s ceiling with throttled copy; consecutive degraded -> stretch to 60 s + last-good banner; 3 consecutive errors -> stop polling with a manual `Reconnect` button (no infinite retry).
- **BR-10 Card semantics.** `visitors` = sum of deduped views in the window; `cpmSeries` = 30 per-minute click counts, oldest to newest; every card carries an `as of HH:MM:SS` stamp derived from `generatedAt`; small-screen sparkline downsampling keeps exact totals.
- **BR-11 Same numbers, PWA included.** Installed PWA renders identical figures and lifecycle rules to the browser (feature 11 guarantee); reads are `private, max-age=10`.
- **BR-12 Global rollups are ops-only.** Platform-wide live totals are exposed on the ops metrics surface only; no creator-facing global endpoint exists, and creator credentials cannot reach ops views. Global leaderboards remain 45+ scope.
