# Metrics Framework (canonical monetization KPIs)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P3-ops (ships with first revenue); needs a plan update before build. Definitions here; dashboards in `sales/sales-analytics.md`; facts in `sales/sales-ledger.md`.

## Purpose

Fix each monetization metric's one true definition — formula, inputs, and edge-case handling — so every dashboard, report, and argument uses the same numbers. Without this, MRR gets computed three ways (by dashboard, by finance export, by ad-hoc query), renewals double-count trials, and refunds quietly stay in revenue. This doc is the dictionary every other analysis cites; new KPIs are added here first, never invented inside a dashboard.

## Users

- Operator/founder: quotes MRR, NRR, conversion, and churn knowing finance sees the identical figure.
- Finance: closes books and board packs off versioned definitions with drill-down to ledger lines.
- Analyst (future): builds new cuts without re-deciding what "active subscriber" means.

## Functional requirements

1. Metric registry (versioned, append-only history): each KPI carries `{name, formula, input tables, grain, edge-case rules, version, effective_from}`. v1 set — MRR/ARR (normalized monthly value of active + grace subscriptions from the ledger, not cash collected), NRR/GRR (expansion − contraction − churn over cohorts, trials excluded), ARPU (MRR ÷ billable base, base defined), conversion (checkout-started → paid by funnel stages), refund rate and dispute rate (adjustments ÷ gross, by provider/product), CAC payback (fully-loaded acquisition cost ÷ new-pro MRR — cost inputs owned by finance, see Q1), AI cost per generation (Workers AI metering where exposed, else modeled).
2. Edge-case rules (decided once, applied everywhere): trials never enter MRR until first paid grant; grace periods count as retained (not churned) until expiry; coupon-discounted terms enter at net collected (not list price); partial refunds net the period they post in (no restatement of closed periods — closed snapshots from the sales-analytics doc are immutable); disputed-then-won revenue re-enters in the win period with a visible restatement note.
3. Base and cohort discipline: billable base, acquisition cohorts (by month × channel: direct, affiliate, campaign, ad), and plan-version cohorts defined once; retention/churn read off the same cohort tables the lifecycle job maintains. No dashboard may redefine "churned" (ledger `expired` without renewal within the cohort window) or "active" (ledger `active|grace|trial` current row).
4. Change control: redefining a metric publishes a new version with dual reporting over one overlap period (old vs new side by side); finance close always names the definition version used. Breaking changes announced a cycle ahead; dashboards migrate, history is not rewritten.
5. One-definition enforcement: implementation holds a single metrics library (one function per KPI) consumed by dashboards, exports, and alerts — copy-pasted SQL per dashboard is a review failure. Unit tests assert each KPI against hand-computed fixtures including all edge cases in rule 2.
6. Review cadence: metric definitions reviewed quarterly with finance (or on any pricing/packaging change); unused metrics retired to an archive section, never deleted.

## Data / API touches

- Reads `sales_lines`/`sales_adjustments`, `subscriptions`, funnel aggregates, and finance cost inputs (CAC numerators live in finance tooling, referenced — not duplicated — here). Derived KPI snapshots (`kpi_daily{day, metric, version, dimensions..., value}`) rebuilt idempotently; finance-close figures frozen per the sales-analytics snapshot discipline. All PG via `worker/db/*`; RLS restricts to operator/finance roles.
- No buyer/affiliate/creator-facing surface: KPI aggregates never leak into scoped earner dashboards (those keep their own ledgers).
- Definition registry stored as versioned config (reviewed like code), with the metrics library tested in CI.

## Acceptance criteria

- Fixture drill: hand-computed MRR/NRR/ARPU/conversion/refund-rate on a seeded ledger (trials, coupons, partials, disputes, grace) match library output exactly under the current version.
- Dual-report drill: a redefined metric shows old-vs-new side by side for one overlap period; finance close names its version; history unrewritten.
- Single-library drill: no dashboard ships KPI SQL outside the shared library (grep + review gate); a definition change propagates to all consumers without per-dashboard edits.
- Closed-period drill: post-close adjustments appear in the posting period with restatement notes; closed snapshots byte-identical.
- Auth probe: non-ops roles on KPI endpoints → 403; earner-scoped callers see only their ledgers, never business KPIs.

## Out of scope

- Dashboard presentation and alerts (see `sales/sales-analytics.md`); pipeline and taxonomy standards (see `data-analysis/analytics-pipeline.md`); experiment design and readouts (see `data-analysis/experimentation.md`).
- Revenue facts (`sales/sales-ledger.md`); cost accounting itself (finance tooling); P4/P5.

## Open questions

- Q1: CAC inputs — which cost lines (ads, affiliate payouts, staffing?) enter fully-loaded CAC, and who certifies them monthly?
- Q2: AI cost attribution — metered per generation from the platform, or modeled allocation until metering exists?
