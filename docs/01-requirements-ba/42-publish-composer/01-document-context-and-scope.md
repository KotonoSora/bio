# Publish Composer - Document Context and Scope (feature 42)

Phase 1 BA · Feature 42 of 53 — publish/automate 2/4. "Post everywhere" as one calm flow: one video, one caption, seven networks, a calendar, honest per-target status. Sources: `publish/composer/compose-schedule.md`, `publish/delivery/fanout-status.md`, `platform/delivery/publish-automate.md` (FR-3..FR-4). Implemented by `docs/02-architecture-techlead/42-publish-composer/`. Service: publish-service (port 8089, `publish_db`, Redis db 11).

## Context

Publishing is the least reliable input in the product: seven foreign APIs with seven moods. This feature makes partial failure a first-class visible state — "5 of 7 published, here are the two legs and why" — never a spinner that lies, never a silent 6-of-7. The legacy worker/queue model (`SCHEDULER` + `POST_QUEUE`, R2) maps to the mandated stack: Redis Streams with consumer groups (baseline 03), MinIO multipart for media, in-service worker pools behind `DispatchPort`. The legacy monolith-vs-split debate is superseded: the service boundary is fixed; further extraction of fan-out workers requires a spec amendment per the baseline's doctrine.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Every platform limit enforced before anything queues (duration, size, codec, caption, caps, quota) — failures happen in the composer with plain guidance |
| OBJ-02 | One video, one caption, per-network trims, healthy-connection targets only, one-click "Post to All" |
| OBJ-03 | Scheduling with timezone-explicit `run_at`, drafts, a 5-minute dispatch lock, pre-lock cancel, `late` never silent |
| OBJ-04 | Async fan-out: schedule returns once queued; per-target status machine; N-of-7 computed live, never hand-incremented |
| OBJ-05 | Exactly-once intent: idempotency keys where offered, dedupe records otherwise, explicit confirm gate on ambiguous timeouts |
| OBJ-06 | Honest retry: transient classes auto-retry with backoff; permanent classes fail immediately with plain-language reasons + fix guidance |
| OBJ-07 | Outage discipline: per-network pause, `queued_behind_outage`, ordered resume, poison quarantine with ops page |
| OBJ-08 | Quotas: free 10 lifetime published; pro 30-day rolling unlimited with velocity guardrails; over-quota refused with reset/upgrade path |

## Scope

In scope (SC-01..SC-08): media intake gates + MinIO multipart; caption mapping + per-network trims; target selection; schedule/cancel/reschedule + lock; calendar reads; quota accounting; dispatch scheduler + fan-out consumers + status polling; per-target status + retry + confirm gate; outage pause/resume + poison quarantine; ops console; E4 gate audit.

Out of scope: connections vault (41); automations, contacts, DMs (43/44); image/carousel/text posts (v1 is video-only); server-side transcoding (named v2 candidate, never v1); broadcast campaigns (gated 48–53); agency publishing (45–47).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | C/R/U/D own drafts + scheduled posts; R calendar + status; R retry failed targets | Composes, schedules, watches N-of-7 |
| Planner (same person) | R/U calendar moves | Deconflicts the week |
| Ops | R queue + status; C network pause/resume; C poison replay (dual-control on mass) | Recovers publishing during outages |
| System (scheduler/workers) | U target/dispatch states via guards | Bounded, idempotent repair |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Upload protocol (42 Q1 / publish-automate Q1) | MinIO multipart with server-issued parts (S3-compatible); resumable client; no client-chosen object keys |
| Q-02 | First split (publish-automate Q2) | Superseded: service boundary fixed; workers in-service behind `DispatchPort`; extraction needs a spec amendment |
| Q-03 | Server-side transcode in v2 (42 Q2) | Named as the v2 pipeline candidate; v1 rejects non-allowlisted media with the reason, never transcodes |
| Q-04 | Ambiguous-timeout UX (fanout Q1) | Poll-then-ask: reconcile with the provider first; only if unverifiable does the `unknown — confirm before repost` gate appear |
| Q-05 | Dispatch deadlines (fanout Q2) | Uniform 15 min per target (`publish.policy.targetDeadlineMinutes`), tuned from observed latency later |
| Q-06 | Lock window | 5 min before `run_at` (`publish.policy.lockMinutes`); stated in-UI |

## Constraints carried

Targets restricted to healthy connections (41); tokens never enter queue messages (opaque lease ids only); request handlers never await a publish; schedule-time validation is synchronous and complete; calendar reads post rows only (no separate event store to drift); quota state shown pre-confirm; every state change audits to the append-only spine.
