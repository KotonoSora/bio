# Publish Composer - UI Data Dictionary (feature 42)

Phase 1 BA · Surfaces: composer, content calendar, post status view, ops publishing console. All money-free; timestamps creator-local with the stored timezone explicit.

## Composer

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `videoUpload` | Upload + progress | Yes | ≤ 3 min, ≤ 300 MB, MP4/H.264 allowlist; resumable multipart | Gates verified pre-complete; failure cites the exact limit |
| `thumbnail` | Upload (optional) | No | Same validation class as video; poster-frame default | Auto poster-frame when absent |
| `caption` | Textarea | Yes | ≤ 2,200 chars base; per-network trims edited in place | Live char counter; X/Threads show their own counters |
| `targetPicker` | Checkbox list | Yes | Healthy accounts only (41); dead unselectable + reconnect link | Default = all healthy ("Post to All") |
| `trimWarnings` | Inline list | When trims apply | Names each network whose limit forces a trim | Trim preview per network |
| `scheduleMode` | Radio | Yes | `now` or `run_at` | `now` default |
| `runAt` | Datetime | When scheduled | Timezone-explicit (Asia/Ho_Chi_Minh default); future only | Lock stated: "Edits freeze 5 min before send" |
| `quotaMeter` | Meter | Yes | Free: "n of 10 lifetime"; pro: rolling-cycle state | Reads the shared counting function; over-quota blocks confirm with reset/upgrade path |
| `confirmAction` | Button | Yes | Disabled on any gate/quota failure | Success → draft/scheduled row |

## Content calendar

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `monthWeekToggle` | Segmented control | Yes | Month / week views | Month default |
| `postChip` | Chip per post | Yes | Status-colored: scheduled/sending/partial/sent/failed/cancelled/late | Drill-down opens status view |
| `conflictHint` | Inline marker | When detected | Two posts to the same network within the window | Advisory only; never blocks |
| `dragMove` | Drag interaction | Yes | Reschedules; refused inside lock window with copy | Optimistic with server confirm |

## Post status view

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `nOfSeven` | Headline | Yes | Computed from children: "5 of 7 published" | Never rounded up (BR-14) |
| `targetRow` | Row per target | Yes | Network, account, status, reason, provider ref link, attempts | Statuses closed set (BR-09) |
| `retryAction` | Button | On eligible rows | Eligibility-checked; dedupe/confirm gate per BR-11 | Disabled with reason when ineligible |
| `confirmGate` | Modal | On ambiguous timeout | Explicit "confirm before repost" acknowledgment | Only after poll-then-ask fails to verify |
| `lateBadge` | Badge | When late | Delivered-late timestamp + notice | Never silent |

## Ops publishing console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `networkPauseToggle` | Toggle + reason | Yes to flip | Audited; delists only the leg | Affected posts show `queued_behind_outage` |
| `queueDepth` | Chart | On load | Per-network depth + lag | Pages before creator-visible staleness |
| `poisonQueue` | Table | On load | Dead-lettered jobs: target, reason, redeliveries | Replay action; dual-control on mass replay |
| `dispatchLog` | Table (read-only) | On load | Scheduler runs: scanned, dispatched, held, errors | Idempotent runs; ids only |

## Validation and behavior notes

- Composer failures are pre-queue and specific: duration 3:01, size 301 MB, caption 2,201, disallowed codec each name the limit.
- No surface exposes token material, raw provider payloads, or other users' posts; all reads owner-scoped (RLS).
- Timezone is explicit everywhere; relative times are display-only.
- Empty states: no connections → CTA to 41; calendar empty → compose CTA; status view for a draft shows "not scheduled yet".
