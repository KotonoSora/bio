# Automate Contacts - Workflow and User Journey (feature 43)

Phase 1 BA · W-1..W-4. Rules: `docs/01-requirements-ba/43-automate-contacts/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/43-automate-contacts/03-api-contracts-and-payloads.md`.

## W-1 Capture (from 44's delivery pipeline)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (44) | send intent created for a matched comment | eligibility check runs (quota + suppression + class) before any send | BR-03, BR-09 |
| 2 | System | DM accepted for delivery | capture writes the contact row (idempotent per automation+comment) + `contact_events` row | BR-01, BR-02 |
| 3 | System | repeat comment from the same person | `last_seen` updates + event appended; no duplicate row | BR-01 |
| 4 | Creator | opens contacts | list shows provenance: automation, keyword, media, first seen | US-01 |

## W-2 Suppression (three mechanisms, one transition)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Contact | replies `OPTOUT` in any thread | 44's watcher matches the keyword → suppression transition (no DM sent for it) | BR-04 |
| 2 | Contact | uses the profile-link removal center | signed-token page → one action → same transition | BR-04 |
| 3 | Contact | taps the platform removal button (where offered) | provider signal → same transition via verified webhook | BR-04 |
| 4 | System | any of the three | `status='suppressed'` + reason + audit; eligibility check excludes everywhere, effective immediately | BR-03, BR-04 |
| 5 | Creator | sees the contact | status chip `suppressed` with reason; excluded from export-for-sending | BR-04 |

## W-3 Export

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | requests CSV export (filter optional) | rate-limit check → job builds the file → audited (`contacts.exported`, row count) | BR-07 |
| 2 | System | file ready | expiring signed download (≤ 24 h); provenance columns included | BR-07 |
| 3 | Creator | triggers leads handoff | per eligible row: idempotent `POST /internal/v1/leads` with automation source stamp + actual consent class | BR-07, BR-08 |
| 4 | System | handoff results | counts surfaced (imported, skipped-ineligible); failures retryable; no consent upgrading anywhere | BR-08 |

## W-4 Hygiene (scheduler)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scheduler | platform-block signal or failed-send bounce class | row prunes to `blocked_platform` + event | BR-06 |
| 2 | Scheduler | duplicate identity detected (post-merge reconciliation) | strictest consent class + earliest provenance kept; one row remains | BR-06 |
| 3 | Scheduler | no interaction for 12 months | park to `stale`; excluded from sends and export-for-sending | BR-06, Q-02 |
| 4 | System | parked contact comments again | fresh consent event → active again with new provenance | Q-02 |
