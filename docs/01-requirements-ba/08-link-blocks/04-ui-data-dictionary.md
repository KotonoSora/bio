# Link Blocks - UI Data Dictionary (feature 08)

Phase 1 BA · React 19.3 components for `/app/blocks` (or the profile-editor tab). Server verdicts final; the client mirrors schemas for instant hints only. Topology: `docs/02-architecture-techlead/08-link-blocks/01-architecture-and-tech-stack.md`.

## Screen: Block editor `/app/blocks`

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| BlockList | sortable list | - | server order; drag handle per row; keyboard move-up/down controls required (a11y) | - |
| ThumbnailCell | image | - | renders `thumbnailKey` when present; `16:9` platform-fixed | placeholder glyph |
| TitleCell | text | - | payload title or type default label | - |
| TypeChip | badge | - | one of the 13 frozen types | - |
| StatusChip | chip + popover | - | `visible`, `hidden`, `scheduled_future` (shows from-date), `expired` (shows until-date) | from `hiddenReason` |
| RowMenu | menu | - | Edit, Duplicate, Hide/Show, Delete | - |
| OutOfWindowSection | collapsed group | - | expired/out-of-window rows; never deleted silently | collapsed by default |
| UndoToast | toast | - | 5 s window; re-creates from cached payload as a NEW row (stated in copy) | - |
| EmptyState | illustration + button | - | `No blocks yet` + `Add block` | - |
| PendingOpSpinner | per-row spinner | - | one `pendingOp` per row; blocks only that row's actions | - |

## Dialog: Add/Edit block

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| TypePicker | grid of 13 options | Yes (create) | frozen list; no free-text entry; hidden on edit | - |
| UrlInput | url input | Yes (link/social/embed and typed rows) | `https:` + non-empty host, <= 2048 chars; message identical to server | empty |
| TitleInput | text input | No | <= 120 chars; counter | empty |
| SizeControl | segmented control | No | `small`, `medium`, `big`, `button` | `medium` |
| ThumbnailSlot | image upload slot | No | returns `thumbnailKey`; upload owned by feature 09 pipeline | empty |
| NetworkSelect | select | Yes (social) | Instagram, TikTok, YouTube, generic | - |
| ProviderInput | select + label | Yes (embed) | provider id + URL; hint "the renderer decides the markup" | - |
| GalleryItems | repeatable rows | Yes (gallery) | per item: `url` (`https:`) + `alt` (<= 160) | empty |
| DocumentPicker | select | Yes (resume) | lists the owner's resume documents (features 17-21); returns `documentId` | - |
| VisibilityToggle | switch | Yes | hides block publicly without deletion | on |
| FromPicker / UntilPicker | date-time pickers | No | `until > from` when both set; live state label below | empty (open-ended) |
| LiveStateLabel | computed text | - | `Visible now` / `Goes live <time>` / `Expired` from the server rule | computed |
| SubmitButton | primary button | - | disabled while pending | `Save block` |

## Errors and edges

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| FieldError | inline text | - | maps `code` to one field (`bad_url_scheme`, `title_too_long`, `payload_too_long`) | hidden |
| ScheduleError | inline text | - | `bad_schedule_window` under the schedule section | hidden |
| ReloadToast | toast + action | - | `reorder_mismatch` -> rollback + `Reload` | hidden |
| BlockLimitNotice | inline notice | - | at 250 blocks, `Add block` disabled with cap restated | hidden |
| SessionExpiryState | redirect | - | re-login preserves in-progress dialog input (session storage) | - |
