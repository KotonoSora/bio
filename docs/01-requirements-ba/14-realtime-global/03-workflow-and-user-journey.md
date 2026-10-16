# Realtime Global - Workflow and User Journey (feature 14)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Pro now-line panel lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/analytics/overview` (pro) | Realtime panel renders above historical cards; one immediate fetch | BR-01, BR-09 |
| 2 | System | Fetch returns | Visitors stat, 30-point click sparkline, top links, countries, as-of stamp | BR-10 |
| 3 | Client | Timer ticks 15-30 s with +/-5 s jitter | Next poll; in-flight request never overlaps | BR-09 |
| 4 | Creator | Hides the tab | Zero requests while hidden; polling resumes on visible | BR-09 |
| 5 | Creator | Navigates away | Interval and in-flight request cleared; no background polls | BR-09 |
| 6 | Creator | Reads the footnote | Aggregates-are-truth copy shown once per panel | BR-05 |

## Journey 2 - Free creator teaser

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free creator | Overview loads | Locked teaser card occupies the panel position; zero realtime fetches | BR-01 |
| 2 | Free creator | Clicks upgrade | Pricing path; historical analytics untouched | BR-01 |
| 3 | Free creator | Deep-links the endpoint directly | `403 realtime_forbidden`; teaser remains | BR-01 |

## Journey 3 - Degraded mode

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Merge fails (cache layer) | `status:"degraded"` + `lastGoodAt`; last-good banner; poll stretches to 60 s | BR-04, BR-09 |
| 2 | System | Next merge succeeds | `ok` restores silently; banner clears | BR-04 |
| 3 | Creator | Quiet profile (no visits) | `ok` with zeros; no degraded banner - quiet is not degraded | BR-04 |

## Journey 4 - Throttle and backoff

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Client | Polls again within 10 s | `429 rate_limited` + `Retry-After`; backoff 15 -> 30 -> 60 s; throttled copy | BR-06, BR-09 |
| 2 | Client | 3 consecutive errors | Polling stops; manual `Reconnect` button renders | BR-09 |
| 3 | Ops | Throttle spike | `realtime.poll_throttled` alert fires | BR-06 |

## Journey 5 - Plan demotion mid-session

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Pro creator | Downgrades while panel is open | Next poll gets `403 realtime_forbidden`; panel swaps to teaser without error copy | BR-01 |
| 2 | Free creator | Re-upgrades | Next visit renders the live panel; nothing historical is lost | BR-01 |

## Journey 6 - Bucket writes (system path)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Hits a profile | Accepted (post-dedupe, post-bot) hit increments the current UTC minute bucket via the intake path | BR-02, BR-03 |
| 2 | System | Bucket write fails | Intake unaffected (202 already sent); `rt.write_error` increments; degradation stays honest (no fabrication) | BR-04 |
| 3 | System | 1 h elapses | Expired buckets read as zero; quiet profiles read zero, never stale highs | BR-03 |

## Journey 7 - Sales strip (conditional, future supply)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Platform | Order rollup for the profile exists | `sales` block present with revenue series + pipeline-lag label | BR-08 |
| 2 | Platform | No order data | `sales` absent; no strip, no placeholder, no zero-fill | BR-08 |

## Journey 8 - Ops global view

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | Opens the ops metrics surface | Platform-wide live totals visible with ops role only | BR-12 |
| 2 | Creator | Looks for platform-wide totals | No creator-facing endpoint exists; nothing renders | BR-12 |
