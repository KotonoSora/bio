# Account and Access - Workflow and User Journey (feature 01)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/01-account-and-access/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/01-account-and-access/02-user-stories-and-business-rules.md`.

## Login (new and returning)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Clicks a provider button | 302 to IdP carrying `state` + `nonce` + PKCE (single-use, 10 min TTL) | BR-01, BR-04 |
| 2 | IdP | Consents / denies | Returns to callback with `code`+`state`, or generic failure redirect | BR-04 |
| 3 | System | Callback arrives | Validates state/PKCE server-side, exchanges code, fetches verified profile; any failure -> generic `auth_failed` | BR-04 |
| 4 | System | Identity resolution | Find-or-create account by `(provider_id, provider_sub)`; email never merges | BR-02, BR-03 |
| 5 | System | Account ready | Creates session (registry row + edge validation key), sets `HttpOnly` cookie | BR-06 |
| 6 | System | Post-login routing | No profile yet -> choose-username screen; else `callbackURL` or `/app` | BR-14 |
| 7 | System | Login completes | Audits the event; absent/unverified email -> completion prompt for contact email | BR-03, BR-11 |
| 8 | Returning user | Logs in after IdP email change | Same account via stable `sub`; verified email syncs only when collision-free; collision -> `email_conflict` notice, no merge | BR-02, BR-03 |
| 9 | Returning user | Private relay / denied email | Login succeeds; contact-email prompt shown; never blocked | BR-03 |

## Linking and unlinking providers

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Settings -> Connect provider | Fresh step-up required if older than 10 min, then OAuth dance | BR-05, BR-07 |
| 2 | System | Callback validates | Inserts link row for the current account only; a `sub` already linked elsewhere is refused | BR-05 |
| 3 | Creator | Unlink provider | Fresh step-up; last remaining provider cannot be unlinked (`409 last_link_forbidden`) | BR-05, BR-07 |
| 4 | System | Unlink succeeds | Removes the link; both remaining/other logins still resolve to one account; audits both transitions | BR-05, BR-11 |

## Sessions, revoke, and logout

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens account settings -> Sessions | Lists sessions with coarse device label, ip-class, coarse last-active, current-session marker | BR-06 |
| 2 | Creator | Revokes one session | Registry row revoked + edge key deleted; in-flight JWTs die within 60 s | BR-06 |
| 3 | Creator | Revokes all others | Every session except the current one revoked identically | BR-06 |
| 4 | Creator | Logs out | Current session revoked, cookie cleared across tabs | BR-06 |
| 5 | System | Any later request on a revoked session | 401 `unauthenticated`; no degraded path exists | BR-08 |

## Sensitive-action step-up

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Attempts billing change / username change / unlink / deletion / Act-as-user / billing-contact transfer | Freshness check against `≤ 10 min` re-auth; stale -> `403 step_up_required` with re-auth path | BR-07 |
| 2 | Creator | Completes the re-auth dialog | SSO re-dance refreshes the session's re-auth time | BR-07, BR-04 |
| 3 | System | Freshness satisfied | Action proceeds; step-up itself is audited | BR-07, BR-11 |

## Lockout recovery (lost all provider access)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Contacts support | Documents an identity path (billing receipt + account email, or org-owner attestation) | BR-12 |
| 2 | Support/ops | Triggers one-time re-link invitation | Signed single-use token, 15 min TTL; evidence reference recorded | BR-12 |
| 3 | Creator | Opens the invitation | One-time link flow for a new provider; no credential is ever set or displayed | BR-12 |
| 4 | System | Link completes or fails | Success/failure audited; expired/reused token -> generic invalid | BR-11, BR-12 |

## Entitlement propagation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (features 32-40) | Plan change committed | Snapshot source updates; consumer caches expire within `≤ 30 s` and re-resolve | BR-09 |
| 2 | Creator | Uses a pro-only capability right after downgrade | Next request re-clamps (fail-closed to free when the snapshot is unreachable) | BR-09 |
| 3 | Creator | Upgrades | The capability unlocks without re-login once caches refresh; no data loss on lapse | BR-09 |
