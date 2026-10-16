# Advertising (Inventory, Serving, Creator Share, Retargeting) - Acceptance Criteria (feature 50) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-19 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/50-advertising/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero ad surface found (`ad_*` tables, `/api/a/` route, decisioning, segment builder, earnings UI); a hit blocks promotion by tooling |
| AC-02 | Catalog drill | Each placement rendered desktop + mobile | Formats, positions, labeling inspected | Exact format/position/labeling; checkout/payment surfaces provably ad-free (grep + review) |
| AC-03 | Safety drill | Prohibited creative; creator category-block; live takedown | Each path runs | Rejected at approval; block respected in serving; takedown pauses everywhere ≤ minutes |
| AC-04 | Opt-in drill | New profile; mid-flight opt-out; pro page probe | States evaluated | Default OFF; opt-out stops serving immediately with no penalty; pro never forced |
| AC-05 | Separation drill (under load) | Ad traffic at volume | Both tables inspected | Zero ad rows in creator analytics; zero creator rows in ad aggregates |
| AC-06 | Auth drill | Advertiser A reads advertiser B flights/reports; creator edits flights | Attempts run | 403/404 with zero rows; creator has no flight-edit route |
| AC-07 | Decision drill | Eligibility matrix fixtures (window/placement/opt-in/category/caps) | Decisioning runs | Per-combination correctness; empty inventory collapses with zero layout shift |
| AC-08 | Counting drill | Refresh/double-click storms; bots; below-viewport renders; click floods | Pipeline runs | Dedupe to one; bots excluded; unverified views uncounted; floods quarantined with billing aggregates clean |
| AC-09 | Dispute drill | Any billed impression/click | Evidence bundle replayed | Full inputs recovered; filter-exclusion report matches raw-minus-aggregate math |
| AC-10 | Outage drill | Measurement downtime | Queue + sweep recover | No loss, no doubles (dedupe keys) |
| AC-11 | Earnings drill | Counted events across a month | Estimates → monthly close → payout | Numbers reconcile event → aggregate → ledger → statement end to end; statements match emails |
| AC-12 | Clawback drill | Post-close invalid-traffic finding | Ledger reacts | Only the tainted share → `clawed_back` with notice + evidence; legitimate earnings payable |
| AC-13 | Self-farming drill | Creator inflating own slots | Detection runs | Tainted earnings voided; warn/restrict ladder; legitimate history preserved |
| AC-14 | Shared-runner drill | Affiliate + creator lines in one cycle | Batch runs | One audited run, earner-type partitioned; per-earner statements correct; zero cross-visibility |
| AC-15 | Segment drill | Fixture event streams | Evaluator runs | Exact membership (entry on qualifying event, expiry at lookback end); exclusion logic verified; per-creator only |
| AC-16 | Consent drill | Marketing-opted-out abandoner; transactional-only recipient | Reach evaluates | Lifecycle reminder only; promo variant refused by eligibility with reason logged |
| AC-17 | Export-refusal drill | Custom-audience upload attempted | Attempt runs | 404/422 — surface absent, never silently stubbed |
| AC-18 | Fatigue drill | Over-cap recipients; past buyers | Sends/serves evaluate | Suppressed across surfaces; exclusions hold; cause + one-click stop on every message |
| AC-19 | Lift drill | A flight with the 10% holdout | Report generated | Conversion vs control reported; reconciles to underlying events within dedupe tolerance; no claim without control |
