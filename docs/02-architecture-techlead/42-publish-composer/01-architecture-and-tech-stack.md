# Publish Composer - Architecture and Tech Stack (feature 42)

Phase 2 TL · Service: `publish-service` (port 8089, `publish_db`, Redis db 11). Implements `docs/01-requirements-ba/42-publish-composer/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| Composer validation + media gates; `scheduled_posts`/`post_targets`/`publish_events` (V2); the dispatch scheduler + consumers; status rollup + retry + confirm gate; calendar reads; quota counting; outage pause + quarantine; ops console | Healthy accounts + in-process token leases -> 41; quota ladders + policy -> `publish.quotas`/`publish.policy` (own `ops_config`); notification enqueue -> 32's contract; queue conventions -> baseline 03; plan identity for quota tier -> 35 |

Worker pools run in-service behind `DispatchPort` (extraction seam per baseline doctrine; any split needs a spec amendment).

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.publish.composer`: `MediaGate`, `CaptionMapper`, `ScheduleService`, `DispatchWorker`, `StatusRollup`, `QuotaCounter`; dispatch never runs in request threads | One validation path; one dispatch path |
| D-2 | `V2__publish_composer.sql`: `scheduled_posts`, `post_targets`, `publish_events` (+ indexes + RLS) | Single DDL owner; append-only trace |
| D-3 | Queue: Redis Stream `publish.jobs` with consumer group `publish-dispatch`; messages carry `{postId, targetId, leaseRef}` only; XAUTOCLAIM reclaim ≤ 60 s; max-delivery N=5 → dead-letter stream `publish.jobs.dlq` | Baseline 03 conventions; tokens never serialized (41's lease pattern) |
| D-4 | Scheduler: `@Scheduled(fixedDelay = 60000)`; selects due rows `FOR UPDATE SKIP LOCKED`, marks parent `sending`, XADDs children, commits; idempotent per row; past-due handled on the same tick with `late` flag | No second writer; at-least-once is safe |
| D-5 | Status: parent state computed from children on every read (`partial` = mixed terminal outcomes; `late` = overlay flag); the N-of-7 counter is a SQL aggregate — never a hand-incremented column | No drift by construction (BR-09) |
| D-6 | Idempotency: provider idempotency key `post-{postId}-{targetId}` where offered; else dedupe record in `post_targets` + `confirm_required` gate on ambiguous-timeout retries (poll-then-ask first) | Exactly-once intent (BR-11) |
| D-7 | Retry classes in the worker: transient (rate-limit/5xx) auto with backoff ≤ 5 attempts; permanent (auth/policy/media) terminal with fix guidance; manual retry eligibility checked server-side | Honest recovery (BR-12) |
| D-8 | Outage: per-network pause flag in `publish.policy`-adjacent ops state (own `ops_config` document `publish.network_pause`); dispatch reads fresh (≤ 5 s); resume replays in `run_at` order | Ops control without redeploys |
| D-9 | Quota: one SQL counting function backs gate, meter, and reconciliation; free 10 lifetime (`quota_counted_at` on first success; retried-once counts once); pro 30-day rolling from `quota_counted_at` history + velocity guardrails | One source of truth (BR-07) |
| D-10 | Media: MinIO multipart, server-issued parts; magic-byte verification; object keys server-random; preview URLs signed ≤ 15 min; no transcode path exists in v1 | Gates before queue (BR-01/BR-02) |
| D-11 | Config: `publish.policy` {`lockMinutes` 5, `targetDeadlineMinutes` 15, `conflictWindowMinutes` 60} + `publish.quotas` {`freeLifetimePosts` 10, `freeConnectionCap` 1, `proConnectionCap` 10, …} in own `ops_config` | Audited 5 s flips |
| D-12 | E4 gate: release-gate audit greps E0–E3 images for zero publish tables/jobs/endpoints | Legacy P4 gate mechanized |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | public paths: publish callbacks land here (OAuth callbacks with 41; `POST /webhooks/social/{network}` with 41/44) |
| `MIGRATION.md` | rows 42 + batch line at the batch tail |
| 41 / 32 / 35 | No edits: vault leases, notification contract, plan read consumed as-is |
