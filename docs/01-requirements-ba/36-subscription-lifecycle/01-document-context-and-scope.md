# Subscription Lifecycle - Document Context and Scope (feature 36)

Phase 1 BA · Feature 36 of 53 — monetize 5/9: what happens after the grant — periods, renewal, reminders, grace, expiry, cancel, trials, and the lossless downgrade. Sources: `monetize/billing/subscription-lifecycle{,-backend-plan,-database-plan}.md`; trials slice of `monetize/subscription/trials-promotions.md` (coupons -> feature 39, referral/goodwill credits -> gated affiliate major); `monetize/subscription/recurring-billing.md` recorded as deferred. Implemented by `docs/02-architecture-techlead/36-subscription-lifecycle/`.

## Context

Feature 35 made plan state one current subscription row plus a versioned catalog; this feature moves those rows through time. Seven of the eight rails are one-shot, so renewal is an explicit re-purchase with reminder UX and no card-on-file; Stripe-native recurring is deferred (32's Q-05), so one lifecycle absorbs everything a buyer can experience. The expiry job materializes statuses, but the resolver stays time-aware so a lagging job never serves a lapsed plan beyond grace and never locks out early.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Time moves rows: one daily job, idempotent per row, audited, bounded scans; reruns write nothing |
| OBJ-02 | Reminders exactly-once at `period_end - 7d/-1d`, transactional only, deep-linking to checkout |
| OBJ-03 | Grace: gates pass, renew banner shows `graceEndsAt`, AI credits do not refill, window bounded by config |
| OBJ-04 | Expiry downgrades losslessly: every effect is read-time and reversible; the restore drill proves byte-identical behavior |
| OBJ-05 | Cancel: one click with step-up, service-to-period-end, zero PSP coupling, no refund coupling (38) |
| OBJ-06 | Trials: bounded full-pro evaluation, once ever, no payment instrument, conversion nudges, lossless end |
| OBJ-07 | Renewal honesty: early renewal extends from `max(now, period_end)`; no proration exists anywhere in v1 |
| OBJ-08 | Ops period fixes are role-gated, reasoned, and audited with before/after values |

## Scope

In scope (SC-01..SC-09): expiry/grace progression + free-row opening; reminder state and email enqueue; renewal extension math in the grant path; cancel; trial start; lossless-downgrade effect matrix with its enforcement points; resolver grace-window cutoff; ops period fix; `reminded_*`/`source` schema evolution (V3).

Out of scope: receipts and history UX (37); refunds (38); coupons and promo codes (39); rail webhooks/PSP verification (40); native recurring charges and dunning (deferred, asserted by absence); proration math (never in v1); multi-seat/org periods (agency major 45-47); deletion purge (02 owns the roster).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Pro subscriber | R: own lifecycle state; U: cancel | Sees period end, renews, cancels cleanly |
| Trial evaluator | C: own trial (once ever); R: own state | Full gates during trial; converts or lapses |
| Lapsed subscriber | R: own state; C: renewal via checkout | Reminders, grace banner, predictable downgrade |
| Support/ops | U: period fix (reason mandatory, audited) | Answers "will I lose my pages" with a documented no |
| Lifecycle job (system) | U: statuses/flags; C: free rows; C: reminder enqueues | Idempotent, advisory-locked, audited |
| Owning features (system) | R: snapshot only | Enforce downgrade effects at their own read paths |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Grace length (lifecycle Q1) | `ops_config` `billing.lifecycle.graceDays`, default 7, validated 1..30; reads cached `<= 5 s` |
| Q-02 | Mid-term cancel refunds (lifecycle Q2) | None at cancel; provider-allowed goodwill refunds run via 38's flow only |
| Q-03 | Trial length and scope (trials Q1) | `trialDays` from `billing.catalog` (03: launch 7, full-pro); unset -> trial unavailable; one trial per account ever, signal metrics logged |
| Q-04 | Native recurring (recurring Q1/Q2) | Deferred in v1 (32 Q-05): renewal is re-purchase; no provider-subscription/standing-charge columns exist (asserted by absence) |
| Q-05 | Downgrade mechanics | Read-time at every owning surface (04's precedent) instead of write-back flag flips; restoration is automatic and byte-identical |
| Q-06 | Cancel vs grace | Cancel applies to paid `active` rows only; cancelled rows skip grace and expire at `period_end` |
| Q-07 | Reminder reliability | Row flags + pipeline idempotency keys; a missed `-7d` window sends only the `-1d` nudge (no double-spam) |
| Q-08 | Early renewal | Same plan+term extends the current row from `max(now, period_end)`; switches start fresh at grant; no proration code exists |
| Q-09 | Trial prerequisites | Trial offers only when `trialDays` is set and a pro monthly term is active (the trial stamps it for credits) |
| Q-10 | Excess-profile effect | Vacuous in v1: 06 ships one profile per user (BR-03); the recorded effect activates with multi-profile, whose guard already reads `profiles.limit` |

## Constraints carried

Money state transitions server-side only (34's guard); RLS unchanged; config values never in code; audit spine 24 mo; no client-settable lifecycle flags anywhere; step-up consumed from feature 01; transactional email only via 32's pipeline.
