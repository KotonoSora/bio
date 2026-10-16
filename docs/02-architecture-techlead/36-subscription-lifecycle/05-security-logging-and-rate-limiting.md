# Subscription Lifecycle - Security, Logging, and Rate Limiting (feature 36)

Phase 2 TL · BA sources: `docs/01-requirements-ba/36-subscription-lifecycle/` (NFR-09..NFR-18, BR-11..BR-18). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: session + RLS on every read/write; `user_id` server-set only; cross-user reads 403 + RLS deny; the internal projection resolves identity from the path + service JWT only | AC-12; RLS probes |
| S-2 | Cancel integrity: step-up `<= 10 min` re-checked per request; stale -> 403 with no partial state; second attempt 409; zero PSP calls (lifecycle never contacts rails) | AC-03 drill log |
| S-3 | Trial abuse: one per account over all history (DB-checked, race-safe via the partial unique); velocity/abuse signals logged as a metric; no client-settable trial state anywhere; Turnstile not required (authenticated, once-ever, bounded 7-day value) but abuse metric alerts on multi-account patterns | AC-07; metric review |
| S-4 | Reminder PII: recipient resolved server-side via 01's read; emails masked in logs (`a***@domain`); payloads carry no card data (none exists) and no provider secrets; every send is class `transactional` - the pipeline's `marketing_not_accepted` boundary is untouched | Log audit; pipeline contract tests |
| S-5 | Ops period fix: ops role re-verified per request; mandatory reason; before/after + operator in the 24-month audit; rate-limited; no bulk endpoint exists | AC-11; role-spoof probe |
| S-6 | Job safety: advisory lock, bounded batches, per-row transactions, status-guarded updates; a failing row is skipped + metered and retried next run; the job holds no user-facing request path | AC-10; crash-injection drill |
| S-7 | Config integrity: `graceDays` validated 1..30 at flip, last valid value stays on refusal; config flips audited by 32's controls; no lifecycle value is ever hardcoded as a fallback beyond the documented default 7 | Flip refusal tests; grep |
| S-8 | Data honesty gates: CI grep for proration and standing-charge/auto-renew/dunning identifiers must return zero matches; V3's single-DDL-owner grep holds | CI gate |

## Rate limits (gateway rules; Redis-backed counters)

| Route | Limit | On breach |
|---|---|---|
| `POST /api/v1/billing/plan/cancel` | 5/min/user; 20/h/user | 429 `throttled` + `Retry-After` |
| `POST /api/v1/billing/trial/start` | 3/min/user | 429 `throttled` |
| `POST /api/v1/ops/billing/subscriptions/{userId}/period-fix` | 20/min/operator; 100/h/operator | 429 `throttled` |
| `GET /api/v1/billing/plan` (35, extended) | 60/min/user | 429 `throttled` |
| Internal reads/notifications | service JWT; no user limit; job pacing is the throttle (<= 500 rows/class/run) | - |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Lifecycle job down | Resolver cutoff keeps truth (free after `period_end + graceDays`, never early); reminders late within their 24 h slack; `job_duration` alert fires |
| Feature 01 email seam down | Reminder skipped, flag unset, `lifecycle.reminder_failed` increments; retry next run; no money impact |
| Notification pipeline backlog/down | Flags stay unset, sends retry; backlog metric; state transitions unaffected |
| Owning surface down (04/09/18) | Surface enforces on its next successful snapshot read (`<= 30 s`); no lockout, no write-back to replay |
| `billing-service` down | Plan page reads freeze per 35's degrade; cancel/trial/period-fix return 503; checkout's grant path already owned by 34's degrade posture |
| Config flip invalid | Refused at validation; last valid `graceDays` remains in force |
| Trial prerequisites vanish mid-window (term retired) | Running trial keeps its stamped term; new trials return `trial_unavailable` |

## Audit and observability

Audit entries (24-month spine, actor + before/after where applicable): `plan.grace`, `plan.renewed`, `plan.cancelled`, `plan.expired`, `plan.downgraded`, `plan.trial_started`, `plan.period_fix`. Metrics per NFR-18 (`reminders_sent{template}`, `reminder_failed`, `grace_started`, `expired`, `renewed`, `cancelled`, `trial_started`, `trial_abuse_signal`, `downgraded`, `period_fix`, `job_duration`) with no PII labels. Logs structured; recipient emails masked; lifecycle transitions never log full problem bodies carrying email echoes.

## Support answers

"Why is this user pro?" -> the current row's `source` + audit trail. "Why did this expire?" -> `plan.expired` + `plan.downgraded` entries with the governing `period_end` and config version. "Why no reminder?" -> flag state + `reminder_failed` metric + pipeline idempotency key.
