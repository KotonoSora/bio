# Account Deletion - Security, Logging, and Rate Limiting (feature 02)

Phase 2 TL · BA sources: `docs/01-requirements-ba/02-account-deletion/` (BR-01, BR-04, BR-06, BR-08, BR-11). Step-up and session controls: `docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`; baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Destructive gating: owner session + CSRF + step-up `<= 10 min` + exact typed confirmation; support can never trigger, accelerate, or purge | AC-10, AC-09 drills |
| S-2 | Collateral scoping: every purge handler is `user_id`-parameterized; wildcard/unscoped deletes fail review; probe asserts zero cross-user impact | AC-04; NFR-09 probe |
| S-3 | Idempotency + single-flight: `runId` idempotent handlers; Redis lock (contention only) + per-account DB row guard (truth); concurrent runner -> `409 purge_in_progress` | AC-07 double-run drill |
| S-4 | Certificate integrity: emitted only on full participant success; private bucket, server-side reads only; support reads audited | AC-06; bucket policy probe |
| S-5 | Propagation bound: public surfaces hide within `<= 60 s` via the status-cache bound; on account-service outage read paths serve the cached value and assume `active` on a cold miss (+ counter + alert) - availability wins for public reads, and the trade-off is bounded by the cache window | AC-08; outage drill documents the bound |
| S-6 | Retirement integrity: held name during grace; tombstone insert and profile delete in one transaction at purge; resolve after purge is `missing` | AC-05; rename/tombstone constraint re-use (`username_retired`) |
| S-7 | Retention minimization: certificate and deletion record carry ids/counts/timestamps only; buyer-email hashing from config; legal-hold flag recorded honestly | NFR-10; certificate schema review |
| S-8 | Audit completeness: request, cancel, purge start, participant results, certificate, support reads - append-only with actor + target | AC-06; grant probe (no UPDATE/DELETE) |
| S-9 | Probe discipline: weekly residual probes + stuck-run alert; alerts include `userId`, `runId`, failing participant | AC-03; ops runbook walkthrough |
| S-10 | Mail + secrets: reminder is transactional class only; no secrets or PII in purge paths; bucket credentials via vault | Grep gate + bounce drill |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| `delete-request` / `delete-cancel` | 10 req/min/user | `429 throttled` + `Retry-After` |
| `delete-status` reads | 60 req/min/user | `429` |
| Support status/certificate reads | 30 req/min/operator, audited | `429`; anomaly alert |
| Internal purge/probe routes | service budget, trace-attributed | `429` + trace; runner backoff |

## Degrade matrix

| Condition | Behavior |
|---|---|
| account-service down | New requests/restores deferred; in-flight purge safe (DB guard, resumes next run); support reads fail closed; public reads follow the 60 s cache bound |
| Participant service down mid-purge | Bounded retry next run; status stays `grace`; 24 h stuck pages ops; never a partial "purged" |
| Mail provider down | Reminder retried within the day and again `>= 5 d` before purge; support flagged; purge date never moves |
| Redis lock store down | Runner still safe (DB guard is truth); run skipped + alert - never two runners |
| Probe job failure | Alert; next weekly run re-covers; certificate diff retained |
| Legal hold active | Held evidence retained, everything else purged, certificate notes the hold |

## Logging

- Fields: `userId` (internal), `runId`, participant, table counts, status, timing, trace; never emails, names, content, or hashed-email values.
- Alertable counters: stuck runs, participant failures, residual probes non-zero, reminder bounces, `purge_in_progress` conflicts, support-read anomalies.

## Gates

- G5: S-1, S-2, S-4 probes run per release; S-3 double-run drill is part of AC-07.
- G7: the drill set (AC-01..AC-10) attaches to the tag; forward consumer drills (36, 41-44) attach at their batches.
