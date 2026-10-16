# Subscription Lifecycle - UI Data Dictionary (feature 36)

Phase 1 BA · Surfaces: plan page lifecycle states and cancel dialog (extends feature 35's plan page rows), trial CTA on the pricing page (feature 03), ops period-fix form. All values are server-computed; nothing below is client-settable.

## Plan page: lifecycle states

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `status` | Status badge | Yes | Server enum: `trial`, `active`, `grace`, `cancelled`, `expired`; derives copy per row below | Rendered from `GET /api/v1/billing/plan` |
| `trialEndsAt` | Badge copy suffix | When `status = trial` | Server instant; shows as "Trial — ends {date}" | Absent otherwise |
| `graceEndsAt` | Banner copy | When `status = grace` | Server-computed `period_end + graceDays` (config) | Renew banner: "Your plan lapsed — renew by {graceEndsAt}" with CTA |
| `periodEnd` | Text row | Yes | Server instant, UTC rendered local | "Renews {date} / ends {date}" per status |
| `cancelledAt` | Text row | When `status = cancelled` | Server instant | "Ends {periodEnd} — you won't be charged again" |
| `renewCta` | Primary button | When `status` in `grace`, `cancelled`, `expired`, and near-renewal | Deep-links to the plan page checkout with the current plan+term preselected | Hidden while `active` far from `period_end` |
| `downgradeEffects` | Disclosure list | When `status = grace`, `expired` | Static copy from the effect matrix: domains stop serving, premium styling falls back, credits stop; "nothing is deleted" | Expanded in the expired state |
| `upgradePath` | Secondary link | When `status = expired` | Points to checkout | Restores all pro behavior |

## Cancel dialog

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `effectsSummary` | Static list | Yes | Server-provided period end; exact consequences (service to end, reminders stop, no refund, lossless downgrade after) | Shown before confirmation |
| `stepUp` | Inline auth dialog | When re-auth older than 10 min | Feature 01's dialog; `step_up_required` opens it | Cancel button disabled until satisfied |
| `cancelConfirm` | Destructive button | Yes | Single action; second attempt returns `cancel_conflict` | On success: dialog closes, badge shows `cancelled` |
| `keepPlan` | Secondary button | Yes | Closes dialog, no call | Default focus |

## Trial CTA (pricing page, feature 03)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `trialDays` | CTA copy | When config set | Server from `billing.catalog`; hidden when unset (03's absence rule) | "Try pro free for {trialDays} days" |
| `trialUsed` | Disabled state | Yes | Server boolean from the plan read | When true: CTA disabled with "Trial already used — upgrade" |
| `startTrial` | Primary button | Yes | Login required; `trial_unavailable`/`trial_already_used` render inline copy | Calls `POST /api/v1/billing/trial/start` |

## Ops period-fix form

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `userId` | Read-only text | Yes | Prefilled from the support trace | - |
| `newPeriodEnd` | Date-time input | Yes | Must be > `period_start`; server re-validates (`validation_failed`) | Current `period_end` prefilled |
| `reason` | Textarea | Yes | Non-empty, <= 500 chars; stored in audit | - |
| `beforeAfter` | Diff block | Yes | Read-only rendering of current and proposed values | Confirmation required before submit |
| `fixSubmit` | Primary button | Yes | Ops role enforced server-side; `billing_ops_required` rendered as disabled state | Audit entry `plan.period_fix` |

Notes: no client payload can set statuses, flags, periods, or trial state anywhere (server-side only). All copy states effects truthfully and never promises proration.
