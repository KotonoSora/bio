# Subscription Lifecycle - Exception Handling and Errors (feature 36)

Phase 1 BA · Failure and edge behavior for the lifecycle paths. Rules: `docs/01-requirements-ba/36-subscription-lifecycle/02-user-stories-and-business-rules.md`. Codes follow RFC 9457; exact JSON in `docs/02-architecture-techlead/36-subscription-lifecycle/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Cancel with no current paid `active` row (free, grace, expired) | Error | 409 `plan_not_active`; no state change | "There's no active plan to cancel." |
| Cancel during a trial | Error | 409 `plan_not_active`; trials lapse naturally | "Trials can't be cancelled — yours ends {date}." |
| Cancel a row already `cancelled` | Error | 409 `cancel_conflict`; idempotent read returned | "This plan is already cancelled." |
| Cancel with stale step-up | Error | 403 `step_up_required`; step-up dialog opens | "Confirm it's you to continue." |
| Trial start with `trialDays` unset or a current pro row is active | Error | 409 `trial_unavailable` | "A trial isn't available right now." |
| Trial start after any prior trial row | Error | 409 `trial_already_used` | "You've already used your trial." |
| Trial start: no active pro monthly term exists | Error | 409 `trial_unavailable`; recorded metric | "A trial isn't available right now." |
| Trial start fires twice concurrently | Edge | Partial unique on current rows + once-ever check; exactly one row wins | - (second request sees `trial_already_used`) |
| Renewal arrives for a retired/absent term row | Error | Checkout refuses at 33's create (`term_invalid`); grant never runs | "That price is no longer valid — pick a current term." |
| Renewal grant lands while the row is `cancelled` | Happy edge | Un-cancel: status `active`, extension from `max(now, period_end)`, audit `plan.renewed` | - |
| Job down past `period_end` | Degrade | Resolver cutoff serves free after `period_end + graceDays` regardless of row status; no lockout before | - (banner states renew path) |
| Job down, `-7d` window missed, run lands inside `-1d` | Edge | Jump rule: only the `-1d` template sends; both flags set | One reminder email, never two |
| Email resolution (feature 01 seam) fails or returns absent | Degrade | Send skipped, flag stays false, `lifecycle.reminder_failed` increments; money state unaffected | - (no user-facing error) |
| Notification pipeline rejects (4xx/5xx) | Degrade | Retry only next run via flag; pipeline backlog metric alerts | - |
| `graceDays` config invalid (0, negative, > 30) | Error | Flip refused at validation (`flip_invalid`); last valid value stays | Ops sees flip error |
| Period fix with `newPeriodEnd <= period_start` | Error | 422 `validation_failed`; no write | "The new period end must be after the period start." |
| Period fix for a user with no subscription history | Error | 404 `subscription_absent` | "No subscription history for this user." |
| Two period fixes in a row | Edge | Each validated and audited individually; no coalescing | - |
| Concurrent cancel and renewal on the same row | Edge | Row lock serializes; survivor state consistent (either `cancelled` serving to end, or `active` extended); audit shows order | - |
| Expiry job crashes mid-batch | Degrade | Per-row transactions: committed rows stay, uncommitted retry next run; zero duplicate audits | - |
| Downgrade executes while an owning surface is down | Degrade | Surface enforces on its next successful snapshot read (`<= 30 s` fresh); no lockout, no write-back needed | - (labeled surfaces on return) |
