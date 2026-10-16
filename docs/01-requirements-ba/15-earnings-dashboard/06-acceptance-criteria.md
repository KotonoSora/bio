# Earnings Dashboard - Acceptance Criteria (feature 15)

Phase 1 BA · BDD. Drills run against `docs/02-architecture-techlead/15-earnings-dashboard/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Gating drill | Pre-revenue profile; then each revenue source ships its flag | Dashboard payload and DOM inspected | Pre-revenue: engagement-only, zero money keys, zero stubs. Shipped: its strip lights within pipeline lag; no inferred panels |
| AC-02 | Consistency drill | Unified view and origin tabs/order list at one `generatedAt` | Figures compared; CSV downloaded | Every figure matches its origin exactly; CSV reconciles cell-for-cell including `source` and `generated_at` columns |
| AC-03 | Attribution drill | Fixture with known revenue and clicks for one link + window | `rpc` rendered | Same-window division matches fixture (round-half-up); mismatched-window fixture fails review; purchase-day attribution stated |
| AC-04 | Honesty drill | Unshipped source; timed-out source; gross-only source | Rows render | `not_yet_available` never a zero; timeout shows `temporarily unavailable` + retry; gross/net verbatim; money totals hidden when incomplete |
| AC-05 | IDOR drill | User A requests user B's `profile` on both endpoints | Request evaluated | `403 foreign_profile` on JSON and CSV; no fan-out occurs |
| AC-06 | Abuse drill | CSV flood from one user; cancel mid-stream | Export streamed | Flood throttled (`429` + `Retry-After`) with flat memory; cancel stops the server stream, no orphaned work |
| AC-07 | Speed drill | 500-link history; idle screen 10 min | Table paged; request log read | 25 rows/page server-side; zero idle requests (no interval polling); focus refresh only on return |
| AC-08 | Traceability drill | Any row's trace link | Link opened | Origin opens in the identical range window; cell tooltips show source + lag + timestamp everywhere |
| AC-09 | Parity drill | Browser + installed PWA at one range | Both opened | Identical values and labels; arrangement may collapse (feature 11 same-numbers) |
| AC-10 | Single-formula drill | Same link + window on audience and earnings surfaces | CTR rendered | Both consume `ctr_x1000` (window sums; null under 30 views); no second formula exists in code (`grep` clean, 13 AC-08) |
| AC-11 | Violation drill | Codebase + schema inspected | Grep + finance review | No dashboard-side net math, no money table, no `CREATE TABLE` in this feature; violation fails review |
| AC-12 | Grain-separation drill | Pro profile with live ticks during polling | Response and screen inspected | Unified payload carries no live counters; ticks render only via 14's component; no server mixing (`grep` clean) |
| AC-13 | Same-numbers drill | Overview, audience, and earnings at one `generatedAt` | All fetched | Shared figures are identical across tabs; earnings embeds the same components as its sources |
