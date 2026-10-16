# Subscription Lifecycle - Step-by-Step Implementation Plan (feature 36)

Phase 2 TL · Order matters: schema, then resolver/job, then endpoints, then cross-edits, then drills. Package `com.app.billing.lifecycle`. BA sources: `docs/01-requirements-ba/36-subscription-lifecycle/`.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__subscription_lifecycle.sql` per `02-database-schema-design.md` + drift test (constraint evolution, partial unique incl. `trial`) | 35's V2 applied | V3 drills 1-3 green |
| T-02 | BE | Resolver cutoff (`period_end + graceDays`) in the in-service resolver + register `billing.lifecycle` in `ops_config` (default 7, 1..30) + `<= 5 s` cache; 35's D-5 cross-edit lands with it | T-01 | AC-05, AC-06 |
| T-03 | BE | Lifecycle job skeleton: `02:10 UTC`, advisory lock, batching `<= 500`/class, per-row transactions, audit writer | T-01 | AC-10 (skeleton), NFR-02 |
| T-04 | BE | Reminder pass: flags + jump rule + 4 templates + enqueue via 32's pipeline + 01's email seam (+ seam lands in 01's file) | T-03 | AC-04; failure metric drill |
| T-05 | BE | Expiry pass: `active|trial -> grace`, `cancelled -> expired`, `grace -> expired`, free-row insert, `plan.downgraded` audit | T-03 | AC-01 (server half) |
| T-06 | BE | Renewal extension branch in the grant writer: `max(now, period_end) + term`, un-cancel, re-stamp, fresh credit window, `plan.renewed`; switches stay close/open | T-01; 34's grant txn | AC-02 |
| T-07 | BE | Cancel endpoint + step-up check + errors (`plan_not_active`, `cancel_conflict`) + zero-PSP-call assertion | T-02 | AC-03 |
| T-08 | BE | Trial endpoint: prerequisites (`trialDays` + active pro monthly term), once-ever check, row + window creation | T-01; 35's term rows | AC-07, AC-08 (start half) |
| T-09 | BE | `GET /internal/v1/billing/subscriptions/{userId}` + envelope rules + 03 derivation contract test (`TrialState` from bounds) | T-02 | Projection tests green |
| T-10 | BE | Ops period-fix route: role check, bounds, reason, before/after audit | T-01 | AC-11 |
| T-11 | BE | Cross-edits per `01-architecture-and-tech-stack.md` table (35, 34, 32, 03, 01, 09, 18) | T-02..T-10 | Diff review; full-tree validation green |
| T-12 | FE | Plan page lifecycle states (trial badge, grace banner with `graceEndsAt`, cancelled/expired copy) + cancel dialog incl. step-up + trial CTA states | T-07, T-08 | AC-05, AC-07 UI |
| T-13 | FE | Ops period-fix form with before/after diff | T-10 | AC-11 UI |
| T-14 | DevOps | Metrics + alerts (`lifecycle.reminder_failed` > 1%/run, job duration) + CI greps (proration, standing-charge terms) | T-04..T-10 | NFR-09, NFR-12 |
| T-15 | BE/DevOps | Drills: time-travel full cycle, lossless restore across 04/09/18, job double-run, cutoff with job stopped, email-seam outage | T-02..T-14 | AC-01..AC-12 complete |

## Sequencing notes

1. T-01 before everything: constraints gate trial inserts and the partial unique gates double-trial races.
2. T-02's cutoff is independent of the job, so it lands before T-03 and already fixes AC-06 during development.
3. T-06 touches 34's grant transaction: land it behind the extension branch's own unit-proven helper, then wire; the close/open path must stay byte-identical when the branch does not match.
4. T-12 can start against contract stubs once TL 03's DTOs freeze; `GET /api/v1/billing/plan`'s additive fields ship with T-09 so old clients never see a missing key (additive-only under the OpenAPI diff gate).
5. Rollout: no feature flag - the job's first run on existing data is a dry pass by construction (all rows predate flags, furthest `period_end` governs). Run T-15's cutoff drill in staging with the job disabled before enabling the schedule.

## Definition of done

All AC-01..AC-12 green in CI; full-tree validation (word caps + backticked path resolution) green; grep gates for proration and standing-charge terms return zero; `MIGRATION.md` row 36 flips to `done` at batch tail with legacy sources deleted.
