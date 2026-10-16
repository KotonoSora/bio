# Account Deletion - Architecture and Tech Stack (feature 02)

Phase 2 TL · Service: `account-service` (feature 01 extension; no new service, no new container). Implements `docs/01-requirements-ba/02-account-deletion/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Purge is a platform-wide participant contract: every service that owns user rows exposes an idempotent `POST /internal/v1/account/purge` returning per-table counts; the runner fans out and aggregates. | DEVIATION CARRIED vs the legacy monolith's per-major delete functions: microservices baseline makes per-service handlers the only alternative to cross-database deletes, which never exist. |
| D-2 | Deactivation propagates two ways: events for cancels/purges (schedulers, billing) and a 60 s-cached account-status read for read paths (profile resolve, sitemap). | Baseline rule: lost events self-heal via cache TTLs; public 404s never depend on event delivery alone. |
| D-3 | Username retirement is a profile_db tombstone (`profile.retired_usernames`), written by profile-service's purge handler; resolve after purge is plain `missing`. | Feature 06 owns the namespace; permanent reservation is its decided policy; the tombstone extends the retired check to deleted names. |
| D-4 | The runner is a daily in-service scheduler (03:30 UTC) with a Redis single-flight lock, bounded per-participant retries, and a 24 h stuck alert. | One code path, no new infra; idempotent by `runId` so retries are always safe. |
| D-5 | Certificates are JSON files on the local storage volume (`account/purge-certificates/{userId}/{runId}.json`), referenced from the deletion row. | Proof without a new store; retention rides the platform storage policy (`docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`). |
| D-6 | Legal retention floors (year counts, hashed-email allow-list) load from configuration. | Counsel-owned values; Q-01 stays a launch blocker, not a spec invention. |

## Boundaries

`account-service` owns: request/restore/status endpoints, deactivation fan-out, runner, certificates, probes, reminder job, deletion records. It does not own: per-service delete SQL (each owning service's spec), subscription cancellation (feature 36 consumes the event), export generation (features 17-21, 27-28, 37), org dissolution (45-47), legal-hold policy (legal). Sessions and step-up come from feature 01 unchanged.

## State machine

`grace` (requested; restorable; public hidden) -> `purged` (all participants succeeded; certificate emitted) or `grace` -> `cancelled` (restored; events unsuspend; billing stays stopped). `purge_started_at` marks a run in progress; a run failure keeps `grace` and retries. No other states exist; `purged` is terminal.

## Consumers

| Consumer | Mechanism | Effect |
|---|---|---|
| profile-service | `account.deletion_requested.v1`/`deletion_cancelled.v1` + 60 s `account-status` cache + purge handler | Resolve + sitemap parity (`missing`), event-driven cache purge, tombstone + row deletes + avatar files at purge (`docs/02-architecture-techlead/06-public-profile/`) |
| public-site-service | Events (best-effort purge of `ssr:*` keys) + TTL bound | Stale SSR pages live at most one cache window (`docs/02-architecture-techlead/10-share-and-seo/`) |
| link-service | Purge handler | Row + file deletes with counts (`docs/02-architecture-techlead/29-short-links/`) |
| analytics-service | Purge handler | User-scoped storage/clicks deletes (`docs/02-architecture-techlead/12-profile-analytics/`) |
| features 41-44 (forward) | `account.deletion_requested.v1` | Cancel queued posts/automations/campaigns; broadcasts exclude |
| feature 36 (forward) | `account.deletion_requested.v1` | Stop billing at request; never auto-resume |
| features 45-47 (forward) | `account.deletion_requested.v1` | Org-owner departure transfer/dissolution path |

Roster discipline: the participant list is configuration; a participant added by a later batch registers its handler in its own spec and appends here when it migrates. Dev boot fails on a registered-but-unreachable participant; production alerts.

## Ops runbook hooks

Stuck-run page includes `userId`, `runId`, failing participant, last counts; manual re-run command = same runner entry (idempotent); certificate probe command provided for support tickets. No support path can trigger a first-time purge (D-4 runner only, BR-11).
