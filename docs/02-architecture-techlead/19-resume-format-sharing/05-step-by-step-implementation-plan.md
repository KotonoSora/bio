# Resume Format Sharing - Step-by-Step Implementation Plan (feature 19)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/19-resume-format-sharing/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V2__format_sharing.sql`: `formats` + `format_clones` + RLS + RESTRICT + indexes | Feature 17 T-01 | TL 02 drills 1, 2, 5, 6, 7 green |
| T-02 | BE | `FormatValidator` (pure, allowlist, field-mapped codes) + draft CRUD (`GET/POST/PUT` mine) | T-01 | AC-01 (validation half), AC-11 (no-JSON-editor half) green |
| T-03 | BE | Publish/unpublish: scrub proof over exact bytes, `vN+1` insert + draft delete in one tx, audit, thumbnail auto-render (placeholder fixture) | T-02 | AC-08 green; AC-01 (zero-rows half) green |
| T-04 | BE/FE | Clone + selection extension: copy `layout_json`, idempotent `already:true`, `fmt:` acceptance on feature 17's PUT, layout interpreter wiring into feature 18's render pipeline | T-03 | AC-04, AC-09 green |
| T-05 | BE | Public gallery: list + detail with attribution resolution (feature 06 internal read), 60 s cache, edge purge consumer | T-03 | AC-03 (live/hidden states), AC-05 (public half) green |
| T-06 | BE | Moderation: report intake (Turnstile, anonymous allowed) + ops remove route (reason mandatory, audited) + removed-state detail | T-05 | AC-07 green |
| T-07 | BE | `FormatRescanJob` (weekly 04:40 UTC, personal-data key re-scan, flags + metric, no auto-action) | T-03 | AC-01 (re-scan half) green |
| T-08 | FE | Builder (form-only, placeholder preview, field-mapped errors), my-formats, gallery cards/detail, clone confirm with pinning copy, report form, offline draft | T-04..T-06 | UI halves of AC-02..AC-10 green |
| T-09 | DevOps/QA | Drills + gates: AC-01..AC-11 wiring, rate envelopes, grep gates (no upload route, no JSON textarea, no paywall hooks), print fixtures add shared formats | T-07, T-08 | G5 probes + G7 spot-check green |

## Notes

- Rollout order: T-01..T-03 dark (author-only, gallery behind a flag), T-05 opens the public gallery, T-06 moderation must ship before the gallery flag flips (no ungated public surface without a takedown path).
- Purge participant: the resume-service handler extends to `formats`, `format_clones`, and thumbnail files; feature 02's roster row was updated in this change.
- The layout interpreter is additive to feature 18's pipeline: registry templates keep rendering unchanged; a `layout_json` render fixture suite (per section variant x token set) gates T-04.
- Weekly re-scan can never auto-remove (BR-06): the job writes flags + metric only; removals stay human + audited.
- No pricing or gating anywhere: a grep gate asserts no paywall hooks exist in this feature's code paths (AC-11).
