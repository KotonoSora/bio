# Profile Analytics - UI Data Dictionary (feature 12)

Phase 1 BA · React 19.3 dashboard surfaces for `/app/analytics/overview`. Topology: `docs/02-architecture-techlead/12-profile-analytics/01-architecture-and-tech-stack.md`.

## Widgets and components

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| FetchState | shared state component | yes | wraps every widget: loading skeleton -> content \| empty \| degraded | skeletons, never a page-blocking spinner |
| TotalsRow | stat cards | yes | views, clicks, engagement (clicks/views) with period deltas; server-computed deltas, client formats only | range default 30d |
| DailySeriesChart | chart | yes | series points already downsampled (cap 120); totals shown must equal payload totals | renders from payload, no refetch |
| LinkTable | table | yes | 25 rows then `show more`; columns: title, clicks, share bar; share = clicks / total clicks | client-side paging |
| SourcesList | list | yes | top-N referrer groups (audience-owned read; empty until feature 13 ships) | - |
| CountriesList | list | yes | top-N country groups from the edge geo header (empty until feature 13 ships) | - |
| FreshnessFooter | footer text | yes | `generatedAt` + `hourly data` label once per widget group | always visible while numbers are |
| RangePicker | segmented control | yes | free 7/30/90d; pro adds 365d; out-of-plan selection snaps back on `422 range_clamped` with plan text | 30d |
| RealtimeLockedCard | locked teaser | yes (free) | zero realtime fetches while locked; pro link goes to feature 14's panel | hidden for pro |
| EmptyState | illustrated panel | yes | shown when a profile has zero data; includes `Share your page` CTA; never zero-charts implying failure | - |
| DegradedBanner | banner | yes | appears when `lagSeconds` exceeds threshold; numbers stay visible with stale chip | hidden when fresh |
| ErrorStates | inline state | yes | `403` -> re-login; `429` -> cooldown text with retry timer | - |

## Absence rules (enforced by review/grep)

| Rule | Assertion |
|---|---|
| No bot toggle | no "include bots" control exists in v1 (backend Q-01 default) |
| No client-side aggregation | percentages derived from the same payload; nothing refetched differently per device |
| No interval polling | refresh on focus only; hidden tab issues zero requests |
| No raw-log access | no UI path reads or joins raw NDJSON, ever |

## State and copy rules

| Item | Rule |
|---|---|
| Stale chip copy | `Data delayed - last updated {time}` attached to the group, not per number |
| Clamp copy | `Available on your plan: 7/30/90d` (EN fallback; VN default) |
| Deleted block label | `Deleted link` with historical counts preserved |
| Timezone | day buckets rendered in the viewer's local zone, labelled; server `day` is the contract |
