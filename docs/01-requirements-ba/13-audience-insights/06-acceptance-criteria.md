# Audience Insights - Acceptance Criteria (feature 13)

Phase 1 BA · BDD. Drills run against `docs/02-architecture-techlead/13-audience-insights/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Parity drill | Seeded traffic with hand-computed figures | Aggregates are read | New/returning, device, geo, referrer, UTM, and CTR match exactly within dedupe tolerance |
| AC-02 | Gating drill | Free account requesting 365d or a bypass deep-link | Read endpoint evaluates the range | `422 range_clamped` with `allowed` stated; direct API call clamps identically; no bypass exists |
| AC-03 | No-regression drill | Sustained load on the base pipeline | Beacon payload, queue throughput, PG writes measured | Payload and throughput unchanged vs feature 12; zero per-hit PG writes still holds |
| AC-04 | Privacy drill | Any PG row; a cross-profile stitching query | Inspected | No IP/visitor-id column exists; stitching query returns nothing by construction |
| AC-05 | Dedupe tolerance | Refreshes and double-taps within the 10-minute window | Figures compared to raw fixture | Segments inherit dedupe + bot decisions; no double counting, no second filter drift |
| AC-06 | Retention drill | Rows aged past free/pro windows | Nightly job runs; downgrade then re-upgrade a profile | Aged rows pruned on schedule; downgrade clamps immediately; re-upgrade never resurrects |
| AC-07 | Compare drill | Fixture with known weekday spikes | Previous-period overlay rendered | Weekday-aligned compare matches fixture; `aligned` badge shows; deltas render absolute + percentage |
| AC-08 | Single-formula drill | Same link + window on audience and earnings surfaces | CTR rendered | Both derive from `ctr_x1000` (windowed clicks ÷ windowed views); no second formula exists in code (`grep` clean) |
| AC-09 | Cardinality drill | 5k-campaign fixture | UTM tab renders | Paginates at 25 with server-ordered `Other (n campaigns)` row; DOM node count bounded; no client sorting |
| AC-10 | Honesty drill | Untagged profile; link with <30 views | Tabs render | Unlock CTA (not empty chart); `not enough data` (not a noisy 100% CTR) |
| AC-11 | Returning drill | Same visitor returns within 30d; different profile visited | Splits computed | Counted `returning` on the same profile; the other profile sees a stranger (`new`); no cross-profile stitching |
| AC-12 | Mobile supply drill | Feature 11's mobile tab requests the device mix | `dimension=device` read | UA-mix rows served from the same aggregates; no separate read path exists |
| AC-13 | Same-numbers drill | Audience tab and overview tab, one `generatedAt` | Both fetched | Identical base figures and clamps |
