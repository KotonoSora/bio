# Automate Triggers - Architecture and Tech Stack (feature 44)

Phase 2 TL · Service: `publish-service` (port 8089, `publish_db`, Redis db 11). Implements `docs/01-requirements-ba/44-automate-triggers/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| Webhook intake for comment events; `automations`/`trigger_log`/`dm_templates`/`send_intents` (V4); match evaluator; delivery pipeline (reply + DM); caps/pacing/quiet/burst-abort; template checks + review queue; ops health/reinstate | Healthy IG connection + scopes -> 41; contact capture + single eligibility gate -> 43 (in-process); notification enqueue -> 32's contract; destination allowlist policy -> 29/30's link policy; queue conventions -> baseline 03 |

Watcher + delivery consumers run in-service behind `WatchPort` (extraction seam per baseline doctrine). No new service.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.publish.automate`: `WebhookIntake`, `WatcherConsumer`, `MatchEvaluator`, `TemplateChecker`, `DeliveryConsumer`, `CapBuckets`, `BurstMonitor`; intake never matches or sends in the request | One decision path; webhook ack ≤ 300 ms |
| D-2 | `V4__automate_triggers.sql`: four tables + indexes + RLS; intent status is the single delivery truth | Single DDL owner; append-only trigger log |
| D-3 | Streams: `automate.watch` (intake → match, group `automate-match`) and `automate.delivery` (match → send, group `automate-deliver`); XAUTOCLAIM ≤ 60 s; max-delivery 5 → DLQ; replay-safe consumers | Baseline 03 conventions; isolation of matching from sending |
| D-4 | `MatchEvaluator`: normalize (case-fold + trim) → scope check → self/blocklist → collision-ordered first match by rule creation order; always writes a `trigger_log` decision row; first observed comment version only | Exact, auditable, deterministic (BR-01..BR-05) |
| D-5 | Delivery order fixed: contacts quota (43) → suppression → 24 h window → quiet hours → fire cap → pacing → send (public reply first, then DM); holds are states re-evaluated on release | No bypass path exists; holds are visible (BR-10) |
| D-6 | Caps: Redis counters `pub:cap:fire:{automationId}:{mediaId}:{hour}` and token bucket `pub:pace:{accountId}` (burst 10/min, sustained 30/min); DB intents are truth — counters rebuild from intents on Redis loss | Self-healing enforcement (BR-07/BR-13) |
| D-7 | `BurstMonitor`: sliding 10-min window from intent outcomes; > 20% failures with ≥ 20 attempts → auto-pause (`paused_reason='burst_abort'`) + `automation.auto_paused.v1` + ops page | BR-14; reinstate is ops-only |
| D-8 | Quiet hours: creator-local from the automation's stored `timezone` (default Asia/Ho_Chi_Minh), 22:00–08:00 (`publish.policy`); `held_quiet` releases in order ±60 s | Recipient-local impossible on IG (no timezone) — creator-local fails safer (Q-04) |
| D-9 | `TemplateChecker` at save/edit: exactly one link slot, https + allowlist destination (29/30 policy), prohibited classes (stealth/misleading), length limits; reported → `pending_review` + pause-until-decided | BR-15; stealth is a spec violation |
| D-10 | Idempotency: `uniq_si_comment (automation_id, comment_ref)`; one intent per comment; comment delete cancels pending; webhook anti-replay insert-first | BR-17 |
| D-11 | Config: `publish.policy` gains `{fireCapPerHour` 60, `quietStart` "22:00", `quietEnd` "08:00", `dmDelayMinSeconds` 60`}`; `publish.quotas` gains `{freeKeywordRules` 3, `freeAnyCommentRules` 4`}` (own `ops_config`, audited 5 s flips) | One tunable surface for the major |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | public paths: `POST /webhooks/instagram` anonymous callback entry (lands with this feature's batch) |
| `MIGRATION.md` | row 44 + batch line at the batch tail |
| 41 / 43 / 32 / 29-30 | No edits: connection/scopes, capture + eligibility, notification contract, and link policy consumed as-is |
