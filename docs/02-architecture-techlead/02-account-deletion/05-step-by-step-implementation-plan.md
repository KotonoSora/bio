# Account Deletion - Step-by-Step Implementation Plan (feature 02)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/02-account-deletion/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V2__deletion_requests.sql` + RLS + partial due index; retention columns | Feature 01 T-02 | TL 02 drills 1-2 green |
| T-02 | BE | Endpoints `delete-request` / `delete-cancel` / `delete-status`: step-up gate, typed confirmation, idempotent re-request, status enum | T-01 | AC-10, BA 05 idempotency rows green |
| T-03 | BE | Deactivation fan-out: events + `account/status` API; profile-service consumes (`profile.acctstat` 60 s cache, resolve/sitemap `missing` parity, event invalidation); public-site best-effort `ssr:*` purge | T-02 | AC-08 green (`<= 60 s`) |
| T-04 | BE | Runner: daily 03:30 UTC scheduler, Redis lock + per-account DB guard, due query, bounded participant retries, 24 h stuck alert | T-01 | AC-07 green |
| T-05 | BE | Participant handlers: account-service own tables; profile-service (tombstone + rows + avatar files); link-service; analytics-service (rows + markers) | T-04 | AC-03 partial; NFR-09 zero cross-user deletes |
| T-06 | BE | Certificate emission (full success only) + retention split + legal-hold flag + hashed-email config read | T-05 | AC-06 green |
| T-07 | BE | Reminder job at day 23 (`reminder_sent_at` guard) + bounce surfacing | T-02 | NFR-03 green |
| T-08 | BE/FE | Restore flow + byte-identical drill + no-auto-recharge assertion (subscription fixture owned by feature 36) | T-03 | AC-01, AC-02 green |
| T-09 | BE | Weekly residual probe job + ops read route (`deletion-status`) + runbook hooks | T-06 | AC-03, AC-06 probe halves green |
| T-10 | FE | Consequences screen, typed confirmation, status panel, restore screen, export links | T-02 | UI halves of AC-01/AC-05 green |
| T-11 | DevOps | Drills wired: flow, billing fixture, retention, collateral, cooldown, certificate, failure, public-surface, no-shortcut | T-03..T-10 | G5 probes + G7 spot-check green |

## Notes

- DEVIATION CARRIED: the legacy monolith's per-major delete functions become the participant contract (D-1); per-service handlers ship under each service's own plan (profile-service additions recorded in `docs/02-architecture-techlead/06-public-profile/`, link rows in `docs/02-architecture-techlead/29-short-links/`, analytics in `docs/02-architecture-techlead/12-profile-analytics/`).
- Forward seams are registered, never stubbed: features 36 (billing stop), 41-44 (queued work cancel), 45-47 (org path) consume `account.deletion_requested.v1` when they migrate; their drills attach then.
- Legal floors (T-06 config) remain a launch blocker per Q-01: purge logic reads configuration; no year count or hashing rule is hard-coded.
- Evidence for "no dark pattern" (BR-01): the consequences screen ships with T-10 and its copy is part of the G7 spot-check, not just the API.
