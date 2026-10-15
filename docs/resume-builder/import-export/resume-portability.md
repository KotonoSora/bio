# Resume Portability (backup + adapters)

PLAN ref: P2-5 scoped to resume. Full-profile export covering shop/leads/domains belongs to their own majors.

## Purpose

Guarantee a user can back up, restore, and bootstrap a resume without lock-in. Covers JSON export/import of resume documents (plus their linked `resume` block payload) and one-way adapters from LinkedIn/GitHub into the resume schema. Proves schema changes stay migratable via a checked-in fixture.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Job seeker | Export/import own resume documents and linked resume blocks (calls scoped by session `user_id` + RLS) | Downloads a backup, restores after accidental delete, moves between accounts |
| New user | Import via LinkedIn/GitHub adapters into own draft resume (server-side fetch, no third-party tokens on client) | Bootstraps a first resume from LinkedIn or GitHub profile data |
| Developer | — (reported: no access-control statement in this file) | Runs the fixture profile through migrations to catch breaking schema changes |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | download a backup, restore after accidental delete, and move between accounts | I avoid lock-in | High |
| US-02 | New user | bootstrap a first resume from LinkedIn or GitHub profile data | I can start without manual entry | High |
| US-03 | Developer | run the fixture profile through migrations | breaking schema changes are caught | High |

## Functional requirements

1. Dashboard export: download JSON containing `documents[]` (with `template_id`, `snapshot_json`) + linked `resume`-type block rows. Import: upload the same file, validate schema version, create new rows (never overwrite in place without confirm).
2. Round-trip lossless: export → fresh account import → re-export produces semantically identical JSON (ids and timestamps excepted).
3. LinkedIn adapter: paste profile URL or upload LinkedIn export; maps positions/education/skills into resume schema as draft (requires user review before Save Snapshot).
4. GitHub adapter: fetch public repos/profile (handle only, no token v1); maps pinned/top repos + bio into projects/links section as draft.
5. Versioned schema (`schema_version` field in export). Unknown/newer version import is rejected with a clear error, never partially applied.
6. Fixture profile: a minimal `fixture-resume.json` checked into the repo; every schema change must keep the fixture importable (migration or documented break).
7. Scope guard: this doc covers resume documents + `resume` blocks only. Import/export of `product/course/tip/form` blocks, `products`, `leads`, and `custom_domains` is defined in their own majors and must not be smuggled in here.

## Data / API touches

- No new tables. Reuses `documents` and `profile_blocks(type=resume)` from `documents/resume-snapshot.md`.
- Endpoints (same JSON/cookie/`user_id`-scoped contract): `GET /api/v1/export/resume` (download), `POST /api/v1/import/resume` (validated dry-run + confirm), `POST /api/v1/import/linkedin`, `POST /api/v1/import/github`.
- Validation: size cap (e.g. 2 MB), JSON schema check, `https:` URL allowlist for imported links, HTML sanitization on all imported strings. Adapter fetches are server-side; client never sees third-party tokens.
- Audit: record import/export events in `audit_log{actor, action, target, at}` per security baseline.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Round-trip test on fixture plus one real resume: re-export diff shows only ids/timestamps changed | an exported resume file | imported into a fresh account and re-exported | the diff shows only ids/timestamps changed |
| AC-02 | Malformed, oversized, or newer-version file: rejected with actionable error; zero rows written (atomic import) | a malformed, oversized, or newer-version file | import is attempted | it is rejected with an actionable error and zero rows are written |
| AC-03 | LinkedIn/GitHub import creates draft only; no public `/:username/cv` change until explicit Save Snapshot + publish | a LinkedIn/GitHub import | the import completes | it creates a draft only with no public `/:username/cv` change until explicit Save Snapshot + publish |
| AC-04 | IDOR probe: user A cannot export/import against user B `profile_id`; all calls scoped by session `user_id` + RLS | user B `profile_id` | user A attempts export/import against it | access is denied with all calls scoped by session `user_id` + RLS |
| AC-05 | XSS probe: malicious strings in imported payload render stripped on dashboard preview, public CV, and re-export | malicious strings in an imported payload | rendered on dashboard preview, public CV, and re-export | they render stripped |
| AC-06 | Fixture import passes on a clean database after each schema migration | a clean database after a schema migration | the fixture is imported | the import passes |

## Out of scope

- Snapshot semantics and template rendering (see sibling docs).
- Full profile+blocks backup, shop `products`, course lessons/progress, `leads` CSV export, custom domains (separate majors).
- Continuous sync with LinkedIn/GitHub (one-shot import v1 only).
- Paid digital-product delivery via `orders{product_id}` (shop major + P3 billing).

## Open questions

- Q1: Include avatar image bytes in resume export, or reference `avatar_r2_key` and require separate re-upload?
- Q2: Fixture location — `docs/resume-builder/import-export/fixture-resume.json` or a shared `fixtures/` root?
