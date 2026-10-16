# Short Links - Step-by-Step Implementation Plan (feature 29)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Acceptance criteria cited from `docs/01-requirements-ba/29-short-links/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Gradle module `link-service` (wrapper 9.8.1, Kotlin 2.4.21, Boot 4.1.1, version catalog) | platform baseline | `./gradlew build` green from a clean checkout |
| T-02 | DevOps | Compose slice: `link-service` + `links-db` on the platform network; env contract; Podman parity | T-01 | `docker compose up` and `podman compose up` both cold-boot to healthy |
| T-03 | BE | Flyway `V1__short_links_baseline.sql` (schema + table + checks + index + RLS) | T-02 | Drills 1-4 of `02-database-schema-design.md` pass on a fresh 18.6 container |
| T-04 | BE | Entities + repository (`ShortLinkEntity`, `findByCode`); request filter sets `app.user_id` from gateway identity | T-03 | RLS drill: cross-owner visibility denied |
| T-05 | BE | `CodeGenerator`: CSPRNG base62, >= 7 chars, collision-safe insert helper | T-04 | 10,000 samples: no duplicates, no sequential pattern |
| T-06 | BE | Mint endpoint: validation, blocklist check (fail closed), quota counter, Turnstile verify, bounded retry | T-05 | AC-03 and AC-06 green |
| T-07 | BE | Quota headers on mint + list; reset computed from the period key | T-06 | AC-11 green; header values match the Redis counter |
| T-08 | BE | Resolve: cache-first, PK fallback, blocklist re-check, state machine 302/301/410/403/404 | T-04 | AC-02 and AC-12 green; zero database writes on resolve (query log) |
| T-09 | BE | PATCH + DELETE (immutable-field guard; tombstone = `expires_at = now()`) | T-06 | AC-10 green |
| T-10 | BE | Ops: report queue + suspend endpoint + audit + `link.suspended.v1` purge event | T-08 | AC-04 and AC-05 green |
| T-11 | BE | Hourly expiry sweep + resolve-time expiry computation (both agree) | T-08 | `links.sweep.lag` <= 60 min; no expired link serves 302 |
| T-12 | BE | Hit-event producer -> `links.hits` queue, after the response | T-08 | 1,000/1,000 events (AC-02); p95 delta <= 5 ms |
| T-13 | BE | Security config: JWT resource server (internal JWT), ownership checks, deny-by-default | T-06 | AC-07 green; unsigned or direct calls 401 |
| T-14 | DevOps | Gateway rules: rate limits + WAF on resolve/mint/report; audited ops bulk-mint job | T-13 | AC-06 green; bulk job writes an audit line |
| T-15 | BE | Observability: metrics + tracing + structured logs per `06-security-logging-and-rate-limiting.md` | T-08 | Dashboards show resolve p95 and cache hit ratio |
| T-16 | BE | Drill suite: unit + Testcontainers integration + AC-01..AC-12 executable | T-06..T-15 | All ACs green in the CI log |
| T-17 | FE | `/app/links` route: table, status chips, quota meter, empty state | T-07 | Renders from the live endpoint; header-driven meter (AC-11) |
| T-18 | FE | Mint dialog: validation, alternatives on `code_taken`, quota-disabled state | T-17 | AC-01 and AC-11 UI paths green |
| T-19 | FE | Edit, tombstone confirm, copy, Stats/QR handoff links (features 30/31) | T-17 | AC-10 UI path green |
| T-20 | DevOps | Image + release gate: digest pin, SBOM, rollback, blue/green deploy | T-16, T-19 | `docker run` + `podman run` cold boot; rollback drill <= 10 min |

Definition of done: AC-01..AC-12 green, four schema drills green, release gate passes, legacy short-links docs deleted (git history is the archive).
