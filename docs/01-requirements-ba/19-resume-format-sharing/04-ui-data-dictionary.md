# Resume Format Sharing - UI Data Dictionary (feature 19)

Phase 1 BA · Author dashboard + public gallery surfaces. Contracts: `docs/02-architecture-techlead/19-resume-format-sharing/`. Rules: `docs/01-requirements-ba/19-resume-format-sharing/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| My formats list | List | Auto | Drafts + published with status chips and version lists; `vN live - vN+1 draft` shown distinctly | Sorted by updated desc |
| Section picker | Checkbox list + drag order | Yes to build | Allowlist of snapshot section keys; order persisted as `sections[]` | All visible, default order |
| Layout variant select | Dropdown per section | No | Allowlist variants only (for example `single`, `two-column`, `compact`) | Per-section default |
| Token swatches | Swatch grid | No | Font/scale/accent/density chosen from swatches only - no text inputs that could smuggle CSS | Registry defaults |
| A4 rules | Preset selects + toggles | No | Margin presets; page-break hint toggles; no free numeric CSS | Default margins |
| Placeholder preview | Canvas | Auto | Renders the fictional fixture client- and server-side; accepts no snapshot input; label `Preview uses placeholder data` | Always on |
| Validation feedback | Inline field errors | Auto | Field-mapped, actionable (`format_personal_data` names the key path) | Hidden |
| Pre-publish checklist | Dialog | Yes to publish | Layout-only attestation checkbox + placeholder-preview confirmation; fix-before-publish list blocks the button | Blocks publish |
| Version chip | Chip | Auto | `vN` on published cards; `vN+1 draft` badge while a newer draft exists | Current version |
| Unpublish copy | Inline text | Auto | States clone independence: `Existing clones keep working` | On unpublish confirm |
| Gallery card | Card | Auto | Thumbnail (auto placeholder render), name, author line (username + profile link or frozen string), clone count (exact), version | Public surface |
| Gallery detail | Page | Auto | Layout summary, versions, `based-on` line where applicable, report link, `Use this format` | Public surface |
| Clone confirm | Dialog | Yes to clone | Names format + pinned version + pinning copy (`later author updates won't change your copy`) | Cancel default focus |
| `Based on` line | Text row | Auto | Author attribution permanent on the reuser's copy detail | Hidden for built-ins |
| Report action | Link + form | Yes to report | Turnstile inline; reason select + optional text; anonymous allowed; `Report received` (no tracking theater) | On every card/detail |
| Removed detail state | Page state | Auto | `This format was removed`; clones unaffected; no author blame copy | Post-takedown |
| Thumbnail upload | - | No | Does not exist in v1 (Q-06): thumbnails are auto placeholder renders | Absent by design |
| Offline builder notice | Banner | Auto | Draft persists in session storage; publish disabled with `You're offline` | On connectivity loss |

Copy discipline: no quotas/credits language anywhere (BR-09); no leaderboard/usage-count surfaces (Q-03); attribution wording never implies endorsement.
