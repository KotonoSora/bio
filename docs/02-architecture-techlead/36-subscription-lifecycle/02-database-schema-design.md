# Subscription Lifecycle - Database Schema Design (feature 36)

Phase 2 TL · `billing_db` migration `V3__subscription_lifecycle.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/36-subscription-lifecycle/`. No new tables; V2's text stays as history and V3 evolves it explicitly.

## Flyway V3 (`V3__subscription_lifecycle.sql`)

```sql
ALTER TABLE subscriptions
  ADD COLUMN reminded_7d BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN reminded_1d BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN source TEXT NOT NULL DEFAULT 'paid',
  ADD COLUMN source_ref TEXT;

ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_status_check; -- PG's default name for V2's inline status CHECK
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_status
  CHECK (status IN ('active','grace','cancelled','superseded','expired','trial'));
ALTER TABLE subscriptions DROP CONSTRAINT chk_sub_paid;
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_paid CHECK (plan <> 'pro' OR (
  period_end IS NOT NULL AND (
    (source = 'trial' AND provider IS NULL AND order_code IS NULL) OR
    (source <> 'trial' AND provider IS NOT NULL AND order_code IS NOT NULL))));
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_trial
  CHECK (status <> 'trial' OR (plan = 'pro' AND source = 'trial'));
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_source
  CHECK (source IN ('paid','trial','coupon','credit')); -- coupon|credit written only by 39+ flows

DROP INDEX uniq_sub_current;
CREATE UNIQUE INDEX uniq_sub_current ON subscriptions (user_id) WHERE status IN ('active','grace','trial');
DROP INDEX idx_subs_period;
CREATE INDEX idx_subs_period ON subscriptions (period_end) WHERE status IN ('active','grace','cancelled','trial');
CREATE INDEX idx_subs_reminders ON subscriptions (period_end) WHERE status IN ('active','trial');
-- no new tables, grants, or RLS policy changes; append-only event tables unchanged
```

## Semantics

- Statuses after V3: `active|grace|trial` current (one per user, partial unique); `cancelled` serves to `period_end`; `superseded`/`expired` are history. `closed_at` set when currentness ends (cancelled rows set it at expiry, not at cancel).
- Lifecycle job transitions (per-row transaction, status-guarded): `active|trial` past `period_end` -> `grace`; `cancelled` past `period_end` -> `expired`; `grace` past `period_end + graceDays` -> `expired` + insert free row (`ON CONFLICT DO NOTHING` on the partial unique). Rerun = zero writes. 39 extends the pass set (TL 01 D-1): campaign windows (`scheduled` -> `live` at `starts_at`; `live`/`paused` -> `ended` past `ends_at`; audits `campaign.started`/`campaign.ended`), credit-day application (`active`/`grace` rows: `period_end += unconsumed days`, ledger rows consumed once with subscription id, audit `plan.credits_applied`), coupon expiry sweeps (`active` -> `expired`, one summary audit per run).
- Renewal extension (grant writer branch, TL 01 D-6): `UPDATE subscriptions SET period_end = greatest(now(), period_end) + term, status = 'active' WHERE id = ? AND status IN ('active','grace','cancelled','trial')` + re-stamp `plan_term_version` + audit `plan.renewed` (detail carries the renewing `orderCode`; 38's revocation matcher reads it). Switches keep close/open (`superseded` + insert).
- Source vocabulary: `paid` (all provider grants; provider + order required), `trial` (provider/order null; status `trial` while current), `coupon` (39's grant hook stamps it + `source_ref = campaign_id`), `credit` reserved (credits extend periods via the job, never create rows).
- Reminder flags: set inside the job pass (enqueue first with the pipeline idempotency key, then flag; either-order safe). Flags never client-writable; no grant widens.
- Resolver cutoff (TL 01 D-3) needs no column: `period_end + graceDays` is computed per read from cached config.
- Editors are append-only on timeline: no UPDATE grant change; lifecycle writes run under the existing `sub_ops`-style service context; owner policies stay SELECT-only for `subscriptions`.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V3 runs after V2 clean; four columns visible; old rows default `reminded_* = false`, `source = 'paid'` |
| Constraint evolution | Trial insert without `source='trial'` fails; paid insert with null provider fails; trial insert with provider fails; `status='trial'` with plan `free` fails; unknown source fails |
| Partial unique across statuses | Parallel trial + paid grant -> exactly one current row, loser `superseded`; trial retry after history does not violate (old trial is `expired`) |
| Extension math | Same plan+term grant on an active row moves `period_end` to `max(now, old_end) + term`; on a cancelled row un-cancels; on grace row starts from `now`; no second row inserted |
| Job rerun | Second run of the same day: zero UPDATEs, zero audits, zero inserts (log + row versions unchanged) |
| Resolver cutoff matrix | active/grace/trial -> plan while `now < period_end + graceDays`; cancelled until `period_end`; past-cutoff rows resolve free with the job stopped |
| Index usage | `EXPLAIN` on reminder + expiry scans picks `idx_subs_reminders` / `idx_subs_period`; no sequential scan at 10^6 rows |
| Absence gates | CI grep: zero standing-charge/auto-renew/dunning/proration identifiers anywhere under `docs/` |
| Single DDL owner | `grep -rn "reminded_7d" docs/02-architecture-techlead` matches only this file (rest of the tree references columns by prose) |
