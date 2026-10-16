# Link Blocks - Workflow and User Journey (feature 08)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Create a block (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/blocks`, clicks `Add block` | Type picker shows the 13 frozen options (no free-text type) | BR-01 |
| 2 | Creator | Picks a type, fills the per-type form | Client mirrors schemas for hints only; schedule section offers toggle + from/until with `until > from` check | BR-03, BR-05 |
| 3 | SPA | Submits `POST /api/v1/blocks` | Disabled while in flight; non-`https:` URLs blocked pre-send with the same message as the server | BR-03, BR-12 |
| 4 | Profile service | Ownership assert -> payload validation -> `position = max + 1` -> save | `201` appends at the end | BR-04, BR-08 |
| 5 | Profile service | Audit + publish `profile.published.v1` | Purge event; public page catches up <= 2 min | BR-11 |
| 6 | SPA | Renders the new row with status chip | `422` maps the code to the exact field | BR-01, BR-03 |

## Journey 2 - Edit, hide, delete, duplicate

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Edits via row menu (type fixed, hidden in dialog) | `PATCH` sends changed fields only | BR-10 |
| 2 | Profile service | Owner-load -> merge -> re-validate -> save -> audit -> event | `200` merges into the list | BR-11 |
| 3 | Creator | Toggles hide | Optimistic chip flip; rollback + toast on failure | BR-05 |
| 4 | Creator | Deletes (confirm names the block) | `204`; row animates out; `Undo` for 5 s re-creates from cached payload as a new row | BR-09 |
| 5 | Creator | Duplicates | Copy inserted directly below the source; position comes from the server | BR-09 |

## Journey 3 - Reorder (consistency-critical)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Drags a row | Optimistic move renders instantly | BR-04 |
| 2 | SPA | On drop commits `POST /api/v1/blocks/reorder {profileId, ids}` | Further drags queue behind the in-flight commit (never parallel) | BR-12 |
| 3 | Profile service | Transaction: set-equality assert -> batch position update -> audit once -> event once | `200` confirms silently | BR-04, BR-11 |
| 4 | SPA | On `422 reorder_mismatch` | List rolls back to pre-drag order; toast with `Reload` action | BR-04 |
| 5 | System | Reload at any point | Dashboard and public page show the identical server order | BR-04 |

## Journey 4 - Scheduling

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Sets from/until pickers | Live label computed from the server rule: `Visible now` / `Goes live <time>` / `Expired` | BR-05 |
| 2 | Profile service | Validates `until > from` when both set | `422 bad_schedule_window` with zero writes | BR-05 |
| 3 | SPA | Expired rows | Sink to a collapsed `Out of window` section; never silently deleted | BR-05 |

## Journey 5 - Public read (renderer)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Public-site service | Internal call for `/:username` blocks (cache miss) | One windowed list: `is_visible AND now in window`, ordered `position ASC, id ASC` | BR-05 |
| 2 | Profile service | Serves the filtered list | No drafts, no reasons, no owner fields on the public payload | BR-08 |
| 3 | Visitor | Opens the page | Blocks render in the arranged order; ties broken stably by id | BR-04 |
