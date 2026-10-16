# Resume Portability - Data Contract and Fixture (feature 21)

Phase 2 TL · **Zero migrations**: backup moves rows through files, never into new tables. BA sources: `docs/01-requirements-ba/21-resume-portability/` (BR-01..BR-10). DDL of consumed tables lives in feature 17 (documents/snapshots) and feature 08 (blocks) - never redefined here.

## No-DDL assertion

- `grep -rn "CREATE TABLE"` over this directory returns nothing; imports assemble in memory and commit through the owning services - staging tables are forbidden (violation drill, BA AC-10).
- Consumed tables: `resume.documents`, `resume.snapshots`, `profile_blocks` (type `resume` only). The export query has no path to any other table (structural scope guard, BR-09).

## Envelope v1 (closed)

```json
{"schema_version": "1", "exported_at": "<rfc3339>",
 "documents": [{"export_id": "doc-1", "title": "...", "template_id": "classic",
   "is_public": false, "published_version": null,
   "snapshots": [{"version": 1, "snapshot_json": {}, "created_at": "<rfc3339>"}]}],
 "blocks": [{"type": "resume", "position": 3,
   "payload_json": {"documentId": "doc-1", "showContact": true},
   "is_visible": true, "visible_from": null, "visible_until": null}]}
```

- `export_id` is a file-local handle (`doc-1`, `doc-2`, ... by export order) used for block relink only; import assigns fresh ids.
- `snapshot_json` follows feature 17's closed schema; `payload_json` follows feature 08's resume payload. Unknown top-level keys reject; nested keys follow the owning validators.
- `blocks[]` carries only blocks whose `payload_json.documentId` is in the exported document set (unlinked rows are not restorable facts and are excluded).
- Every envelope change bumps `schema_version` with a documented migration or an explicit break note.

## Round-trip definition

Export -> fresh-account import -> re-export is semantically identical modulo ids and timestamps: full snapshot history transfers; `published_version` re-resolves by version number; `blocks[]` relink by `payload_json.documentId` <-> `export_id`. Sole documented divergence: a `public_conflict` landing (TL 01 D-4) legitimately changes `is_public` on re-export.

## Import landing rules

| Envelope row | Landing |
|---|---|
| `is_public=false` | Private; `published_version=NULL` (feature 17 unpublish invariant) |
| `is_public=true`, target has no public document | Public; `published_version` re-resolved by version number |
| `is_public=true`, target already public | Private + `public_conflict` warning (dry-run + result) |
| `blocks[]` row, `documentId` resolvable in-envelope | Created via the internal create route after documents commit |
| `blocks[]` row, unresolvable `documentId` | Skipped + `ImportWarning` (never a dangling row) |

## Write-phase ordering and retry

1. Gates (size -> parse -> version -> closed schema -> sanitize/allowlist) complete with zero rows written; dry-run is the same pipeline with no writes.
2. Phase 1: one `resume_db` transaction creates documents + snapshots (fresh ids).
3. Phase 2: per-block create in `profile-service` (idempotent by the run manifest); failures return `503 import_unavailable`.
4. Retry: the SPA reuses its `Idempotency-Key` (generated at upload). Manifest `resume:import:{userId}:{idempotencyKey}` (Redis db 8, TTL 24 h) records new ids + linked blocks + phase; a same-key retry completes phase 2 or replays the 201 body - no duplicate documents. A fresh key is a fresh import (copies-only semantics).
5. Single-flight: `resume:import:lock:{userId}` serializes runs per account; Redis down -> fail closed (503).

## Fixture

`docs/02-architecture-techlead/21-resume-portability/fixture-resume.json`: placeholder person, one document ("Backend resume", `classic`, `is_public=false`, `published_version=null`), two snapshot versions, one linked visible `resume` block at position 3 whose `payload_json.documentId` equals the document's `export_id`. CI imports it against a clean PG `18.6` (Testcontainers) on every build and runs the round-trip diff; a schema change that breaks the import fails `./gradlew build` (migration or documented break decided in the same change).

## Verification drills

1. No-DDL drill: `CREATE TABLE` grep over this directory returns nothing.
2. Round-trip drill: fixture + one real resume -> diff = ids/timestamps only (modulo the documented `public_conflict` case).
3. Rejection drill: oversized/malformed/newer files -> exact codes; row counts identical before/after on both services.
4. Relink drill: kill `profile-service` after phase 1; same-key retry links blocks without duplicating documents.
5. Fixture drill: fixture import on empty `resume_db` green after every migration replay.
6. Manifest drill: expired manifest (TTL) -> the retry behaves as a fresh import (documented; no silent partial-restore claim).
