# Audience Insights - UI Data Dictionary (feature 13)

Phase 1 BA · React 19.3 tab `/app/analytics/audience`; shared component library inherits feature 12's `FetchState`, lag footnotes, and clamp-back behavior. Topology: `docs/02-architecture-techlead/13-audience-insights/01-architecture-and-tech-stack.md`.

## Widgets and components

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| DimensionTabs | tabs | yes | device \| geo \| referrer \| UTM \| returning; one tab fetched at a time; switching cancels in-flight | `device` |
| BarRows | list | yes | key label, absolute views, share %; server order (top first) | top-25 + `Other (n campaigns)` |
| ReturningDonut | donut | yes (returning tab) | `new` vs `returning`; definition copy states the 30-day seen-before window once | - |
| ReferrerIcons | inline labels | yes | social/search/direct classes with icons read from server rollup labels | - |
| UtmDrilldown | drill-down list | yes (UTM tab) | key -> per-campaign rows; pagination 25 rows, server-side ordering — no client sorting of full keyspaces | - |
| PerfTable | table | yes | columns: link, clicks, CTR, rank, delta; ties share rank numbers; CTR displayed from `ctrX1000` only, formatted as percent | sort by rank |
| MoversTable | sub-table | yes | top gainers/fallers with sparklines from the per-day series | - |
| OverlayToggles | toggles | yes | previous-period + year-ago; disabled when span not retained, one-line reason; `aligned` badge when weekday-shifted | both off |
| ClampBack | picker behavior | yes | shared with overview: `422 range_clamped` -> snap back + plan text; downgrade re-clamps on next fetch | 30d |
| NotEnoughData | inline state | yes | CTR hidden under 30 views in-window with `not enough data` copy | - |
| EmptyUtmState | illustrated panel | yes | untagged links -> `Tag links with utm_campaign to unlock this view` + editor link | - |
| VirtualList | list behavior | yes (beyond 100 rows) | virtualize; DOM node count bounded on viral campaigns | - |

## Absence rules (enforced by review/grep)

| Rule | Assertion |
|---|---|
| No client CTR recomputation | CTR arrives computed in `ctrX1000`; the client formats only (BR-05) |
| No client sorting of full keyspaces | ordering and `Other` collapse are server-side (BR-10) |
| No new tracking | no cookie, no pixel, no fingerprint exists in this tab (BR-02) |
| No bypass path | deep-linking a tab with an out-of-plan range still clamps (BR-07) |

## State and copy rules

| Item | Rule |
|---|---|
| Alignment copy | 1 line under overlay charts: `Compared on matching weekdays` when `aligned:true` |
| Returning copy | `Returning = visited this profile within 30 days` stated once on first donut render |
| Year-ago copy | `Not available yet on your plan - needs longer history` (disabled toggle) |
| Low-traffic copy | `Not enough data (needs 30+ views)` |
| Timezone | Day buckets rendered in the viewer's local zone, labeled; server `day` remains the contract |
