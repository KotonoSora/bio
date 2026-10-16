# Public Profile - UI Data Dictionary (feature 06)

Phase 1 BA · React 19.3 components for `/app/profile` (client render; public `/:username` is SSR owned by feature 10). Integration topology: `docs/02-architecture-techlead/06-public-profile/01-architecture-and-tech-stack.md`.

## Screen: Profile editor `/app/profile`

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| AvatarSlot | image + upload trigger | No | pipeline owned by feature 09; 2 MB cap server-side | default glyph |
| UsernameRow | text + Change button | Yes (exists) | displays current username verbatim; no inline edit | opens rename wizard |
| DisplayNameInput | text input | No | <= 80 chars; maxlength only client-side | current value |
| BioTextarea | textarea + counter | No | <= 500 chars; counter shows used/remaining | current value |
| SeoTitleInput | text input | No | <= 70 chars; live SERP-style preview line | current value |
| SeoDescInput | text area | No | <= 160 chars | current value |
| PublicToggle | switch | Yes | flips optimistically; PUT on change | current `isPublic` |
| ShowResumeToggle | switch | Yes | controls resume section on the bio page only | current `showResume` |
| SaveBar | sticky bar + button | - | disabled while pending; one in-flight request | appears when form dirty |
| PendingChip | status chip | - | `publishing...` while the public page catches up; clears <= 2 min | hidden |
| ProblemBanner | alert | - | renders problem `code` + human text from the server | hidden |
| FieldError | inline text under field | - | maps `code` to exactly one field (see `05-exception-handling-and-errors.md`) | hidden |
| FirstRunClaimHeader | header + explainer | - | first-run variant when `GET` returns `profile_absent` | shown once |

## Dialog: Rename wizard

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| UsernameInput | text input | Yes | maxlength 30; no regex, no reserved list in client bundle (grep-gated) | empty |
| NormalizedEcho | text | - | shows the server-normalized form (`Alice!` -> `alice!`) | empty |
| AvailabilityVerdict | status line | - | `available` (green) or `reason` (red) naming the violated rule | empty |
| OldToNewReadout | text | - | screen 2: `alice -> alice-dev` in large type | - |
| RedirectNotice | static text | - | exact copy: "Your old link will redirect to the new one permanently (301)." | - |
| ReservationNotice | static text | - | exact copy: "Your old username stays reserved forever and cannot be claimed by anyone." | - |
| ConfirmButton | primary button | - | disabled while pending or when verdict is not `available` | "Change username" |
| CancelButton | secondary button | - | returns to screen 1, input preserved | - |

## States

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| SkeletonState | skeleton cards | - | shown while `GET` pending; never a half-rendered form | - |
| OfflineState | inline notice | - | disables Save with `You're offline`; no queued mutations; drafts in memory only | - |
| SessionExpiryState | redirect | - | re-login preserves wizard input (session storage), resumes at screen 1 | - |
| CooldownText | inline notice | - | `429` renders the server `Retry-After` as readable cooldown | - |
