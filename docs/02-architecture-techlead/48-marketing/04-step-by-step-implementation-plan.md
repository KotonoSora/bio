# Marketing Consent and Broadcasts - Step-by-Step Implementation Plan (feature 48) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/48-marketing/`. Contracts: `docs/02-architecture-techlead/48-marketing/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero marketing surface (schema diff, OpenAPI diff, source + bundle grep: `consents`, `EligibilityCheck`, preference-center strings, campaign UI) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — marketing remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently; promotion review cites the amendment |
| T-03 | BE | AT AMENDMENT: `marketing_db` bootstrap + `V1__marketing.sql` (six tables + RLS + append-only grants) | amendment; 01 D-1 | Schema drills green |
| T-04 | BE | AT AMENDMENT: `ConsentService` (append-only capture, proof exports, deletion integration) | T-03 | AC-03, AC-06 |
| T-05 | BE | AT AMENDMENT: `EligibilityCheck` internal route + CI grep gate (no sender ships without it) | T-03 | AC-05, AC-12 |
| T-06 | BE | AT AMENDMENT: `PreferenceCenter` (signed tokens, toggles, receipt, unsubscribe, re-consent + throttle) | T-04 | AC-04, AC-16 |
| T-07 | BE | AT AMENDMENT: `AudienceService` (ingestion, strictest-wins merge, hygiene jobs, 12-month parking, exports) | T-03 | AC-07..AC-11 |
| T-08 | BE | AT AMENDMENT: `CampaignService` (compose, test-send, review count, two-step confirm, per-recipient idempotent dispatch, caps + auto-pause) | T-05, T-07 | AC-12..AC-15 |
| T-09 | BE | AT AMENDMENT: `ResendWebhook` (verified, idempotent transitions + stats) + dedicated marketing subdomain config | T-08 | AC-14 |
| T-10 | BE | AT AMENDMENT: pipeline extension in billing-service (`sendClass='marketing'` path via eligibility; refusal mapping) | T-05; 32 contract | AC-12, AC-15 |
| T-11 | FE | AT AMENDMENT: preference center + in-account mirror + ops surfaces (health, import, campaign flow, proof lookup) | T-06..T-08 | UI flows complete |
| T-12 | DevOps | AT AMENDMENT: drill battery (classification, proof, withdrawal, enforcement, deletion, lifecycle, import, hygiene, export, silo, eligibility, two-step, stats, reputation, exclusions) | T-03..T-11 | AC-02..AC-16 green |
| T-13 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-12 | NFR-13; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced, not policy; the pipeline's `marketing_not_accepted` refusal stays until the amendment record exists.
2. T-05 before any sender work: the single eligibility function is the enforcement architecture — new senders cannot ship without calling it.
3. T-09's webhook is verified + idempotent from the first commit; stats-only (no grant-like side effects).
4. T-10 is a cross-service edit reviewed with the 32 owner; transactional/lifecycle paths must be provably untouched.
5. Feature gate: E4 scope audit — zero marketing surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-16 green; single-eligibility + append-only + no-exclusion-surface greps green in CI; full-tree validation green; `MIGRATION.md` feature 48 row notes the amendment when it lands.
