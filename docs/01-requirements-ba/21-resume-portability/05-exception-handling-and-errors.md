# Resume Portability - Exception Handling and Errors (feature 21)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/21-resume-portability/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| File over 2MB | API | `413 import_too_large` pre-parse; client pre-check mirrors it | "This file is larger than 2MB - backups are compact by design." |
| Malformed JSON | API | `422 import_malformed` with byte position; zero rows | "Couldn't read this file ({position}) - pick a different file." |
| Unknown/newer `schema_version` | API | `422 import_version_unsupported` naming found vs supported; zero rows | "Made with a newer app version (v{X}). Supported: v{Y}." |
| Unknown top-level keys | API | Closed-schema `422`; nothing silently dropped | "This file has unrecognized fields - it may be from a newer version." |
| Link URL with `http:`/`javascript:`/`data:` | API | `https:` allowlist; import rejected (not silently clamped at top level - per-field clamp only for cosmetic normalization) | "This file contains an unsupported link ({path})." |
| Script/iframe/event-handlers in strings | API | Sanitized at import AND capture; re-export shows stripped text; audited count | No visible error; content renders as text |
| Import while another import runs | API | Serialized per account; second request queues or `429` | "An import is already running - try again in a moment." |
| Daily quota hit (imports + adapters) | API | `429 import_quota_exhausted` (20/day shared) | "Daily import limit reached - try again tomorrow." |
| Block relink fails mid-restore | API | `503 import_unavailable`; private documents exist; retry completes relink idempotently - no duplicate documents, no orphan block | "Restore paused - retry to finish (your imported drafts are safe)." |
| Public conflict on restore | API | `public_conflict` warning in dry-run; conflicting docs land private; nothing overwritten | "You already have a public CV ({title}) - imported one lands private." |
| Adapter fetch fails (timeout/404/private IP) | API | Fail closed; `import.rejected{reason}` counted; no partial draft | "Couldn't fetch that profile - check the handle and try again." |
| LinkedIn file is not an export | API | `422 import_malformed` style messaging (mapping finds nothing) | "This doesn't look like a LinkedIn export - download it from LinkedIn Settings." |
| Export of another user's data | API | No path exists (session scoping); probes get `403` | "You don't have access to this data." |
| Session expires mid-wizard | UI | Uploaded file preserved client-side; re-submit post-login | "You were signed out - your file is still here." |
| Avatar expectations | UI | Stated once in the wizard | "Profile photos aren't included." |
| Staging table proposal | CI/review | Review fails: imports assemble in memory/transaction | Build/review error: "backup staging tables are forbidden" |
| Fixture breaks after a schema change | CI | `./gradlew build` fails at the fixture gate | Build error: "fixture-resume.json no longer imports on an empty database" |
| Secrets audit | Ops | Env/vault only; `.env` and overrides uncommitted; no secrets in logs/bundles | Ops-facing audit failure |
