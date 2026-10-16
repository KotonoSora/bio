# Subscription Lifecycle - Architecture and Tech Stack (feature 36)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/36-subscription-lifecycle/`. Platform pin: `docs/02-architecture-techlead/00-platform-baseline/`. Stack: Spring Boot `4.1.1`, Kotlin `2.4.21`, PostgreSQL `18.6`, Flyway on boot.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| Lifecycle job + statuses/flags, renewal extension inside the grant writer, cancel, trial rows, reminder enqueues, period-fix ops route, resolver cutoff | Grant transaction + `TransitionGuard` -> 34; catalog/terms/credits -> 35; transactional send -> `POST /internal/v1/notifications` (32, same service); recipient email -> `GET /internal/v1/users/{userId}/email` (01, new seam); downgrade enforcement -> 04/09/18/03 read paths (no writes out) |

No new service, no new Redis keys, no new tables: V3 is columns + constraint/index evolution on `subscriptions`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Job runs in-process in `com.app.billing.lifecycle` (`@Scheduled(cron = "0 10 2 * * *", zone = UTC)`, `pg_try_advisory_lock` guard, per-row transactions, batches <= 500) | Legacy's "monolith lifecycle with in-process ledger writes" maps to the single billing-service owner of `subscriptions`; a scheduler split would add a hop without a second writer; 39 extends the same job with three passes (campaign windows, credit application, coupon expiry) under the same lock |
| D-2 | `V3__subscription_lifecycle.sql` (chain per `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`): reminder flags, `source`/`source_ref`, status enum + trial, partial-unique/period-index evolution, `chk_sub_paid` rebuilt for trial provenance | One migration; no new tables or grants |
| D-3 | Resolver cutoff: `active|grace|trial` -> plan while `now < period_end + graceDays`; `cancelled` -> until `period_end`; else free. `graceDays` in `ops_config` `billing.lifecycle` (default 7, valid 1..30, cached <= 5 s) | The resolver, not the job, is the truth: downtime can neither serve a lapsed plan beyond grace nor lock out early |
| D-4 | **DEVIATION FLAGGED**: downgrade effects are enforced read-time at each owning surface (04's precedent), not by legacy's service-port flag flips. Domains: 04's host-resolve gate (exists). Themes/templates: render fallback lines added to 09/18. Profiles: vacuous in v1 (06 ships one profile; guard already capability-driven; recorded, not built). No `plan.changed`-style event exists | Same observable outcome, stronger guarantee: nothing is written to reverse, so restore after re-upgrade is byte-identical by construction |
| D-5 | Trials: status `trial`, `source = 'trial'`, provider/order null; start requires `trialDays` set + an active pro monthly term row (stamped for credits); one per account ever (checked over all history); conversion = same plan+term renewal (extension from trial `period_end`); nudges reuse the reminder flags with `plan.trial_*` templates | Full-pro evaluation flows through the same ledger, gates, and audit as paid time; no instrument exists on one-shot rails |
| D-6 | Renewal extension lives in the grant writer: a same plan+term grant on a current `active|grace|cancelled|trial` row updates that row (`period_end = max(now, period_end) + term`, status `active`, re-stamped term, fresh credit window, audit `plan.renewed`) instead of close/open. Switches keep the close/open path. No proration code exists (grep gate) | Legacy AC-02's single-current-row extension; early renewal never double-charges days; un-cancel needs no separate route |
| D-7 | Cancel: `POST /api/v1/billing/plan/cancel`, session + CSRF + step-up <= 10 min; paid `active` rows only; status `cancelled`; zero PSP calls on every rail; expiry at `period_end` without grace | Deliberate stops are honest; grace is a lapse safety net, not a cancel perk |
| D-8 | Reminders: daily pass sets flags + enqueues via 32's `SendRequest` (idempotency key `sub:{subscriptionId}:{7d|1d}`, class `transactional`, templates `plan.renewal_*`/`plan.trial_*`); jump rule collapses missed windows; recipient email from 01's new internal read | Flags + pipeline keys give exactly-once under rerun without a new subscription-state column |
| D-9 | Ops period fix: `POST /api/v1/ops/billing/subscriptions/{userId}/period-fix` (`billing_ops_required`, reason mandatory, bounds validated, before/after audited) | Support never edits rows by hand; every change is explainable |
| D-10 | Native recurring (Stripe et al.) stays deferred per 32's Q-05; absence is asserted by CI grep (no standing-charge, auto-renew, dunning, proration columns or terms) | Defers the whole PSP-subscription surface without forking buyer experience; renewals already identical across rails |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/01-requirements-ba/35-entitlement-and-plans/02-user-stories-and-business-rules.md` | BR-01 + `trial` in the current-row set; BR-04 renewal extension; BR-05 resolver cutoff + `trial` |
| `docs/01-requirements-ba/35-entitlement-and-plans/04-ui-data-dictionary.md` | Trial badge; grace tooltip uses `graceEndsAt` |
| `docs/02-architecture-techlead/35-entitlement-and-plans/01-architecture-and-tech-stack.md` | D-4 extension branch; D-5 resolver cutoff + `trial` |
| `docs/02-architecture-techlead/35-entitlement-and-plans/02-database-schema-design.md` | Statuses bullet gains `trial` |
| `docs/02-architecture-techlead/35-entitlement-and-plans/03-api-contracts-and-payloads.md` | `BillingPlanView` + `graceEndsAt`, `trialUsed` (additive) |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/01-architecture-and-tech-stack.md` + `02-database-schema-design.md` | Grant path: renewal-extension branch note |
| `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md` + `03-api-contracts-and-payloads.md` | V3 row text; registry rows (cancel, trial, period-fix, subscriptions read) |
| `docs/02-architecture-techlead/03-tier-presentation/03-api-contracts-and-payloads.md` | Consumed path aligned to `/internal/v1/billing/subscriptions/{userId}`; status set + `trial` |
| `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md` | New internal email read row |
| `docs/02-architecture-techlead/09-themes-and-avatar/03-api-contracts-and-payloads.md` + `18-resume-templates/03-api-contracts-and-payloads.md` | Render-time downgrade fallback lines |
