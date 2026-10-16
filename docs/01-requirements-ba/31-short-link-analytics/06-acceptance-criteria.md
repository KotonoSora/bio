# Short-Link Analytics - Acceptance Criteria (feature 31)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Counting drill | scripted genuine opens (mixed devices/referrers) plus prefetch/bot traffic | aggregates build | genuine opens reconcile hit-for-hit; prefetch/bot traffic is excluded per the decided posture; exclusion counters increment per reason |
| AC-02 | Hot-path drill | counting enabled vs disabled; then a queue outage | resolve runs and recovery runs | resolve latency delta ≤ 5 ms p95 (benchmarked as a gate); the outage degrades to sweep-repair with no loss and no doubles |
| AC-03 | Flood drill | a single-source flood (≥ 500/min) with concurrent legitimate traffic | filtering runs | the flood is quarantined out of aggregates with an ops case; legitimate same-window traffic is unaffected; release adds once; drop discards with evidence intact |
| AC-04 | Separation drill (under load) | short-link hits and profile analytics with a bio-embedded short URL | hits are recorded | zero short-link rows in `profile_views`/`link_clicks` and vice versa; the dual-count case is documented and verified (two true events) |
| AC-05 | Replay drill | any reported figure within the 90-day window | it is replayed from retained evidence | it replays exactly; `raw − excluded = reported` holds exactly; the lookup is audited |
| AC-06 | Repair drill | a simulated queue loss for a day | repair runs; repair re-runs | totals match hit-for-hit from raw; the second run is byte-identical (convergent rebuild, no doubles); completeness markers gate the rebuild |
| AC-07 | Estimation drill | organic scan traffic and event-pack codes | readouts render | heuristic scan shares carry `estimated: true`; location-code splits are exact; the styles are visually distinct |
| AC-08 | Decide drill | a pending quarantine row | release is attempted without a reason; then a valid drop | 422 without a reason; valid decisions are audited with operator + reason; the exclusion panel updates on next fetch |
| AC-09 | CSV drill | a 90-day history for one code | export runs | the streamed CSV reconciles with on-screen figures including exclusion columns and `generated_at` |
| AC-10 | RLS/IDOR drill | creator A probing creator B's code stats and health both | requests run | 404 with zero rows; no existence oracle; ops routes 403 for creator tokens with audit |
