# Sales (Ledger, Analytics, Order Console) - Architecture and Tech Stack (feature 51) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/51-sales/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. No new service: the projection must happen in the same transaction as the grant (34) and decomposition-exactness demands one database — 51 extends `billing-service` (`V9__sales_ledger.sql`, `V10__sales_analytics.sql`).

## Boundary (at amendment): `billing-service` extensions

| Component | Change | Notes |
|---|---|---|
| `SalesProjection` (V9) | Each grant handler writes exactly one `sales_lines` row in the same transaction | UNIQUE on `order_code`; duplicate grants emit nothing |
| `SalesNetting` (V9) | 38's refund execution + 40's dispute losses append `sales_adjustments` in their own transactions | Append-only; originals byte-identical |
| `OrderConsole` | Universal lookup (five identifier kinds) + assembled trail | p95 ≤ 500 ms; reads owning features; never reimplements lifecycle |
| `ActionGuard` | Role gates, precondition checks, stale-view re-validation, dry-run previews | Delegates each action to the owning feature's endpoint |
| `DualControl` | `approval_requests` maker-checker with 24 h expiry | Proposer ≠ approver; expired cannot execute |
| `CaseService` | `support_cases` lifecycle | Buyer mirror shows "under review" only |
| `SalesAnalytics` (V10) | Funnel + revenue aggregates (hourly), close snapshots | Rebuilt idempotently from facts + events; close re-runs byte-identical |
| `Scorecards` | Provider health aggregates + alerting | Config thresholds; rules versioned |
| Scoped payout reads | Internal reads into 49/50 payout ledgers (reserved rows there) | Business cuts never include cross-earner detail |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Extend `billing-service`; no new service; V9 + V10 | Same-transaction projection + exact decomposition require one database |
| D-2 | Facts append-only (INSERT + SELECT grants); corrections append reasoned adjustments | BR-05; grep + migration-review gate enforces |
| D-3 | Console orchestrates only: every action delegates to 33/34/36/38/40 endpoints | No second lifecycle; statuses owned by 34 |
| D-4 | Dual control as first-class records with expiry | BR-11; auditable maker-checker |
| D-5 | Aggregates derived from facts + events, rebuilt idempotently; close = versioned frozen snapshot | BR-15; no KPI without decomposition |
| D-6 | Scorecard thresholds config-owned (`sales.scorecards`); alert rules versioned | Q-03; feeds kill-switch + provider ordering |
| D-7 | Payout visibility via scoped internal reads (49/50 own the ledgers) | BR-18; no cross-earner leakage |
| D-8 | Currency posture: integer VND as charged; additive `charged_currency` if ever needed | BR-07; no presentment rework |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | billing-service row note: sales ledger + analytics extensions (V9/V10) |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | registry rows for console/analytics endpoints; V9/V10 in the chain |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/` | grant handler seam: same-transaction projection |
| `docs/02-architecture-techlead/38-refunds/` | refund execution seam: adjustment append |
| `docs/02-architecture-techlead/49-affiliate/` and `docs/02-architecture-techlead/50-advertising/` | scoped internal reads for payout rows |
| `MIGRATION.md` | feature 51 row (doc migration landed with this batch; build at amendment) |
