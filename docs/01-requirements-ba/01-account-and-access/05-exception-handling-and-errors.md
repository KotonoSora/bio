# Account and Access - Exception Handling and Errors (feature 01)

Phase 1 BA · Failure paths handled by `docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`. Codes are stable contract (G4); problem bodies follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Password endpoint probed | API | `404` with code `auth_disabled`; no working fallback exists; probe counted | "Page not found" (no password UI exists anywhere) |
| `state` missing/expired/reused, or PKCE mismatch | Callback | Fail closed; no session issued; replay counter incremented; generic redirect | "Sign-in didn't complete. Try again." |
| Provider error / code exchange failure | Callback | Generic `auth_failed`; provider detail never surfaced (no oracle) | "Sign-in didn't complete. Try again." |
| Off-allowlist `callbackURL` | Request | Rejected server-side at initiation and callback; no redirect echo | "Sign-in didn't complete. Try again." |
| IdP denies email (Facebook) or relay address | Login | Account created; success path; completion prompt | None (success); prompt: "Add a contact email for receipts." |
| IdP email collides with another account | Login | `sub`-matched login succeeds; no merge, no block; conflict flagged | Banner: "This email is already connected to another account. Set a contact email instead." |
| Contact email already verified elsewhere | Form submit | `409 email_conflict`; nothing persisted | Field error: "This email can't be used here. Choose another." |
| Wrong or expired verification code | Form submit | `422 email_code_invalid`; attempt counter advances; 5 misses -> resend only | "That code didn't work. Check it or request a new one." |
| Unlink the last provider | Settings | `409 last_link_forbidden`; link kept | "You need at least one sign-in method. Link another first." |
| Sensitive action without fresh re-auth | API | `403 step_up_required` with re-auth path; action not executed, not partially applied | Dialog: "Confirm it's you to continue." |
| Session revoked mid-use (another tab) | Any request | `401 unauthenticated`; no degraded owner path | Login screen with generic notice |
| Revoke an unknown/already-revoked session | Settings | `404 session_not_found`; list refetch | "That session no longer exists." + refreshed list |
| Auth burst beyond throttle | API | `429 throttled` + `Retry-After`; counted, not punished | "Too many attempts. Try again in {seconds}." |
| Edge session cache unavailable | All session-validated routes | Introspect fallback via account-service (bounded); if both stores unreachable, fail closed `503` for owner routes | "Temporarily unavailable. Try again." |
| IdP outage | Login | Generic failure; bounded retry guidance; no retry storm from the platform | "Sign-in didn't complete. Try again." |
| Recovery token expired/reused/unknown | Landing | Generic invalid state; no existence detail; failure audited | "This link expired or was already used. Contact support." |
| Entitlement snapshot unreachable | Pro-only capability | Consumer fails closed to free; `entitlement_snapshot_miss` counter; request never silently widens | The feature's own upsell/denial message (e.g., "Pro feature") |
| Provider secret rotation | Ops | Dual-config reload; new logins use the new secret; no deploy, no downtime | None (seamless) |
| First-login handoff lookup fails (profile-service) | Post-login redirect | Default to `/app`; dashboard self-recovers the chooser state; failure counted | None visible; worst case one extra navigation |

Non-errors kept out of this table by design: email collision never blocks login, relay logins never block, session-list staleness labels are coarse by construction (feature 01's rules, not failures).
