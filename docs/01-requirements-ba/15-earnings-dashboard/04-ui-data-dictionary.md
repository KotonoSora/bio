# Earnings Dashboard - UI Data Dictionary (feature 15)

Phase 1 BA · React 19.3; owner-only screen `/app/analytics/earnings`. Shared component library inherits 12's `FetchState`, lag footnotes, and clamp-back picker. Topology: `docs/02-architecture-techlead/15-earnings-dashboard/01-architecture-and-tech-stack.md`.

## Widgets and components

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| EarningsScreen | route | yes | Responsive-first order: funnel table -> daily revenue series -> per-product units -> audience embeds (same components as analytics tabs) | `/app/analytics/earnings`, range 30d |
| FunnelTable | table | yes | Columns: link, views, clicks, CTR, units, revenue, rpc; server order views desc; page-local sort only; 25 rows/page server-side | engagement columns always |
| MoneyCells | cells | conditional | Render only when the payload carries money keys; `not_yet_available` -> em-dash + caption; `unavailableCells` -> retry state | absent without flags |
| GrossNetLabel | inline label | conditional | Verbatim from source (`gross` \| `net`); gross-only sources render no net column | - |
| CellTooltip | tooltip | yes | Per cell group: `{source} - lag {n}s - {generated_at}`; tap opens on mobile | - |
| TraceLink | link | yes | One hop to origin (audience tab or order list) carrying the identical range param | - |
| RevenueSeries | chart | conditional | Daily series (hourly grain); `points` cap 120; totals stay exact | absent without flags |
| UnitsTable | table | conditional | Per-product units; 25/page; server order | absent without flags |
| TotalsRow | footer row | yes | Engagement sums always; money totals only when every visible row has complete money cells (`moneyComplete`); otherwise money totals absent | - |
| ExportCsvButton | button | yes | Streamed download with progress + cancel; cancel aborts fetch | - |
| RefreshControls | controls | yes | Focus refetch + manual refresh only; no interval polling | manual |
| LiveTicksEmbed | embed | conditional | Pro only; feature 14's component, 14's poll discipline; never merged into unified numbers | absent for free |
| RangePicker | picker | yes | Shared clamp-back behavior (`422 range_clamped`); downgrade re-clamps next fetch | 30d |

## Absence rules (enforced by review/grep)

| Rule | Assertion |
|---|---|
| No client money math | `rpc` and net arrive from the server; the client formats only - it lacks fee data by design (BR-06) |
| No strip registry client-side | Rendering follows payload keys literally (`if (row.revenue !== undefined)`); no client list of sources exists (BR-04) |
| No zero for missing | `not_yet_available` renders em-dash + caption; zeros render only for measured zeros (BR-05) |
| No interval polling | Unified data changes hourly; idle screens issue zero requests (BR-12) |
| No grain mixing | Live ticks render in 14's components; unified payload never carries live counters (BR-07) |

## State and copy rules

| Item | Rule |
|---|---|
| Not-yet copy | `coming when {source} ships` under the em-dash cell |
| Unavailable copy | `temporarily unavailable` + `Retry` (one view refetch, never a per-cell call) |
| Low-CTR copy | `Not enough data (needs 30+ views)` (inherited from 13) |
| Gross/net copy | Source's label verbatim; no client math wording exists |
| Lag copy | `Data from {source} - lag ~{n}s` on every widget group |
| Totals copy | Money totals hidden (not zeroed) when `moneyComplete:false` |
| Deleted link copy | `Deleted link` (inherited) |
| Upsell placement | Plan-page only; no upsell skeletons or greyed money panels inside the data grid (BO-3) |
| CSV columns | Per row: link, views, clicks, CTR, units, revenue, rpc, gross_or_net, source, lag_seconds, generated_at |
| Currency | VND integer only; formatting localized client-side |
