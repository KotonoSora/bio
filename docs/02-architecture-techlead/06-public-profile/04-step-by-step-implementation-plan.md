# Public Profile - Step-by-Step Implementation Plan (feature 06)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/06-public-profile/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Gradle module `profile-service` (wrapper 9.8.1, Kotlin 2.4.21, Boot 4.1.1, version catalog) | platform baseline | `./gradlew build` green from a clean checkout |
| T-02 | DevOps | Compose slice: `profile-service` + `profile-db` on the platform network; env contract; Podman parity | T-01 | `docker compose up` and `podman compose up` cold-boot to healthy |
| T-03 | BE | Flyway `V1__profile_baseline.sql` (schema, tables, CHECKs, indexes, RLS) | T-02 | Drills 1-5 of `02-database-schema-design.md` pass |
| T-04 | BE | Entities + repositories (`ProfileEntity`, `UsernameRedirectEntity`; `findByUserId`, `findByUsername`, `existsByUsername`, `existsByOldUsername`) | T-03 | RLS drill: cross-owner visibility denied |
| T-05 | BE | `UsernamePolicy`: normalize (trim, NFKC, lowercase) -> length -> charset -> reserved -> retired -> taken; 40-case table | T-04 | All 40 cases green; raced-taken maps from constraint violation, never a 500 |
| T-06 | BE | `ProfileService.updateMine` + `getMine`: sanitize (HTML strip, `javascript:` reject) -> validate -> save -> audit -> event | T-05 | AC-08 green; `Saved` flow audited |
| T-07 | BE | Claim path: create on first `PUT`; `profile_limit_reached` guard; Turnstile verify | T-06 | AC-01, AC-05 green |
| T-08 | BE | Rename transaction: `SELECT ... FOR UPDATE` -> re-run policy -> insert redirect -> update username -> audit -> event | T-07 | AC-03, AC-04 green incl. concurrent double-rename |
| T-09 | BE | Controllers + DTOs + `@ControllerAdvice` (problem codes per `03-api-contracts-and-payloads.md`) | T-08 | Contract tests: each `code` + status exact |
| T-10 | BE | Internal read API: `GET /internal/v1/profiles/{username}` resolve + `profiles:public` list | T-09 | AC-11 green; `missing` covers private with equal shape/timing |
| T-11 | BE | Publication: emit `profile.published.v1` / `username.renamed.v1` after commit | T-10 | AC-09 green (purge fresh within 2 min; TTL-only path also passes) |
| T-12 | DevOps | Gateway rules: rate limits + Turnstile hooks; JWT issuance for internal calls | T-09 | AC-01 family rate drill: `throttled` + `Retry-After` correct |
| T-13 | BE | Drill suite: unit + Testcontainers integration + AC-01..AC-12 executable; ArchUnit (no client regex, no outgoing deps beyond ports) | T-03..T-12 | All ACs green in the CI log |
| T-14 | FE | `/app/profile` route + shell: load flow, form, save bar, field errors, skeleton, offline/session states | T-09 | AC-10 green; load drill renders owner data only |
| T-15 | FE | Rename wizard: debounce 300 ms, normalized echo, verdicts, confirm screen (301 + reservation copy), raced-taken return | T-14 | AC-03 UI path green; wizard copy exact |
| T-16 | FE | Visibility toggle + purge chip + first-run claim variant | T-14 | AC-06, AC-09 UI paths green; rollback on failed PUT |
| T-17 | BE | Observability: metrics + tracing + structured logs per `05-security-logging-and-rate-limiting.md` | T-06 | Dashboards show update latency + reject reasons |
| T-18 | DevOps | Image + release gate: digest pin, SBOM, rollback, blue/green deploy | T-13, T-16 | `docker run` + `podman run` cold boot; rollback drill <= 10 min |

Definition of done: AC-01..AC-12 green, five schema drills green, release gate passes, legacy public-profile docs deleted (git history is the archive).
