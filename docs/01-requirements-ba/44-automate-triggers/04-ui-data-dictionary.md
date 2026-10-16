# Automate Triggers - UI Data Dictionary (feature 44)

Phase 1 BA · Surfaces: rule builder (with preview), trigger log, intent list, ops health + template review. IG-only copy states the Business/Creator requirement.

## Rule builder

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `ruleKind` | Radio | Yes | `keyword` / `any_comment` | `keyword` default |
| `keywords` | Tag input | For keyword kind | Exact phrases (single/multi-word); case-folded, trimmed; collision-checked server-side | Duplicate/overlapping phrases rejected with `keyword_collision` |
| `mediaScope` | Media picker | Yes | Specific posts or account-wide | Account-wide default; warning copy for catch-all scope |
| `delaySeconds` | Number | Yes | ≥ 60 | 60 default; higher allowed |
| `replyTemplate` | Textarea | Yes | Public reply copy; length limit; transparency (no human impersonation) | Placeholders `{keyword}` allowed |
| `dmTemplate` | Textarea | Yes | Exactly one link slot `{link}`; https + destination allowlist; prohibited-content classes refused | Save-time checks; edit re-runs checks |
| `previewAction` | Button | Yes | Renders reply + DM for a sample comment | Shows link destination + validity |
| `ruleQuotaMeter` | Meter | Yes | Free 3 keyword / 4 catch-all; pro unlimited | Save blocked over quota with pause-oldest path |
| `connectionState` | Chip | Yes | Healthy IG Business/Creator with required scopes | Suspended state with reconnect CTA when unhealthy |
| `enabledToggle` | Toggle | Yes | Pauses/resumes matching + delivery | Paused intents hold; resume replays in order |

## Trigger log + intents

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `triggerRow` | Table row | On load | Observed comment (masked ref), media, decision (`matched/no_match/self_comment/blocklisted`), matched rule, time | Newest first; filter decision/rule |
| `intentStatus` | Chip | Yes | Closed set: `matched, held_quota, held_quiet, held_cap, sending, replied, dm_sent, failed, skipped_suppressed, window_lapsed, cancelled` | Reasons in plain language on hover/detail |
| `holdReason` | Meta | On holds | Quota / quiet / cap with release estimate | "Sends resume after 08:00" style copy |
| `fireCapMeter` | Meter | Per automation+media | Rolling-hour sends vs cap | Warning when overflow queue is non-empty |

## Ops surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `automationHealth` | Cards | On load | Error rate 10-min, pause state, overflow depth, per-network scopes | Zero message bodies |
| `reinstateAction` | Button + reason | Yes to act | Ops-only; audited | Resumes paused intents in order |
| `templateReviewQueue` | Table | On load | Reported templates: automation, report class, state | Pause-until-decided |
| `reviewDecision` | Approve/Reject + reason | Yes to act | Audited; reject blocks the template | Creator notified either way |

## Validation and behavior notes

- Copy is transparency-first: "This is an automated reply — it comes from {creator}'s keyword rule."
- One-link enforcement is visible at save and preview; the DM never contains a second URL, tracking pixel, or drip hook.
- Quiet hours and caps are shown as release estimates, never as errors the creator must fix.
- Empty states: no rules → builder CTA; log empty → "No comments seen yet"; suspended → reconnect path (41).
