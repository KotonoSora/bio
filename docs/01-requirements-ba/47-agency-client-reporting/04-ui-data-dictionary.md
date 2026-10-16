# Agency Client Reporting - UI Data Dictionary (feature 47)

Phase 1 BA · Surfaces: per-profile report, combined view, leaderboard, saved views, share dialog, client share page. Aggregates only; no visitor-level rows ever render.

## Per-profile report

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `periodPicker` | Range picker | Yes | 7/30/90/custom ≤ 366 days | Timezone + boundaries stated |
| `viewsStat` | Stat + trend | On load | Shared read component (12/15) | Parity with creator dashboard or metric marked |
| `clicksStat` | Stat + trend | On load | Shared read component | Same parity rule |
| `ctrStat` | Derived stat | On load | Clicks ÷ views (definition owned by 12) | Hidden when views = 0 with reason |
| `topLinksTable` | Table | On load | Top links from 31's read | Absent → section hides with reason |
| `revenueSection` | Stat + units | Conditional | Billing internal summary; only where data exists | Hidden with "no sales yet" — never zero-filled |
| `sourceHealthNote` | Note | On degrade | Reason + last-good timestamp | Never stale-as-fresh |
| `discrepancyMark` | Badge | On mismatch | Metric marked, never restated | Support path linked |

## Combined view

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `profilePicker` | Multi-select | Yes | Linked profiles only (45's access read) | Selections never include unlinked profiles |
| `rollupStats` | Stat row | On load | Read-time sum of per-profile truth | Equals the sum exactly |
| `perProfileRows` | Table | Always | One row per selected profile | Rollup never hides a profile |
| `saveViewAction` | Button | Yes to act | Owner/manager only | Saved per org |

## Leaderboard

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `metricTabs` | Tabs | Yes | Views / clicks / CTR (+ sales where data exists) | Sales tab hides honestly otherwise |
| `methodNote` | Note | Yes | States ranking method + tie-breaks on-screen | Clicks desc, then stable profile id |
| `rankTable` | Table | On load | Rank, profile, value, delta vs previous snapshot | Zero-traffic profiles rank last with zeros |
| `snapshotDate` | Meta | Yes | Daily snapshot standing | Trend available across snapshots |

## Share dialog + client page

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `shareScope` | Radio | Yes | `curated` (default, no revenue) / `full` (explicit owner/manager action) | Curated preselected |
| `shareExpiry` | Select | Yes | ≤ 30 days | 30 days default |
| `shareAction` | Button | Yes to act | Signed token hashed at rest; `noindex` | Copyable link |
| `revokeAction` | Button | Yes to act | Immediate death + audit | Also fires on unlink/membership change |
| `clientSharePage` | Read-only page | On open | No login; curated/full per share scope | Expired/revoked → neutral page |

## Member working view

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `assignedList` | List | On load | Assigned clients only (45's access read) | No org-wide totals render |
| `defaultView` | Report | On load | Own working view; saved views are owner/manager surfaces | Cross-assignment reads are impossible |

## Validation and behavior notes

- Every value carries its period + timezone context; no undated numbers render.
- No cross-org control exists anywhere; benchmarking requests return the isolation explanation.
- Copy for hidden sections: "No sales yet", "Analytics started {date}", "{source} is slow — showing {time} data."
- Empty states: no linked profiles → link CTA (45); no traffic yet → honest zero-state with start date.
