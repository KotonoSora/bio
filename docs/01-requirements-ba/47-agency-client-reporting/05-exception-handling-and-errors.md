# Agency Client Reporting - Exception Handling and Errors (feature 47)

Phase 1 BA · Problems follow RFC 9457 (`code` field). No visitor-level data, tokens, or cross-org refs ever appear in payloads.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Member reads an unassigned client | API | 403 `report_scope_denied` (access read re-checked per request) | "This client isn't assigned to you." |
| Cross-org report ref | API | 404 by construction (org-shaped reads) | "Not found." |
| Client reads another client | API | 403/404; shares are per-profile | "Not found." |
| Unlinked profile in a saved view | System | Profile drops out on the next read (purge ≤ 60 s); rollup recomputes without it | View note: "{profile} is no longer linked." |
| Metric source down | System | Section shows reason + last-good timestamp (≤ 15 min cache); never stale-as-fresh | "{Source} is slow — showing {time} data." |
| Dashboard parity mismatch | System | Metric marked, never restated; ops alert | "This number is under review." |
| Revenue data absent | System | Section hides with reason; sales tab hides | "No sales yet." |
| CTR with zero views | System | Hidden with reason (no divide-by-zero) | "No views yet." |
| Period beyond 366 days | API | 422 `validation_failed` (range) | "Pick a range up to 366 days." |
| Share expired | Share page | Neutral expired page; no enumeration | "This link expired." |
| Share revoked (manual or unlink/membership event) | Share page | Dead immediately | "This link is no longer active." |
| Full-detail share attempted by a member | API | 403 (owner/manager action only) | "Only owners and managers can share full reports." |
| Benchmarking request (cross-org) | System | No surface exists; isolation explained | "Agencies never see each other's data." |
| Ops read without scope | API | 403; scoped reads audited | "Not found." |
| Leaderboard metric without data | UI | Tab hides honestly (reason) | "No sales data for this period." |
| Snapshot job misses a day | System | Leaderboard shows latest snapshot date honestly; alert after 2 misses | "Standings from {date}." |
| Access read (45) down | System | Reports fail closed for scoped reads (no cached cross-role leak beyond the ≤ 60 s cache) | "Can't verify access right now — try again." |
