# AI Design Assist - UI Data Dictionary (feature 16) - GATED

Phase 1 BA · GATED: components describe the future build; while gated, none of them exists in the bundle (AC-01). React 19.3; contextual entries in `/app/profile` and `/app/blocks` - no standalone AI page.

## Widgets and components (future)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| AssistPanel | contextual panel | yes | Inline near the acted-on editor (theme quiz by the gallery, bio polish by the bio field, ordering tidy by the block list); no standalone page | collapsed |
| ThemeQuiz | quiz | yes (theme entry) | 3 questions max, content-type + vibe; auto mode skips it | - |
| ThemePicks | ranked list | yes | Gallery-valid `theme_id`s only, with reasons; Preview uses the gallery's unsaved-preview mechanism | - |
| BioDrafts | cards | yes (bio entry) | Exactly 3 drafts (fewer when low confidence); tone selector; length cap enforced by the shared validator; diff-highlight vs current; character count | - |
| OrderingProposal | before/after list | yes (ordering entry) | Proposed positions + title edits; per-row accept checkboxes; `Apply selected` commits via the existing reorder endpoint | - |
| DiffView | shared diff component | yes | Same visual language as human edits - no AI-authority styling anywhere | - |
| AiOriginCaption | caption | yes | `Suggested by assistant` - dashboard-only, never visitor-facing | - |
| LowConfidenceBadge | badge | conditional | `Low confidence` + fewer options; never padded to look complete | hidden |
| RefusalState | inline state | conditional | `No safe suggestion - try different answers`; never error-styled | hidden |
| QuotaBanner | banner | conditional | `assist_quota_exhausted` -> upsell-safe copy + `Continue manually`; panel collapses; editors untouched | hidden |
| UnavailableBanner | banner | conditional | `Assistant unavailable` + Retry; navigation cancels in-flight generation | hidden |
| ApplyControls | buttons | yes | Per-suggestion explicit only; Apply routes through existing endpoints; results are ordinary editor dirty-state | - |

## Absence rules (enforced by review/grep; live now)

| Rule | Assertion |
|---|---|
| No assist surface while gated | Zero components, chunks, routes, or strings in the dashboard bundle (AC-01) |
| No auto-apply control | No auto-apply or auto-publish toggle exists anywhere - its absence is a requirement (AC-10) |
| No client-side caching of suggestions | Suggestions are never stored client-side; stale suggestions must be unrepresentable (BR-09) |
| No visitor-facing AI labels | Public pages carry no AI-origin markup of any kind (BR-08) |
| No custom-code input path | The panel offers no CSS/JS/HTML/font input surface, even as disabled UI (BR-03) |

## State and copy rules

| Item | Rule |
|---|---|
| Refusal copy | `No safe suggestion - try different answers` |
| Low-confidence copy | `Low confidence` badge; fewer options shown |
| Origin copy | `Suggested by assistant` caption per suggestion group |
| Quota copy | Upsell-safe quota message + `Continue manually`; never blocking editors |
| Outage copy | `Assistant unavailable` + `Retry`; no cached content ever shown |
| Applied state | After Apply, suggestions become normal editor state (dirty/saved flows, purge chip on publish) |
| Locale | All copy VN default with EN fallback; generation language follows the creator locale |
| Progress | Generation shows async progress; editors block never (cancel on navigation) |
