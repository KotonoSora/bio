# Short Links - UI Data Dictionary (feature 29)

Phase 1 BA · Component types are React 19.3 building blocks for `/app/links` (client render only; the redirect path has no UI). Integration topology: `docs/02-architecture-techlead/29-short-links/01-architecture-and-tech-stack.md`.

## Screen: Link manager `/app/links`

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| ShortUrlCell | text + CopyButton | - | shows `https://<host>/go/<code>`; clipboard API | tooltip "Copied"; code never editable |
| DestinationPreview | truncated text + tooltip | - | truncate at 60 chars; tooltip shows the full URL | - |
| StatusChip | badge | - | one of `live`, `expired`, `suspended`, `dormant`; popover explains each | - |
| OpensCell | number | - | from feature 31 handoff; shows "-" until feature 31 ships | - |
| ExpiryCell | date | - | rendered in user locale; "never" when null | - |
| RowMenu | menu | - | Edit, Tombstone, Stats (feature 31), QR (feature 30) | - |
| QuotaMeter | progress bar + label | - | "N of M mints used, resets <date>"; New disabled at N = M | values from response headers |

## Dialog: Mint short link

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| DestinationInput | url input | Yes | `https://` scheme, <= 2048 chars; inline error otherwise | empty; paste-friendly |
| KindToggle | segmented control | Yes | Random / Vanity | Random |
| VanityCodeInput | text input | Yes when Vanity | 3-32 chars `[a-z0-9-]`, no leading/trailing hyphen; folded to lowercase | prefilled from destination hostname slug |
| ExpiryPicker | date-time picker | No | must be future, min now + 1 h | empty (= never) |
| FallbackUrlInput | url input | No | `https://`, <= 2048 chars | empty |
| TurnstileWidget | invisible challenge | Yes | platform Turnstile site key | silent |
| SubmitButton | primary button | - | disabled while in flight, or when quota used = limit | "Create link" |
| ErrorRegion | alert | - | message mapped from error `code` (`05-exception-handling-and-errors.md`) | hidden |

## Dialog: Edit link

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| CodeReadout | text + lock icon | - | read-only; caption "Codes never change and are never reused" | current code |
| DestinationInput | url input | Yes | same rules as mint; blocklist re-checked server-side | current destination |
| ExpiryPicker / FallbackUrlInput | as mint | No | same rules | current values |

## Dialog: Tombstone confirm

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| ConfirmCopy | static text | - | exact copy: "Link stops resolving. Past stats are kept. The code is never reused." | - |
| ConfirmCheckbox | checkbox | Yes | "I understand" | unchecked |
| ConfirmButton | destructive button | - | enabled only with checkbox checked | "Tombstone link" |

States: empty list ("No links yet" + New button); loading skeleton; all server errors rendered from the `code` map, never invented client copy.
