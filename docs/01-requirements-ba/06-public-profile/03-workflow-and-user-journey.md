# Public Profile - Workflow and User Journey (feature 06)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Claim (first run)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Signs in (SSO) and opens `/app/profile` | SPA `GET /api/v1/profile` -> `404 profile_absent`; claim variant renders | BR-03 |
| 2 | SPA | Types a username | Debounce 300 ms -> `POST /api/v1/profile/username-check`; normalized echo + verdict render per keystroke-pause | BR-01 |
| 3 | SPA | Submits claim (`PUT` + CSRF + Turnstile) | Submit disabled while pending | BR-12 |
| 4 | Profile service | Normalize -> policy (length, charset, reserved, retired, taken) | `422` with the exact code; nothing persisted | BR-01, BR-02 |
| 5 | Profile service | Inserts the row, audit, publishes `profile.published.v1` | `200 ProfileResponse` | BR-05, BR-09, BR-11 |
| 6 | SPA | Renders the page; purge chip clears within 2 min | `US-07` |

## Journey 2 - Edit and save

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Edits displayName / bio / SEO fields / toggles | Client trims + maxlength hints (never a verdict) | BR-07 |
| 2 | SPA | Saves (`PUT` + CSRF) | Disabled while pending; one request in flight | BR-08 |
| 3 | Profile service | Sanitize -> validate -> save -> audit -> publish event | `200` -> toast `Saved` + purge chip | BR-06, BR-11 |
| 4 | SPA | Polls the public URL (HEAD) while chip pending | Chip clears on fresh content or after 2 min with `still publishing` | BR-09 |

## Journey 3 - Rename wizard (safety-critical)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `Change username`, types a new name | Debounce 300 ms -> check call; normalized echo + rule verdict | BR-01 |
| 2 | SPA | Confirms on screen 2 | Shows old -> new, the 301 guarantee, and the permanent-reservation notice | BR-05 |
| 3 | Profile service | `POST username-change` in one transaction: lock row -> re-run policy -> insert redirect -> update username -> audit -> event | `200 {username}` | BR-05, BR-09 |
| 4 | SPA | Swaps store username; shows `Your old link now redirects (301)` + purge chip | Polls until live (max 2 min) | US-06 |
| 5 | System | Old URL requested later | `301` to the new URL, permanently | BR-05 |

Alternative: raced rename (name taken between check and confirm) -> `422 username_taken`, wizard returns to screen 1 with the name marked taken.

## Journey 4 - Public read resolution

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Opens `/:username` | Public-site cache lookup (owned by feature 10) | BR-09 |
| 2 | Public-site service | Cache miss -> internal resolve call | `result=profile` -> SSR 200; `result=redirect` -> 301; `result=missing` -> 404 | BR-04 |
| 3 | Public-site service | Private profile resolves as `missing` | 404 with the same shape and timing class as unknown | BR-04 |

## Journey 5 - Visibility toggle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Flips the visibility switch | Optimistic UI; `PUT {isPublic}` in flight | BR-04 |
| 2 | Profile service | Persists, audits, publishes event | `200` -> copy `Hidden - public page returns 404` (or `Live`) + sitemap notice | BR-04, BR-09 |
| 3 | SPA | On failure | Switch rolls back; problem code toasted | BR-08 |
