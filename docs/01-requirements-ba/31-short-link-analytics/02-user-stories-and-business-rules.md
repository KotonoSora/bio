# Short-Link Analytics - User Stories and Business Rules (feature 31)

Phase 1 BA · Realized by `docs/02-architecture-techlead/31-short-link-analytics/` (BR-01..BR-16). Resolve/hot-path contract is feature 29's; separation counterpart is feature 12's.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Link owner | see opens, uniques, devices, referrers, and scan-vs-click splits per link | spend decisions use trustworthy counts | Must |
| US-02 | Dispute reviewer | replay any billed/reported count from stored evidence | figures stay disputable | Must |
| US-03 | Visitor | be counted once per genuine open | I am never tracked across links into a behavioral profile | Must |
| US-04 | Campaign owner | see what was excluded and why | missing numbers never read as data loss | Should |
| US-05 | Affiliate/ad consumer (forward) | read the same aggregates through scoped endpoints | measurement stays consistent | Should |

## Business rules

- BR-01 Async pipeline: the resolve path validates → redirects → enqueues after the response (feature 29); the consumer builds all counts; zero PostgreSQL writes occur on the hot path; no synchronous counting path exists.
- BR-02 Single intake: the pipeline consumes `link.hit.v1` (code, ts, ipHash, ipHashSaltId, refererHost, userAgentClass, country, purposeHint?) — no other write path into aggregates exists.
- BR-03 Dedupe: the same visitor/link pair within a 10-minute window collapses to one count; the visitor key is a salted IP hash (24 h salt rotation); no raw IP or visitor id is stored anywhere.
- BR-04 Filters run in fixed order before aggregates: bot/crawler exclusion → prefetch/prerender suppression (`purposeHint` where present) → double-open collapse → velocity quarantine; each exclusion increments its reason counter (first-class readout data).
- BR-05 Aggregates: one row per code per day (`opens`, `uniques`, `scans`) + segment rows (`device|referrer|scan_class|geo|excluded`); natural-key upserts for incremental processing; a day rebuild deletes and recomputes from raw (convergent, no doubles).
- BR-06 Scan-vs-click: scans are stored separately (counter + `scan_class` segment) and labeled `estimated` wherever heuristic-derived (absent referrer + mobile + camera-class signals); event-pack per-location codes give exact splits — exact and estimated never share a rendering style.
- BR-07 Readouts (owner-scoped): opens/uniques curve, top referrers, device split, scan share, geo-country, exclusion counts per reason, and the reconciling equation `raw − excluded = reported` — shown, not footnoted; CSV export carries the same columns plus `generated_at`.
- BR-08 Evidence: any reported figure replays from retained raw within the 90-day window; every metric decomposes to underlying hits; the replay lookup is ops-only and audited.
- BR-09 Quarantine: a single-source flood (≥ 500 hits/minute from one source) is held OUT of aggregates with a quarantine row + ops alert (case reference); legitimate traffic in the same window is unaffected; release replays held hits through the pipeline (dedupe applies), drop discards; both are audited with the deciding operator.
- BR-10 Separation: short-link hits never enter `profile_views`/`link_clicks` (different service and database — no cross-write path exists); the bio-block dual-count case (short-link open + block click = two true events) is documented in both dashboards with matched windows and never suppressed.
- BR-11 Money-tied posture: billed/affiliate/ad-attributed counting uses the strictest bot/prefetch posture; the matrix is recorded once; any divergence from creator-analytics defaults is labeled in the readout footnote.
- BR-12 Privacy: first-party counting only; no visitor-id columns exist (cross-link identity resolution is unqueryable by construction); declined-consent traffic still contributes aggregate-safe counts (no identity is involved).
- BR-13 Ops access: dispute replay and quarantine decisions run under the audited ops role; creators have no raw-hit path (route absence, not hiding).
- BR-14 Retention: aggregates follow the account's plan window via feature 13's prune job (extended scope); raw NDJSON 90 days; quarantine rows until decided; audit 24 months.
- BR-15 Repair: a queue outage degrades to sweep-repair from raw (no loss, no doubles) within the raw window; completeness markers gate rebuilds; a raw-gap metric alerts when evidence is missing.
- BR-16 Purge: account deletion removes aggregate, segment, and quarantine rows by `user_id` with counts in the certificate; raw evidence carries no identity to purge (hashed only).
