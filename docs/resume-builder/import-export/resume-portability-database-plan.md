# Resume Portability Database Plan (export envelope + fixture — no new tables)

PLAN ref: P2-5 scoped to resume (lossless round-trip, versioned schema, fixture gate) + scope guard (resume documents + `resume` blocks only) + S-1. BA source: `docs/resume-builder/import-export/resume-portability.md`. Consumed by the portability backend plan (export/import/adapters; splits next to this file). Document rows live in `docs/resume-builder/documents/resume-snapshot-database-plan.md`; block rows in `docs/link-to-bio/blocks/link-blocks-database-plan.md` (both referenced, never redefined here). No migration — this slice creates zero tables.

## Purpose

Define the portability data contract without owning storage: the versioned export envelope, what round-trip lossless means byte-for-byte, where the fixture lives, and the validation rules that keep imports from becoming an injection or DoS vector. No `CREATE TABLE` here by design — backup moves rows through files, never into new tables.

## Users

- Backend developer: implements export/import against the envelope and fixture below.
- QA: runs round-trip, rejection, draft-only, and fixture-gate drills.
- New user: bootstraps from LinkedIn/GitHub through adapters that land as drafts.

## Functional requirements

1. Export envelope (closed, versioned): `{schema_version: "1", exported_at, documents: [{title, template_id, is_public, snapshots: [{version, snapshot_json, created_at}]}], blocks: [linked resume-block rows {type, position, payload_json, is_visible, visible_from, visible_until}]}`. Scope guard is structural: the exporter queries ONLY `resume.documents/snapshots` + `profile_blocks WHERE type='resume'` — product/course/tip/form blocks, `products`, `leads`, `custom_domains` cannot appear (the query has no path to them, not just a policy against them).
2. Round-trip lossless defined: export → fresh-account import → re-export is semantically identical excluding `id` and timestamp fields (new ulids, new clocks — everything else byte-equal). Snapshots transfer as full version rows (history survives moves, not just current state); `published_version` pointers re-resolve to the matching imported version (by version number, not by old id).
3. `schema_version` gate: importer accepts only known versions; unknown/newer version → reject with `import_version_unsupported` naming the found vs supported versions, zero rows written (atomicity rule below). Every envelope change bumps the version with a documented migration or an explicit break note.
4. Fixture: `docs/resume-builder/import-export/fixture-resume.json` (decided here: beside the spec, versioned with it — discoverable where the contract lives, no shared-fixtures root to drift). Minimal but valid: one document, two versions, one linked block, placeholder person. Every schema change must keep the fixture importable (migration or documented break); CI imports it on an empty database (the fixture gate).
5. Avatar decision (decided here, BA Q1): exports carry the `avatar_key` REFERENCE, never image bytes (JSON stays text, size caps stay meaningful, no binary smuggling surface). Import with an unresolvable key shows `Avatar not transferred — re-upload` (graceful, non-blocking); resume rendering never depends on avatar presence.
6. Validation rules (applied before any row exists): size cap 2MB (reject `import_too_large` pre-parse — parse bombs die at the gate, not in JSON parsers); JSON schema check against the envelope (closed keys — unknown top-level keys reject, typo'd fields fail loudly instead of silently dropping); `https:` allowlist on imported link URLs; HTML sanitization on every imported string (imported payloads are the adversary's cheapest path to stored XSS — sanitized at import AND at capture, both layers); adapter-mapped drafts pass the same validators as typed input.
7. Atomicity: import is all-or-nothing per file (single transaction: documents + snapshots + block relink; any failure rolls back to zero rows — partial restores that orphan blocks are the failure mode this forbids). Dry-run mode validates and returns the would-create summary without writing (the confirm screen's data source).
8. Audit: every export/import/adapter run writes `audit_log{actor, action, target: document-or-adapter, at}` (export of own data is still logged — exfiltration forensics start here).

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/resume-builder/import-export/` returns nothing. Consumed tables: `resume.documents`, `resume.snapshots`, `profile_blocks(type=resume)` (their database plans own the DDL). Artifacts: envelope schema (rule 1), fixture file (rule 4).

## Acceptance criteria

- Round-trip drill: fixture + one real resume export→fresh-import→re-export diffs only in ids/timestamps.
- Rejection drill: malformed/oversized/newer-version files rejected with actionable codes, zero rows written (row counts asserted before/after).
- Scope drill: export of a profile with shop/leads/domain rows contains zero non-resume data (payload inspection).
- Fixture drill: clean-database fixture import green after every schema change (CI gate).
- Audit drill: each run logged with actor + action + target.
- Violation drill: any new table for backup staging (temp import tables) fails review — imports assemble in memory/transaction, never in staging tables.

## Out of scope

- Export/import/adapter flows (backend plan); wizard screens (frontend plan); snapshot versioning semantics (snapshot database plan); template rendering; share links; shop/leads/domains majors; continuous sync (one-shot imports v1 only); paid delivery via orders (shop + P3 billing).

## Open questions

- Q1: Adapter coverage — LinkedIn file-paste + GitHub handle enough for v1, or also LinkedIn URL scrape (ToS risk stays external)?
- Q2: Export cadence — on-demand download only, or scheduled emailed backups (P4 territory)?
