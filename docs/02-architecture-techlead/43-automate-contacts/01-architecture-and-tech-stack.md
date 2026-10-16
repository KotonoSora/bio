# Automate Contacts - Architecture and Tech Stack (feature 43)

Phase 2 TL · Service: `publish-service` (port 8089, `publish_db`, Redis db 11). Implements `docs/01-requirements-ba/43-automate-contacts/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| `automation_contacts`/`contact_events` (V3); capture; the single eligibility check; suppression (three mechanisms); CSV export + leads handoff; hygiene jobs; quota counting for contacts | Send intents + watcher signals -> 44 (in-process); lead import -> 27's `POST /internal/v1/leads`; quota ladders -> `publish.quotas` (own `ops_config`); profile purge duty -> 02; notification enqueue -> 32's contract |

No new service, no new Redis keys (`pub:rl:*` reused).

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.publish.contacts`: `CaptureService`, `EligibilityCheck`, `SuppressionService`, `ExportService`, `HygieneJob`; 44's consumer calls `EligibilityCheck` in-process before every send | One gate, zero bypass paths (BR-03) |
| D-2 | `V3__automate_contacts.sql`: `automation_contacts`, `contact_events` + indexes + RLS + the counting function | Single DDL owner; append-only event trail |
| D-3 | Quota: one SQL function `contacts_counted(profile, since)` backs gate, meter, and reconciliation; free = rolling year window, pro = rolling month; unique recipient counts once via the row's `first_seen_at` | BR-09/NFR-05 |
| D-4 | Suppression: one transition method used by all three mechanisms (`OPTOUT` keyword via 44's watcher, removal-center token, provider button signal); emits `contact.suppressed.v1` post-commit | BR-04; 44 stops in-flight matching |
| D-5 | Export: CSV built to the uploads volume (`publish/exports/{userId}/{exportId}.csv`), expiring download grant ≤ 24 h, audited with row count + filter class; leads handoff = idempotent `POST /internal/v1/leads` per eligible row (email + actual class), paced ≤ 10 rows/s | BR-07/BR-08; 27 owns lead semantics |
| D-6 | Dedupe: `uniq_ac_identity (profile_id, network, network_user_ref)`; repeat comments append events + update `last_seen`; merges keep strictest consent class + earliest provenance | BR-01/BR-06 |
| D-7 | Hygiene: daily job — prune to `blocked_platform`, merge duplicates, park `active` rows idle ≥ `staleContactMonths` (12) to `stale`; reactivation only via a fresh capture event | BR-06/Q-02 |
| D-8 | Consent classes: `transactional` (default, set by automation flows) and `marketing` (never set here); exports/handoffs carry actual values; no upgrade path exists in code | BR-08 |
| D-9 | Config: `publish.quotas` gains `{freeContactsPerYear` 100, `proContactsPerMonth` 100000`}`; `publish.policy` gains `{staleContactMonths` 12`}` (own `ops_config`, audited 5 s flips) | One tunable surface for the major |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `MIGRATION.md` | row 43 + batch line at the batch tail |
| 27 / 02 / 32 / 44 | No edits: lead import, purge duty, notification contract, and in-process capture call are consumed as-is |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | No edit: publish-service row lands with 41's batch entry (covers 41–44) |
