# Resume Portability - Step-by-Step Implementation Plan (feature 21)

Phase 2 TL · Dependency-ordered chain; acceptance goals cite `docs/01-requirements-ba/21-resume-portability/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Envelope types + fixture move to `docs/02-architecture-techlead/21-resume-portability/fixture-resume.json` (invariant-correct: `is_public=false` -> `published_version=null`) + CI fixture gate + round-trip harness | - | AC-01, AC-06 green |
| T-02 | BE | profile-service additive rows: `GET /internal/v1/blocks/resume/by-user/{user_id}` + `POST /internal/v1/blocks/resume` (same validation as the owner route; landed in feature 08's contract file in this change) | Feature 08 codebase | AC-07 (structural read), AC-10 (create path) green |
| T-03 | BE | Export: query own documents/snapshots + the new block read; streaming serializer (chunked, backpressure, abort-safe); audit row per run; single-document variant | T-01, T-02 | AC-01 (export half), AC-11 green |
| T-04 | BE | Import: gate pipeline (size/parse/version/closed-schema/sanitize) -> dry-run summary -> single-flight lock + `Idempotency-Key` manifest -> phase 1 tx -> phase 2 relink -> public-landing rule | T-03 | AC-02, AC-09, AC-10, AC-12 (server half) green |
| T-05 | BE | Adapters: LinkedIn file mapper (positions/education/skills -> draft + `review_required`); GitHub fetch client (timeout/redirect/cap/private-range blocks) + mapper (repos/bio -> projects/links draft); provenance flags | T-04 (shared validators) | AC-03, AC-08 green |
| T-06 | FE | Backup buttons + toast counts; restore wizard (upload -> dry-run review -> confirm -> done); `ImportReview` (one component, three callers); adapter cards on empty state + settings; rejection states; session-recovery file preservation; no-token assertion | T-03..T-05 | AC-05 (UI half), AC-12 (client half) green |
| T-07 | DevOps/QA | Drills + gates: AC-01..AC-12 wiring; grep gates (zero `CREATE TABLE` here, no token input, zero staging tables); Turnstile/quota wiring; secrets audit; fixture gate in the build pipeline | T-05, T-06 | G5 + G7 green |

## Notes

- Rollout order: T-01/T-02 dark; T-03/T-04/T-05 behind the portability flag; T-06 flips the UI on. Nothing user-visible before T-06.
- Imports never write around the owning services: resume rows via `resume_db`, block rows via feature 08's routes, validation via the same validators as typed input.
- The export read is structural: only resume-type blocks for the caller's own user id are reachable (no type parameter, no cross-user path).
- Support runbook: the rejection-code -> wizard-state table (BA 05) attaches to the release note.
