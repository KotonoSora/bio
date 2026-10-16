# Realtime Global - UI Data Dictionary (feature 14)

Phase 1 BA · React 19.3; panel lives atop `/app/analytics/overview` (pro) and in the same position in the installed PWA (feature 11). Component topology: `docs/02-architecture-techlead/14-realtime-global/01-architecture-and-tech-stack.md`.

## Widgets and components

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| RealtimePanel | panel | yes (pro) | Rendered only when the session plan claim is pro; on `403` mid-session swaps to teaser with no error copy | hidden for free |
| LockedTeaser | lock card | yes (free) | Static; zero realtime fetches; upgrade path link; occupies the panel position | - |
| VisitorsStat | big number | yes | `visitors` from payload only; label `visitors - last 30 min`; as-of sub-label always visible | 0 |
| CpmSparkline | sparkline | yes | `cpmSeries` 30 ints oldest->newest; downsampled on small screens; exact totals in tooltip + aria label | flat line |
| TopLinks | list | yes | Up to 8 rows, server order; deleted block title renders `Deleted link` | empty note |
| GeoList | list | yes | Up to 5 country rows; server sends ISO codes; client maps to localized names | empty note |
| SalesStrip | strip | conditional | Renders only when `sales` is present; revenue series + lag label; never zero-filled | absent |
| AsOfStamp | timestamp | yes | Every card shows `as of HH:MM:SS` derived from `generatedAt`; no relative timers | - |
| DegradedBanner | banner | conditional | On `status:"degraded"`: last-good time + delayed copy; poll stretches to 60 s | hidden |
| ThrottledCopy | inline note | conditional | On `429`: countdown until retry; backoff steps visible | hidden |
| ReconnectButton | button | conditional | After 3 consecutive errors; manual only - no auto-resume loop | hidden |
| AggregatesFootnote | footnote | yes | One line, rendered once: live feed is operational, totals come from hourly aggregates (BR-05) | - |

## Absence rules (enforced by review/grep)

| Rule | Assertion |
|---|---|
| No SSE/websocket client | v1 is short-poll only; no push client exists (BR-02) |
| No client-side counting | The client renders payload numbers; it never dedupes, sums, or re-ranks (BR-10) |
| No cross-tab coordination | The server throttle is the backstop; no localStorage/broadcast polling lock exists (BR-06) |
| No fabricated sales | `sales` absent -> nothing renders; no placeholder zeros (BR-08) |
| No hidden-tab polling | `document.hidden` pauses all realtime requests (BR-09) |

## State and copy rules

| Item | Rule |
|---|---|
| As-of copy | `as of {HH:MM:SS}` in the viewer's locale |
| Footnote copy | `Live view - totals are finalized from hourly aggregates` |
| Degraded copy | `Live data delayed - last good {time}` |
| Throttled copy | `Too many requests - retry in {n}s` |
| Quiet zero copy | `No visits in the last 30 minutes` under the stat when `visitors=0` and status `ok` |
| Deleted link copy | `Deleted link` for missing block titles |
| Demotion behavior | Swap to teaser silently; no toast, no error text (US-04) |
| Sales lag copy | `Sales may lag up to {n} min` driven by the server lag label, only when `sales` present |
| Geo labeling | `Countries` header (country-grain; DEVIATION CARRIED from feature 12 Q-04); server sends ISO codes |
