# Subscription Lifecycle - User Stories and Business Rules (feature 36)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/36-subscription-lifecycle/`. Scope: `docs/01-requirements-ba/36-subscription-lifecycle/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Pro subscriber | renew before lapse and see exactly what my renewal buys | service never lapses by accident | High |
| US-02 | Lapsed subscriber | a grace window and a predictable, reversible downgrade | I never lose content to a missed payment | High |
| US-03 | Trial evaluator | try full pro once, without a payment instrument | I can decide before paying | High |
| US-04 | Expiring buyer | reminders at 7 days and 1 day with a straight path to checkout | renewal is one click, never a surprise | High |
| US-05 | Support/ops | correct a wrong period with reason and before/after audit | money-adjacent truth stays explainable | Medium |
| US-06 | Reviewer | prove every downgrade effect reverses byte-identical | "will I lose X?" has a documented no | High |
| US-07 | Cancelling subscriber | cancel in one action and keep service to my period end | endings are as clean as purchases | Medium |

## Business rules

- **BR-01 Job safety.** One daily job (`02:10 UTC`) runs under a PostgreSQL advisory lock, processes each class in bounded batches (<= 500 rows per class per run), and commits per row. Every transition is status-guarded: a rerun or a double-processed row performs zero writes and records zero duplicate audits.
- **BR-02 Reminder schedule.** Active and trial rows not yet flagged get: `plan.renewal_7d`/`plan.trial_7d` when `now >= period_end - 7d`; `plan.renewal_1d`/`plan.trial_1d` when `now >= period_end - 1d`. In-app surface = the plan page (period end + renew CTA); email = 32's transactional pipeline with idempotency key `sub:{subscriptionId}:{7d|1d}`.
- **BR-03 Reminder exactly-once.** Flags `reminded_7d`/`reminded_1d` are internal only and never client-settable. Jump rule: if the `-7d` threshold slipped (job down), send only the `-1d` template and set both flags. A `-7d` trial nudge fires only when the trial window exceeds 7 days.
- **BR-04 Email resolution.** The recipient address comes from feature 01's `GET /internal/v1/users/{userId}/email`; a resolution or pipeline failure skips the send, leaves the flag unset, increments `lifecycle.reminder_failed`, and never fails the run. Sends are `transactional` class only.
- **BR-05 Expiry progression.** `active|trial` past `period_end` -> `grace`; `cancelled` past `period_end` -> `expired`; `grace` past `period_end + graceDays` -> `expired`. Terminal transitions open a fresh free row in the same transaction and are audited (`plan.grace`, `plan.expired`, `plan.downgraded`).
- **BR-06 Free-row opening.** Expiry inserts the free-floor row idempotently (partial unique protects); a current row always exists after any transition.
- **BR-07 Grace semantics.** During grace the resolver still returns the plan (full gates); the plan page shows the renew banner with `graceEndsAt = period_end + graceDays`; AI credits do not refill (no new window is created); grace length comes from `billing.lifecycle.graceDays` (default 7, valid 1..30).
- **BR-08 Resolver cutoff.** `active|grace|trial` resolve pro while `now < period_end + graceDays`; `cancelled` while `now < period_end`; otherwise free. The resolver is time-aware and job-independent: job downtime never serves a lapsed plan beyond grace and never locks out early.
- **BR-09 Renewal is re-purchase.** A same plan+term order on a current `active|grace|cancelled|trial` row of that plan is a renewal: the row's `period_end` extends to `max(now, period_end) + term`, status returns to `active`, the purchased term row is stamped, a fresh credit window opens, and all audit is `plan.renewed`. Cancelled rows un-cancel by renewing; no separate resume route exists.
- **BR-10 Switch starts fresh.** Any plan or term switch (including monthly <-> yearly) supersedes the current row at grant and starts the new period at grant time. No proration sum exists anywhere in v1 (CI grep gate).
- **BR-11 Cancel.** Allowed on paid `active` rows only (`plan_not_active` otherwise); requires step-up `<= 10 min` (`step_up_required`); sets status `cancelled`; stops reminders; service runs to `period_end`; makes zero PSP calls; refunds nothing (38 runs provider-allowed refunds); a second cancel returns `cancel_conflict`.
- **BR-12 Cancelled rows skip grace** and expire at `period_end` through the shared downgrade path.
- **BR-13 Trial start.** `POST /api/v1/billing/trial/start` requires `trialDays` set and an active pro monthly term (else `trial_unavailable`) and no prior trial row ever (`trial_already_used`). It creates one row: plan `pro`, status `trial`, `source = trial`, provider and order null, period `now .. now + trialDays`, stamped with the pro monthly term (credits = that term's allowance). Full gates apply; no instrument exists on any rail.
- **BR-14 Trial end.** Conversion nudges reuse the reminder machinery (`plan.trial_7d`/`plan.trial_1d`); unconverted trials follow `trial -> grace -> expired + downgrade` losslessly; converting early extends from the trial's `period_end`.
- **BR-15 Downgrade effects are read-time and lossless.** Nothing is deleted, rewritten, or flagged at downgrade; each owning surface recomputes from the 30-second snapshot: custom domains stop serving per feature 04's host-resolve gate (`<= 60 s`, DNS untouched); themes and resume templates fall back at render (stored ids preserved); pro-gated reads and badges flip per features 03/09/13/18; excess profiles stay recorded but vacuous in v1 (Q-10).
- **BR-16 Ops period fix.** `POST /api/v1/ops/billing/subscriptions/{userId}/period-fix` requires an ops role, a mandatory reason, and valid bounds (`new period end > period_start`); records before/after values and the operator in audit.
- **BR-17 Idempotency.** Rerun job = zero writes; double trial fire = one row; second cancel = 409; repeated period fixes are individually audited (no silent coalescing).
- **BR-18 Asserted by absence.** No standing-charge, provider-subscription, dunning, proration, or auto-retry columns/tables exist (CI grep gate); deferred native recurring cannot leak into v1 (Q-04).
- **BR-19 Degrade.** Job down -> resolver cutoff keeps truth; account-service (email seam) down -> reminder skipped + metric; pipeline down -> backlog metric; money reads unaffected.
- **BR-20 Scoping.** Owner endpoints read only the session's rows (RLS + server-set `user_id`); the ops route is role-gated and audited; no client payload can set status, flags, periods, or trial state.
