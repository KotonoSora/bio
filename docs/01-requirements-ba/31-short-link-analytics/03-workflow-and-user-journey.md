# Short-Link Analytics - Workflow and User Journey (feature 31)

Phase 1 BA · System steps cite owning features; ops steps are audited.

## Open → count → readout

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | opens a short link (click, scan, or old print) | feature 29 resolves and redirects; the hit is enqueued after the response | BR-01 |
| 2 | System | consumer drains the queue | dedupe → filters (fixed order) → aggregates upsert; exclusions increment per reason | BR-03/04/05 |
| 3 | System | scan-leaning signals present | counted in `scans` (separate), labeled `estimated` at readout | BR-06 |
| 4 | Owner | opens `/app/links/{code}/stats` | curve, breakdowns, scan share (estimated label), exclusion panel with the equation, `generated_at` footnote | BR-07 |
| 5 | Owner | exports CSV | streamed file with exclusion columns + `generated_at` | BR-07 |
| 6 | Owner | has a bio-embedded short link | dual-count explainer line with matched-window links to both dashboards | BR-10 |

## Flood → quarantine → decision

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Attacker | single-source flood (≥ 500/min) | hits branch to quarantine; aggregates stay clean; ops alert with case reference | BR-09 |
| 2 | Ops | reviews the case | sees held counts and evidence sample; decides release or drop with a reason | BR-09/13 |
| 3 | System | release | held hits replay through the pipeline (dedupe applies); aggregates update; decision audited | BR-09 |
| 4 | System | drop | discard; evidence ages out with the raw TTL; decision audited | BR-09/14 |

## Dispute → replay

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Dispute reviewer | decomposes a reported figure | evidence view: raw-hit sample within TTL + filter decisions per class + quarantine rows | BR-08 |
| 2 | System | replay runs | filter-exclusion math holds exactly (`raw − excluded = reported`); lookup audited | BR-08/13 |

## Outage → repair → separation checks

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | queue outage ends | sweep-repair rebuilds affected days from raw (delete + recompute); no doubles | BR-15 |
| 2 | System | raw gap detected | `rawgap` metric alerts; affected day flagged in readout footnotes | BR-15 |
| 3 | System | under load | zero cross-rows between short-link and profile analytics; dual case documented | BR-10 |
| 4 | Purge runner | account deletion | aggregates/segments/quarantine rows removed; counts in the certificate | BR-16 |
