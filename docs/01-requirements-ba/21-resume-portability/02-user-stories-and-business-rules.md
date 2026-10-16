# Resume Portability - User Stories and Business Rules (feature 21)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/21-resume-portability/`. Scope: `docs/01-requirements-ba/21-resume-portability/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | download a backup, restore after accidental delete, and move between accounts | I avoid lock-in | High |
| US-02 | New user | bootstrap a first resume from LinkedIn export data or a GitHub handle | I can start without manual entry | High |
| US-03 | Developer | run the fixture profile through migrations | breaking schema changes are caught | High |

## Business rules

- **BR-01 Versioned closed envelope.** Export is `{schema_version: "1", exported_at, documents[{export_id, title, template_id, is_public, published_version, snapshots[{version, snapshot_json, created_at}]}], blocks[{type, position, payload_json, is_visible, visible_from, visible_until}]}`. Unknown top-level keys reject on import; unknown/newer `schema_version` rejects naming found vs supported versions; every envelope change bumps the version with a documented migration or an explicit break note. Wire containers stay snake_case; `snapshot_json` content follows feature 17's schema; block payload content follows feature 08's resume keys.
- **BR-02 Round-trip lossless.** Export -> fresh-account import -> re-export differs only in ids and timestamps; full snapshot history transfers; `published_version` re-resolves by version number (never by old id); blocks relink via `payload_json.documentId` matching a document's `export_id`. The one documented exception: a `public_conflict` landing (Q-01) legitimately changes `is_public` on re-export.
- **BR-03 Export freedom and audit.** Export of one's own data is unlimited and ungated; every run writes `audit_log{actor, action, target, at}` - exfiltration forensics start at the first download. Export of another user's data has no path: session `user_id` + RLS, no cross-profile parameter exists.
- **BR-04 Rejection writes zero rows.** Gate order before any write: size cap 2MB (`413 import_too_large`, pre-parse - parse bombs die at the gate) -> JSON parse (`422 import_malformed` with position) -> version gate (`422 import_version_unsupported`) -> closed-schema validation -> `https:` allowlist + sanitization. Failures leave zero rows on both services (count-asserted).
- **BR-05 No partial restores.** The write phase orders documents/snapshots first, block relink last; a block can never point at a missing document (relink requires the imported document to exist); a crash between phases is retry-safe - the dry-run summary states exactly what would be created, and retry completes the relink without duplicating (idempotent by envelope `export_id` within a run). Imports assemble in memory/transaction - staging tables do not exist.
- **BR-06 Public-landing rule.** On import: target has no public document -> honor `is_public` and re-resolve `published_version` by version number; target already has a public document -> conflicting documents land private with a `public_conflict` warning naming both; `is_public=false` imports always land private with `published_version=NULL` (unpublish invariant, feature 17).
- **BR-07 Adapters bootstrap drafts only.** LinkedIn (export file) and GitHub (public handle, no token v1) map into the resume schema as `is_public=false` drafts with `review_required` field paths; nothing renders publicly until explicit Save Snapshot + publish; provenance is visible in the editor (`Imported from {source} - review before saving`).
- **BR-08 Adapter safety.** Fetches are server-side with 10 s timeout, 3-redirect cap, 1MB response cap, private-range egress blocked (SSRF guardrails); no token inputs exist anywhere; adapter endpoints are Turnstile-protected and quota-bounded.
- **BR-09 Scope guard is structural.** The export query reads only `resume.documents/snapshots` + `profile_blocks WHERE type='resume'`; shop/leads/domain/product rows cannot appear because the query has no path to them (payload inspection in the drill).
- **BR-10 Fixture gate.** `fixture-resume.json` (beside the spec) is imported on an empty database on every build; a schema change that breaks it fails `./gradlew build` (migration or documented break, decided in the same change). The round-trip test uses fixture + one real resume.

## Constraints carried

- Imports respect platform invariants (single-public, versioning, linkage rules) - they never bypass them by writing around the service layer.
- Sanitization runs at import AND at capture (both layers): imported payloads are the adversary's cheapest path to stored XSS, and re-export must also show clean text.
- Quotas: imports + adapters share a per-user daily quota (20/day); export carries none (your data leaves freely).
- No pricing or plan values; portability is never gated.
