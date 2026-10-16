# Resume Portability - Workflow and User Journey (feature 21)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/21-resume-portability/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/21-resume-portability/02-user-stories-and-business-rules.md`.

## Backup

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | `Download backup` (per document or whole library) | Streaming JSON download (`resume-backup-{date}.json`); toast states counts (`12 documents, 34 snapshots downloaded`) | BR-01, BR-03 |
| 2 | System | Export completes | Audit row written (actor, action, target) | BR-03 |

## Restore wizard

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Uploads a backup file | Client pre-checks extension + 2MB before a byte uploads; server gates run in order | BR-04 |
| 2 | System | Dry-run (`?dry_run=true`) | Summary: documents-to-create with titles, snapshot counts, warnings (unknown keys, clamped URLs, sanitized fields shown as reviewable warnings, not silent fixes), `public_conflict` list when applicable | BR-04, BR-06 |
| 3 | Job seeker | Confirms | One transaction: documents + snapshots (fresh ids) -> block relink -> `published_version` re-resolution; done screen with `Open` links + audit timestamp | BR-02, BR-05 |
| 4 | System | A validation failure occurs | Exact code + position where applicable; wizard resets to step A; zero rows exist server-side (count-asserted) | BR-04 |
| 5 | System | Relink phase fails (infrastructure) | Documents (private) exist; retry completes relink without duplicating; dry-run state re-checkable | BR-05 |

## LinkedIn adapter

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | New user | Uploads a LinkedIn export file (`Most reliable` path; no URL tab - Q-02) | `POST import/linkedin` (Turnstile) | BR-07, BR-08 |
| 2 | System | Mapping completes | `ImportReview` shows mapped positions/education/skills with `Needs review` flags on every adapter-filled field | BR-07 |
| 3 | New user | `Create draft` | Draft document (`is_public=false`, no published snapshot); editor opens with provenance banner (`Imported from LinkedIn - review before saving`) | BR-07 |
| 4 | New user | Reviews and saves | Standard Save Snapshot path; nothing public until save + publish | BR-07 |

## GitHub adapter

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | New user | Enters a handle (`^[a-zA-Z0-9-]+$`, validated client- and server-side) | Server fetches public repos/profile (timeout/redirect/cap guards; progress `Fetching public repos...` stated upfront) | BR-08 |
| 2 | System | Mapping completes | Pinned/top repos + bio map into projects/links as an `ImportReview` draft with review flags | BR-07 |
| 3 | New user | `Create draft` | Same draft landing + provenance banner (GitHub) | BR-07 |
| 4 | Attacker | Attempts SSRF (private-range URL, redirect chains) | Fetch fails closed; abuse counted (`import.rejected{reason}`); no token input exists to steal | BR-08 |

## Fixture gate (developer workflow)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Developer | Changes the schema or envelope | Same change updates the fixture path or documents the break | BR-10 |
| 2 | CI | Every build | Fixture imported on an empty database; round-trip test (fixture + one real resume) diffs ids/timestamps only; failure fails the build | BR-02, BR-10 |

## Privacy probes

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | User A | Requests export/import against user B's profile | `403`; no cross-profile parameter exists | BR-03 |
| 2 | Attacker | Uploads a file with script payloads, oversized bodies, or newer versions | Rejected or sanitized per the gate order; zero rows on rejection; re-export shows stripped text | BR-04 |
| 3 | Auditor | Inspects an export payload of a profile with shop/leads/domain rows | Zero non-resume data present (structural scope guard) | BR-09 |
