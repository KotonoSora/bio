# Subscription Lifecycle - API Contracts and Payloads (feature 36)

Phase 2 TL · Browser + internal + ops contracts. Auth and rate rules: `docs/02-architecture-techlead/36-subscription-lifecycle/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. Registry rows land in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/billing/plan/cancel` | session + CSRF + step-up `<= 10 min` | - | 200 `PlanCancelView` | 403 `step_up_required`; 409 `plan_not_active`, `cancel_conflict`; 429 |
| `POST /api/v1/billing/trial/start` | session + CSRF | - | 201 `TrialView` | 409 `trial_unavailable`, `trial_already_used`; 429 |
| `GET /api/v1/billing/plan` (35) | session | - | 200 `BillingPlanView` + additive `graceEndsAt`, `trialUsed` | 401 |
| `GET /internal/v1/billing/subscriptions/{userId}` | service JWT | - | 200 `SubscriptionEnvelope` | 401 |
| `POST /api/v1/ops/billing/subscriptions/{userId}/period-fix` | ops role + CSRF | `PeriodFixRequest` | 200 `PeriodFixView` | 403 `billing_ops_required`; 404 `subscription_absent`; 422 `validation_failed`; 429 |
| consumes `POST /internal/v1/notifications` | service JWT | `SendRequest` (32) | 202 | 422 `template_unknown`, `recipient_invalid` |
| consumes `GET /internal/v1/users/{userId}/email` | service JWT | - | 200 `EmailView` | 401; 404 `user_absent` |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class PlanCancelView(val status: String, val periodEnd: Instant, val downgradeAt: Instant)
data class TrialView(val plan: String, val status: String, val periodStart: Instant,
  val periodEnd: Instant, val aiCredits: Int)
data class SubscriptionLifecycleView(val plan: String, val status: String, val term: String,
  val periodStart: Instant, val periodEnd: Instant, val graceEndsAt: Instant?, val nextAmountVnd: Long)
data class SubscriptionEnvelope(val subscription: SubscriptionLifecycleView?) // null = no pro row
data class PeriodFixRequest(val newPeriodEnd: Instant, val reason: String)
data class PeriodFixView(val userId: String, val subscriptionId: String, val before: Map<String, String>, val after: Map<String, String>)
data class EmailView(val email: String, val emailVerified: Boolean) // 01's seam, read-only consumption
```

## Rules

- Resolver: `active|grace|trial` -> plan while `now < period_end + graceDays`; `cancelled` until `period_end`; else free. `graceDays` from `billing.lifecycle` (default 7, valid 1..30, cached `<= 5 s`). The job never gates reads.
- Cancel: paid `active` rows only; sets `status = cancelled`; reminders stop; expiry at `period_end` without grace; zero PSP calls (40's conformance drill asserts the rail audit shows nothing outbound); second cancel `409 cancel_conflict`; cancel on `grace|trial|free|expired` -> `409 plan_not_active`.
- Renewal: same plan+term grant on a current row extends it via TL 01 D-6 and un-cancels; any switch close/opens from grant time; no proration code exists (CI grep gate). `renewUrl` is not a field: the plan page owns the renew path.
- Trial: requires `trialDays` set in `billing.catalog` and an active pro monthly term (stamped; `aiCredits` = that term's allowance); one per account over all history; creates the `trial` row (no provider/order); conversion = ordinary plan checkout extending from the trial's `period_end`.
- Projection: `subscription` present only when a pro row exists (`active|grace|cancelled|trial`); free history -> null. `nextAmountVnd` = active term amount for (pro, row's term), fallback = the row's stamped amount. `graceEndsAt` present only in `grace`. Feature 03 derives its `TrialState` from `status = trial` (`days = DAYS.between(periodStart, periodEnd)`, `endsAt = periodEnd`).
- Reminders (job pass): templates `plan.renewal_7d`/`plan.renewal_1d` for paid, `plan.trial_7d`/`plan.trial_1d` for trials; thresholds `period_end - 7d` / `- 1d`; jump rule collapses a missed 7d window; idempotency key `sub:{subscriptionId}:{7d|1d}`; recipient from 01's email read; send class `transactional`; failure skips + metric, never blocks state.
- Period fix: bounds `newPeriodEnd > period_start`; reason mandatory; before/after + operator audited (`plan.period_fix`); `subscription_absent` when the user has no subscription history at all.
- Money truth stays in `billing_db`: no owning surface writes lifecycle state; effects are read-time (TL 01 D-4).

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `plan_not_active` | 409 | "There's no active plan to cancel." |
| `trial_unavailable` | 409 | Trial CTA inline note; hidden when config unset |
| `trial_already_used` | 409 | CTA disabled state persists |
| `subscription_absent` | 404 | Ops form shows "no subscription history for this user" |
| `user_absent` | 404 | 01's seam; reminder skipped + metric (never surfaced) |

Reused: `step_up_required` (01), `cancel_conflict` (34), `billing_ops_required` (35), `validation_failed`, `throttled`.

```json
{ "type": "https://api.example.com/problems/trial_already_used", "title": "Trial already used",
  "status": 409, "code": "trial_already_used", "detail": "You've already used your trial." }
```
