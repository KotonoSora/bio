# Data Analysis (Pipeline Standards, Experiments, Metrics) - Acceptance Criteria (feature 52) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-17 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/52-data-analysis/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero data-analysis surface found (registry tables, experiment UI, KPI library, quality dashboards); a hit blocks promotion by tooling |
| AC-02 | Taxonomy drill | An unregistered event; a registered-but-malformed payload | Edge validation runs | Unregistered rejected (TEST loud, production sampled + alerted); malformed quarantined with owner notification |
| AC-03 | Exactly-once drill | Redelivered event batches under a replay storm | Consumer processes | Zero aggregate drift — idempotency keys hold end to end |
| AC-04 | Retention drill | Expired raw classes; a buyer deletion request | Sweeps + deletion run | Deletions on schedule (storage audit proves it); raw buyer data cleared within window; aggregates stand with provenance |
| AC-05 | Rebuild drill | A destroyed aggregate | Rebuild runs from raw + ledger | Byte-identical figures; dual-report check passes during definition migrations |
| AC-06 | Adoption drill | One existing pipeline gets a conformance note + taxonomy alias | Change lands | No behavior change; a new pipeline ships conformant from its first review |
| AC-07 | Registration drill | An unregistered pricing change; a registered test | Review gate + registry evaluated | Unregistered blocked; registered carries hypothesis, MDE, guardrails, ship rule before launch |
| AC-08 | Stability drill | Same user across sessions, devices, pending windows; load spike | Assignment evaluated | Same variant throughout; no mid-flight re-bucketing; variant pinned to the order at creation |
| AC-09 | Guardrail drill | Seeded conversion dip; dispute-rate spike | Guardrails evaluate | Auto-pause + paging with the offending slice; finance figures and grandfathered terms provably untouched |
| AC-10 | Holdout drill | A promo campaign with holdout | Results compared | True lift vs raw redemptions; holdout buyers experienced normal UX (no degraded treatment found) |
| AC-11 | Decision drill | A fixed window closes | Readout generated | Framework KPIs only; decision + evidence logged; shipped variant rolls through catalog versioning with renewals consistent |
| AC-12 | Fixture drill | Seeded ledger with trials, coupons, partials, disputes, grace | Library computes MRR/NRR/ARPU/conversion/refund-rate | Output matches hand computation exactly under the current version |
| AC-13 | Dual-report drill | A redefined metric | Overlap period runs | Old vs new side by side; finance close names its version; history unrewritten |
| AC-14 | Single-library drill | All dashboards + a definition change | Grep + propagation run | No KPI SQL outside the library; the change reaches all consumers without per-dashboard edits |
| AC-15 | Closed-period drill | Post-close adjustments | Figures read | Adjustments appear in the posting period with restatement notes; closed snapshots byte-identical |
| AC-16 | Auth drill | Non-ops roles on KPI endpoints; earner-scoped callers probing business KPIs | Attempts run | 403 both ways; earner callers see only their ledgers |
| AC-17 | Fairness drill | A differing-price treatment without disclosure; an underpowered test | Review gate + registry run | Treatment blocked until legal sign-off + disclosure copy; underpowered test declared, not run |
