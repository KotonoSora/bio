# AI Design Assist - Workflow and User Journey (feature 16) - GATED

Phase 1 BA · GATED: journeys 1-6 describe the future build; journey 7 (absence) is the only live flow. Rules cite `02-user-stories-and-business-rules.md`.

## Journey 1 - Theme recommend (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens the theme gallery; starts the 3-question quiz (or auto) | `POST assist/theme`; progress state; editors stay usable | BR-05, BR-09 |
| 2 | System | Response returns | Ranked gallery picks with reasons; each Preview applies tokens in the gallery's unsaved preview only | BR-02 |
| 3 | Creator | Clicks Apply on one pick | Existing theme PUT runs; audit actor = user; purge chip follows the normal publish path | BR-04 |
| 4 | Creator | Discards | Nothing persists; preview clears | BR-04 |

## Journey 2 - Bio polish (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Picks a tone, clicks `Suggest` | `POST assist/bio`; 3 draft cards with diff vs current bio and character count | BR-02, BR-08 |
| 2 | Creator | Uses a draft | Bio field fills (still unsaved); the profile save flow owns persistence + purge | BR-04 |
| 3 | System | Low confidence response | `Low confidence` badge with fewer options shown - never padded | BR-08 |

## Journey 3 - Ordering tidy (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Clicks `Suggest order` | `POST assist/ordering`; before/after list of positions + title edits | BR-02 |
| 2 | Creator | Checks rows, clicks `Apply selected` | Existing reorder endpoint commits with standard conflict/rollback semantics - AI proposals fail exactly like human drags | BR-04 |
| 3 | System | No click history available | Labels-only proposals; no invented numbers | BR-08 |

## Journey 4 - Refusal (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Asks for custom CSS/JS/fonts (or an adversarial prompt demands it) | `200 {suggestions:[], refused:true}` | BR-02 |
| 2 | Creator | Sees the state | `No safe suggestion - try different answers`; not an error-styled dead end | BR-02 |

## Journey 5 - Quota exhausted (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Requests generation after the cap | `429 assist_quota_exhausted`, upsell-safe copy | BR-05 |
| 2 | Creator | Continues editing | Panel collapses to a banner with `Continue manually`; all editors function | BR-09 |

## Journey 6 - Outage (future)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Model capability unavailable | Panel shows `Assistant unavailable` + Retry; no cached suggestions ever render | BR-09 |
| 2 | Creator | Navigates away mid-generation | Fetch cancels cleanly; no orphaned work, no partial apply | BR-04 |

## Journey 7 - Absence (LIVE now, until the gate lifts)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Build tooling | Any release candidate | Schema diff, OpenAPI diff, source + bundle grep assert zero assist surface | BR-01 |
| 2 | Reviewer | Promotion review | A hit on `assist/`, `suggestion_draft`, or `kind='design-assist'` blocks promotion by tooling, not policy | BR-01 |
| 3 | Creator (today) | Uses the dashboard | Manual theme/block/profile editing only - no assist entry point exists | BR-01 |
