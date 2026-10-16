# Subscription Lifecycle - Workflow and User Journey (feature 36)

Phase 1 BA · End-to-end time paths over the rows feature 35 defines. Rules: `docs/01-requirements-ba/36-subscription-lifecycle/02-user-stories-and-business-rules.md`.

## W-1: Purchase to first term (baseline)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Pays a pro order (33/34 flow) | Grant opens/extends the row, writes the credit window, emits `order.granted.v1` | 34's guard; BR-09 |
| 2 | System (job) | Daily run at `02:10 UTC` | Sends `plan.renewal_7d` when within 7 days of `period_end` (flag false, not cancelled) | BR-02, BR-03 |
| 3 | System (job) | Same run, within 1 day | Sends `plan.renewal_1d`; jump rule applies if the 7d window was missed | BR-03 |
| 4 | Subscriber | Clicks the reminder's renew path | Plan page opens with the term preselected; standard checkout proceeds | BR-09 |
| 5 | System | Grant lands while the current row is same plan+term | Period extends from `max(now, period_end)`; status `active`; new credit window; audit `plan.renewed` | BR-09, BR-10 |

## W-2: Lapse, grace, expiry, lossless downgrade

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (job) | `period_end` passed for an `active`/`trial` row | Status -> `grace`; audit `plan.grace`; row still serves (resolver) | BR-05, BR-08 |
| 2 | Lapsed subscriber | Opens any pro surface | Gates pass; plan page shows renew banner with `graceEndsAt`; credits do not refill | BR-07 |
| 3 | System (job) | `period_end + graceDays` passed | Status -> `expired` + downgrade audit + fresh free row opens (same transaction) | BR-05, BR-06 |
| 4 | Owning surfaces | Next read after snapshot refresh (<= 30 s) | Domains stop serving (04 gate, <= 60 s); themes/templates fall back at render; pro reads and badges flip to free | BR-15 |
| 5 | Lapsed subscriber | Opens dashboard | Content intact and readable; downgraded surfaces labeled with the upgrade path | BR-15 |
| 6 | Buyer | Re-purchases the same plan+term | Row re-activates, extension applies, all pro behavior restores | BR-09, BR-15 |

## W-3: Cancel

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Subscriber | Clicks cancel on the plan page | Step-up dialog when re-auth is older than 10 min | BR-11 |
| 2 | Subscriber | Confirms | `POST /api/v1/billing/plan/cancel`; status -> `cancelled`; reminders stop; response states period end and exact effects; zero PSP calls | BR-11 |
| 3 | Subscriber | Keeps using pro until period end | Service continues; plan page shows "ends {date}" | BR-11 |
| 4 | System (job) | `period_end` passed | Status -> `expired`; downgrade audit; free row opens | BR-12 |
| 5 | Subscriber | Changes mind before period end and re-purchases same plan+term | Renewal un-cancels: status `active`, extension from `max(now, period_end)` | BR-09 |

## W-4: Trial

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Pricing page (03) | Trial CTA shows configured `trialDays`; hidden when unset or already used | Q-03, BR-13 |
| 2 | Evaluator | Starts the trial | One row: plan `pro`, status `trial`, `source = trial`, no provider/order, period `now .. now + trialDays`, pro monthly term stamped (credits) | BR-13 |
| 3 | System (job) | Nearing `period_end` | `plan.trial_1d` nudge (and `-7d` only when the window exceeds 7 days) | BR-03, BR-14 |
| 4 | Evaluator | Converts before trial end | Same plan+term renewal: extension from trial `period_end`; status `active` | BR-09, BR-14 |
| 5 | System (job) | No conversion | `trial -> grace -> expired`; lossless downgrade; second trial impossible | BR-14, BR-13 |

## W-5: Ops period fix

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops operator | Opens the period-fix form | Server requires ops role; form demands user id, new period end, reason | BR-16 |
| 2 | Ops operator | Submits | Validates bounds; records before/after + operator in audit; row serves per the corrected bounds on next snapshot | BR-16 |

## W-6: Job rerun / overlap

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops/system | Job fires twice or a second replica starts | Advisory lock serializes; per-row status guards make the second pass a zero-write no-op | BR-01, BR-17 |
| 2 | System | A reminder enqueue was already delivered | Pipeline idempotency key suppresses duplicates; flags stay authoritative | BR-03 |
