# Publish Composer - Step-by-Step Implementation Plan (feature 42)

Phase 2 TL · Package `com.app.publish.composer`. BA sources: `docs/01-requirements-ba/42-publish-composer/`. Order: schema + gates first; dispatch machinery second; status/ops third.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V2__publish_composer.sql`: three tables + indexes + RLS + rollup/quota functions + drills | 41's V1 | V2 drills green |
| T-02 | BE | `MediaGate`: multipart init/complete, magic-byte, allowlist, key randomization; `UploadInitView` | T-01 | AC-01, AC-11 |
| T-03 | BE | `ScheduleService`: validation (gates, targets health, caps, quota, caption), post+target rows, edit/cancel + lock window | T-01, T-02 | AC-01..AC-04 |
| T-04 | BE | `QuotaCounter`: single counting function for gate/meter/reconciliation (free lifetime, pro rolling + velocity) | T-01 | AC-02 |
| T-05 | BE | Scheduler: due-row selection `FOR UPDATE SKIP LOCKED`, parent `sending`, XADD children `{postId, targetId, leaseRef}`; resume path for crashed ticks | T-03 | AC-07, AC-10 |
| T-06 | BE | Dispatch consumers: group `publish-dispatch`, XAUTOCLAIM ≤ 60 s, guarded per-target updates, provider calls via 41 leases + idempotency keys, retry classes, deadline → `timeout` | T-05 | AC-07, AC-08 |
| T-07 | BE | `StatusRollup`: computed parent state + N-of-7; per-target reasons; confirm gate + manual retry eligibility | T-06 | AC-06, AC-12 |
| T-08 | BE | Dead-connection consumers: `connection.dead.v1` / `connection.disconnected.v1` → `skipped_dead_connection` + notice | T-06 | AC-04 (41 seam) |
| T-09 | BE | Outage: `publish.network_pause` fresh-read, `queued_behind_outage` hold, ordered resume; DLQ + poison quarantine + replay | T-06 | AC-09 |
| T-10 | BE | Webhooks `POST /webhooks/social/{network}` publish-confirmation event types → guarded reconciliation | T-06 | AC-07 |
| T-11 | FE | Composer: upload, caption + trims, target picker, schedule, quota meter, pre-queue failure copy | T-02..T-04 | UI flows complete |
| T-12 | FE | Calendar + post status view: month/week, drag-reschedule, N-of-7, per-target retry + confirm gate, late badge | T-07 | AC-03, AC-06 |
| T-13 | FE | Ops console: pause toggles, queue/lag charts, DLQ table + replay | T-09 | Ops flows complete |
| T-14 | BE/DevOps | Cross-edits: baseline 01 public paths (with 41/44); MIGRATION at batch tail | T-01 | Diff review green |
| T-15 | BE/DevOps | Drills: gate matrix, quota fixtures, lock boundary, IDOR, 5-of-7 honesty, idempotency redelivery, retry classes, outage pause/resume, load fan-out | T-01..T-14 | AC-01..AC-12 |
| T-16 | DevOps | Metrics + alerts: dispatch-to-publish p95, failure classes, retry outcomes, queue depth/lag, quota refusals, late dispatches, quarantine | T-05..T-15 | NFR-14 |

## Sequencing notes

1. T-01/T-04 first: the counting function is shared by gate, meter, and reconciliation — no second implementation may appear (grep gate).
2. T-05/T-06 are the only dispatch writers; review with 41's vault owner (leases) and run the message-hygiene scan before enabling the scheduler.
3. T-07 ships before T-12: the UI renders computed states only; no client-side rollup logic exists.
4. T-09's pause path is drill-verified ≤ 5 s fresh-read before ops access is granted.
5. Feature gate: E4 scope audit — zero publish tables/jobs/endpoints in E0–E3 images (release-gate grep).

## Definition of done

AC-01..AC-12 green; message-hygiene and single-counting-function greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 42 flips to `done` at the batch tail with `publish/composer/compose-schedule.md`, `publish/delivery/fanout-status.md`, and `platform/delivery/publish-automate.md` deleted.
