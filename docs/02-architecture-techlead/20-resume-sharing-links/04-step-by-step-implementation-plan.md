# Resume Sharing Links - Step-by-Step Implementation Plan (feature 20)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/20-resume-sharing-links/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `ShareSourceResolver` (single decision point): document flags -> frozen version; `unpublished` state mapping; URL composer (pure, table-tested) | Feature 17 T-04 (published read), feature 06 by-user | AC-02 (resolver half), AC-08 green |
| T-02 | BE | Helper endpoints: `GET share-links` (derivation over four internal reads: profile by-user, domains by-user, blocks by-document, existing short row) + `POST share-links/shorten` (idempotent mint via link-service) | T-01 | AC-03 (mint half), AC-05, AC-09 (server half) green |
| T-03 | BE | link-service extension: `V2__resume_refs.sql` + `POST /internal/v1/links/resume-share` (mint-or-get, quota-exempt) + `username.renamed.v1` rewrite consumer (+ resolve-cache purge) | Feature 29 codebase | AC-03, AC-04 green |
| T-04 | BE | Cross-service read rows: feature 04 `by-user` domains read; verify feature 08 `by-document` linkage read serves the dialog's `bioLinked` state | Feature 04 / 08 codebases | AC-11 (state half), AC-12 (custom/bio rows) green |
| T-05 | FE | Share dialog (one `ShareLinkRow` x five), unpublished state, first-copy mint disclosure, SEO tag live-check, QR jump, revocation/rename captions, A4 view (feature 18 pipeline), bio toggle (feature 17/08 routes) | T-02, T-04 | AC-09, AC-10, AC-11 (UI halves) green |
| T-06 | DevOps/QA | Drills + gates: AC-01..AC-12 wiring; purge fan-out re-verification across the four events; grep gates (zero share-URL storage, zero PDF code both sides); unlimited-share assertion (no quota code path) | T-03, T-05 | G5 probes + G7 spot-check green |

## Notes

- Rollout order: T-01/T-02 ship dark behind the dialog flag; T-03 ships with link-service release train; T-05 flips the dialog on. Nothing user-visible before T-05.
- The helper never queries another service's database: all five inputs are internal API reads (feature 04/06/08 rows + own rows + link-service route).
- Everything user-visible reuses an owning surface: cv rendering (10), shortener pages (29), domain serving (04), print (18). This feature adds no renderer, no cache layer of its own.
- Support runbook: the TL 02 matrix attaches to the release note; support reads only that table.
- No quotas exist by construction: grep asserts no quota counter on the resume-share mint path; the partial unique is the only limiter.
