# Resume Portability - Context and Scope (feature 21)

Phase 1 BA · Feature 21 of 53 (resume, final) · Implemented by `docs/02-architecture-techlead/21-resume-portability/` (5 files + `fixture-resume.json`); extends `resume-service` (export, import, adapters). No new tables, no new services.

## Purpose

Guarantee a user can back up, restore, and bootstrap a resume without lock-in: JSON export/import of resume documents (plus their linked `resume` block rows) and one-shot adapters from LinkedIn/GitHub into the resume schema as drafts. A checked-in fixture proves schema changes stay migratable - every build imports it on an empty database. Scope guard is structural: this feature touches resume documents, their snapshots, and `resume`-type blocks only; nothing else can appear in an export because the query has no path to it.

## Objectives

- O-1 Round-trip lossless: export -> fresh-account import -> re-export is semantically identical excluding ids and timestamps; full version history survives moves, not just current state.
- O-2 Hostile until proven: an import file is treated as adversarial - size gate before parse, closed schema, version gate, `https:` allowlist, sanitization at import and again at capture; rejections write zero rows (count-asserted).
- O-3 No partial restores: validation and dry-run complete before any row exists; the write phase cannot leave a block pointing at a missing document (documents land first, blocks relink last, retry-safe).
- O-4 Adapters bootstrap, never publish: LinkedIn/GitHub imports land as drafts with `review_required` flags; nothing reaches the public CV until explicit Save Snapshot + publish.
- O-5 Export freedom: your data leaves freely - no quotas, credits, or gates on export; every export is audited (exfiltration forensics).

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Job seeker | Full read: own export; Create: imports into own account (session `user_id` + RLS) | Downloads a backup, restores after accidental delete, moves between accounts. |
| New user | Create: adapter drafts into own account | Bootstraps a first resume from LinkedIn export data or a GitHub handle. |
| Developer (internal) | Read: fixture | Runs the fixture through migrations; a break fails the build. |
| Attacker (adversarial) | none | Finds no IDOR path, no parse-bomb hole, no SSRF egress, no stored-XSS vector, no staging-table target. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Public landing on restore | Import honors `is_public` + re-resolves `published_version` by version number only when the target account has no public document (fresh-account move); otherwise conflicting documents land private with a `public_conflict` warning naming both - no silent overwrite, no silent loss. |
| Q-02 LinkedIn URL input | Deferred: v1 accepts the LinkedIn export file only (URL scraping carries ToS risk with no compensating value). DEVIATION FLAGGED: the legacy frontend described a paste-URL tab; it is not built (amendment required to enable). |
| Q-03 Avatar in exports | Excluded entirely: avatars are profile assets (feature 09), not resume scope - carrying an avatar key would point across service namespaces and cannot resolve for a fresh account. DEVIATION FLAGGED vs the legacy `avatar_key` reference decision; the wizard states "Profile photos aren't included". |
| Q-04 Fixture location | Beside the spec in the numbered tree: `docs/02-architecture-techlead/21-resume-portability/fixture-resume.json` (discoverable where the contract lives; no shared fixtures root to drift). |
| Q-05 In-place overwrite import | Never in v1: imports create new rows only (copies-only); merge/overwrite requires a flagged amendment. |
| Q-06 Adapter mapping preview | Whole-draft accept with inline `review_required` flags (no per-field checkboxes in v1). |

## In scope

- Export envelope (closed, versioned) + streaming download; import with size/parse/version/schema gates, dry-run summary, atomic confirm; LinkedIn file-import and GitHub handle-import adapters; the fixture + its CI gate.
- Audit rows for every export/import/adapter run; SSRF guards on adapter fetches; abuse controls (Turnstile, per-user daily quota, parse timeouts).

## Out of scope

- Full profile+blocks backup, shop/course/tip/form blocks, `products`, `leads`, `custom_domains` exports (their majors; the scope guard is structural); snapshot versioning semantics (17); template rendering (18); share links (20); continuous sync (one-shot v1 only); scheduled/emailed backups (an automation-batch amendment); paid delivery.

Related: rules in `docs/01-requirements-ba/21-resume-portability/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/21-resume-portability/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/21-resume-portability/05-exception-handling-and-errors.md`.
