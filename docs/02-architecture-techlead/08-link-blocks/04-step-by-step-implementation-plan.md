# Link Blocks - Step-by-Step Implementation Plan (feature 08)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/08-link-blocks/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Flyway `V5__blocks_baseline.sql` (table, FK cascade, both CHECKs, two indexes, `b_owner` + `b_public`, grants) | feature 06/07 modules | Drills 1-5 of `02-database-schema-design.md` pass | 
| T-02 | BE | `BlockEntity` (maps DDL 1:1, JSONB user-type) + `BlockType` closed enum (unknown string -> 422 before DB) + repository (ordered list, max position, batch position update) | T-01 | Bad-type insert fails only at CHECK when bypassing service - proven in IT | 
| T-03 | BE | `BlockPayloadValidator`: per-type schema table (link/social/embed/resume/gallery/typed rows), sanitizer integration, codes `unknown_block_type`, `bad_url_scheme`, `title_too_long`, `payload_too_long`, `bad_schedule_window` | T-02 | Validator table test: every type x valid/invalid green | 
| T-04 | BE | `BlockService` CRUD: ownership assert first (403, never filter by profile alone), create `max+1`, duplicate `position+1` + batch shift, patch merge + re-validate, delete | T-03 | Service + `@WebMvcTest` green per code | 
| T-05 | BE | `reorder` `@Transactional`: profile row lock (serializes per profile), set-equality assert (`422 reorder_mismatch`, zero writes), positions 0..n, one audit + one event | T-04 | AC-01 + AC-13 green; concurrency IT: no duplicate positions | 
| T-06 | BE | `BlockController` + DTOs per contract + `@ControllerAdvice` (RFC 9457) + computed `hiddenReason` (`visible|hidden|scheduled_future|expired`) | T-05 | Every code contract-tested exactly | 
| T-07 | BE | Internal windowed read `GET /internal/v1/profiles/{username}/blocks`: `b_public` + explicit window predicate, order `position ASC, id ASC`, `profile_absent` parity for private/suspended/missing | T-06 | AC-10 green; one call serves SSR | 
| T-08 | BE | Security wiring: sanitizer on every payload string, CSRF on mutations, owner RLS baseline, rate limits per `05-security-logging-and-rate-limiting.md` | T-06 | AC-03/AC-04/AC-05 drills green | 
| T-09 | BE | Side effects: synchronous audit row per mutation; `@Async` `profile.published.v1` after commit (single event per reorder); `@Timed` on reorder | T-05 | AC-12 green (TTL-only path also passes) | 
| T-10 | BE | Drill suite: Testcontainers IT (bypass CHECK, RLS deny, concurrent reorders, cascade), ArchUnit module boundary, OpenAPI additive diff | T-07..T-09 | AC-01..AC-13 green; `./gradlew build` green | 
| T-11 | FE | `/app/blocks` route: load in server order, status chips with dates, `Out of window` collapsed section, empty state, per-row spinner | T-06 | List/create drill steps green | 
| T-12 | FE | Type picker (13 frozen options) + per-type dialogs + `BlockSchema.ts` mirror (https rule, caps; same messages as server) + schedule section with live state label | T-11 | Create + schedule drills green; no free-text type (grep gate) | 
| T-13 | FE | Drag reorder: optimistic move, queued single in-flight commit, rollback + `Reload` toast on `reorder_mismatch`, keyboard move-up/down controls + ARIA announcements | T-12 | Reorder drill incl. forced stale commit rollback green | 
| T-14 | FE | Hide/delete/duplicate flows: optimistic hide with rollback, confirm naming the block, 5 s undo re-POST (new row, honest copy), duplicate below source | T-13 | Delete drill green | 
| T-15 | BE | Observability: metrics `blocks.write.total{op}`, `blocks.reorder.latency`, `blocks.reorder.mismatch.total`, `blocks.limit_reached.total`, `blocks.sanitized.total`; structured logs without payload bodies | T-09 | Dashboards + alerts live | 
| T-16 | DevOps | Image + release gate: digest pin, SBOM, compose slice unchanged, blue/green deploy, rollback drill | T-10, T-14 | `docker run` + `podman run` cold boot; rollback <= 10 min | 

Definition of done: AC-01..AC-13 green, five schema drills green, reorder concurrency IT green, release gate passes, legacy link-blocks docs deleted (git history is the archive).
