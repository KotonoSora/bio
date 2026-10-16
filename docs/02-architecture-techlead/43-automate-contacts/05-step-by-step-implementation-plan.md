# Automate Contacts - Step-by-Step Implementation Plan (feature 43)

Phase 2 TL · Package `com.app.publish.contacts`. BA sources: `docs/01-requirements-ba/43-automate-contacts/`. Order: schema + eligibility gate first (44 cannot send safely without it).

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__automate_contacts.sql`: tables + indexes + counting function + RLS + drills | 42's V2 | V3 drills green |
| T-02 | BE | `CaptureService`: idempotent upsert + events + `contact.captured.v1`; wired into 44's send-acceptance transaction | T-01 | AC-07 |
| T-03 | BE | `EligibilityCheck`: single gate (status + class), indexed ≤ 50 ms; imported by 44's pre-send path; grep gate: no other gate | T-01 | AC-01 |
| T-04 | BE | `SuppressionService`: one transition; `OPTOUT` keyword hook (44's watcher), removal-center token flow, provider-button webhook; `contact.suppressed.v1` | T-01 | AC-02 |
| T-05 | BE | Quota: counting function wiring for gate/meter/reconciliation; `publish.quotas` values | T-01 | AC-06 |
| T-06 | BE | Export: CSV build to the uploads volume, audit + expiring grant; rate limits | T-02 | AC-03 |
| T-07 | BE | Leads handoff: paced idempotent calls to 27's `POST /internal/v1/leads`; skip-reason accounting | T-06 | AC-03 |
| T-08 | BE | `HygieneJob` (daily): prune blocked, merge duplicates (strictest consent), park stale (12 mo), reactivation on fresh capture | T-01 | AC-04 |
| T-09 | FE | Contacts list: filters, status chips, consent badges, suppress/export/handoff dialogs, quota meter | T-04, T-06, T-07 | UI flows complete |
| T-10 | FE | Removal center page (public, tokenized, one action, honest states) | T-04 | AC-02 |
| T-11 | BE/DevOps | Purge duty: 02's deletion removes rows/events; export files expire; handoff refs orphan | T-06, T-07 | Purge drill |
| T-12 | BE/DevOps | Drills: boundary, suppression unity, export/handoff fixtures, merge rules, stale boundary, consent-immutability grep, IDOR/RLS | T-02..T-10 | AC-01..AC-07 |
| T-13 | DevOps | Metrics + alerts: captures, suppressions by mechanism, export/handoff results, quota holds, hygiene counters | T-05..T-12 | NFR-11 |

## Sequencing notes

1. T-01/T-03 gate 44: the pre-send gate must exist and be drill-verified before any DM can send.
2. T-02 lives inside 44's send-acceptance transaction — one transaction, one truth; failed sends write nothing.
3. T-06/T-07 are privacy-sensitive: export audit + grant expiry + handoff pacing land together; review with the 27 owner.
4. Consent immutability grep (`UPDATE ... SET consent_class`) is a CI gate from T-01 onward.
5. Feature gate: E4 scope audit — zero publish tables/jobs/endpoints in E0–E3 images (release-gate grep).

## Definition of done

AC-01..AC-07 green; single-gate + consent-immutability + counting-function greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 43 flips to `done` at the batch tail with `automate/contacts/contact-lists.md` deleted.
