# Resume Format Sharing Backend Plan (authoring + scrub + clones — server build steps)

PLAN ref: P2-1 extension (form-based builder, immutable versions, clone-only reuse, report/takedown, unlimited preview/print/clone) + S-1/S-2/S-4/S-5/S-7. BA source: `docs/resume-builder/templates-print/resume-format-sharing.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Schema in `docs/resume-builder/templates-print/resume-format-sharing-database-plan.md` (formats + clones, written once). Builder/gallery screens in the format-sharing frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for community formats: closed-schema authoring, privacy-scrubbed publishing with verification re-scans, clone isolation, attribution, and moderated takedown. Follow steps 1–8 in order. Layout only, always — a personal-data key reaching a published row is the system's cardinal failure.

## Users

- Backend developer: executes steps 1–8 in package `com.app.resume.formats`.
- QA: runs scrub, placeholder, attribution, clone, IDOR, XSS, abuse, and secrets drills.
- Format author / reuser / moderator (via UI): build, reuse, and police formats.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`resume` module). Monolith pros: publish (validate + scrub + insert + audit) commits atomically; clone (read + copy + count) shares the RLS baseline; one audit log for the moderator trail. Microservices cons avoided: a format service would split publish atomicity and add gallery-auth plumbing for catalog-volume traffic. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: gallery reads are public-predicate index scans + short edge cache (anonymous bursts absorbed by cache + throttle, not PG); writes are author-paced.
2. Step 1 — schema: run the format-sharing database plan script once. No other DDL in this slice.
3. Step 2 — `service/FormatValidator.kt` (pure, table-tested): closed layout schema `{sections order/visibility, per-section layout variant, tokens{font, scale, accent, density}, A4 rules{margins, page-break hints}}` — allowlist keys only; snapshot data keys (experience text, contact details, links), inline `style`, scripts, external URLs → reject with `format_personal_data|format_bad_style|format_bad_script|format_bad_url`. No per-user CSS/JS can pass by construction (no field carries it).
4. Step 3 — draft CRUD: `GET/POST /api/v1/resume-formats` (mine), `PUT /:id` — drafts editable freely, validated on every write (validator runs on draft saves too, so drafts are always publishable-shaped).
5. Step 4 — `service/PublishService.kt#publish` `@Transactional`: re-validate payload → scrub proof (independent re-scan pass over the exact bytes to be stored, result recorded) → `INSERT formats(slug, version = max+1)` (never UPDATE a published row) → audit `{actor, action: publish, target}` → emit gallery-purge event. `unpublish` flips status (clones unaffected — proven by copy-not-pointer). Publish attempted with personal keys/styles/scripts → rejected, zero rows published.
6. Step 5 — `POST /:id/clone`: assert `status=published` → copy `layout_json` into the reuser's template set (their data from this instant) → insert clone row (idempotent: already-cloned → `200 {already:true}`, no duplicate) → audit. Later author edits/unpublish/takedown cannot alter the copy (no live reference exists — asserted by the clone drill, not by policy text).
7. Step 6 — moderation: `report-abuse` on every format (Turnstile-protected, reporter-agnostic) → ops queue; `POST /api/v1/ops/resume-formats/:id/remove` (`ROLE_OPS`, mandatory reason) → status `removed` → gallery-wide hide within purge SLA → audit with actor + reason. Reporter identity never exposed to the author.
8. Step 7 — verification job `FormatRescanJob` `@Scheduled(cron="0 40 4 * * 0")`: weekly re-scan of all `published` rows against the personal-data key list; detection → auto-flag for review + metric `formats.rescan_hit` (quarantine decision stays human — job flags, reviewer removes). Thumbnails: 2MB + magic-byte + random keys (avatar-baseline mechanics reused); placeholder-data renders only (author preview and gallery thumbnails render the fictional placeholder even when the author owns a full resume — enforced server-side by rendering with the placeholder fixture, never the author's snapshot).
9. Step 8 — security/abuse/tests/container: owner scoping + RLS (S-1/S-6, public gallery anonymous within rate limits); strip + CSP on builder/gallery/preview/cv surfaces (S-2); Turnstile + WAF/rate on gallery/clone/report paths (S-5); secrets via env only; attribution rule (gallery/detail/`based-on` lines show username + profile link; rename follows 301; deleted accounts freeze the non-linked former-username string). Tests: scrub matrix, placeholder test, attribution test (rename + deletion), clone-isolation test, IDOR/XSS/abuse/secrets probes. Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (only writer here). Endpoints: `GET/POST /api/v1/resume-formats` (mine); `GET /api/v1/gallery/formats` (public list + detail, rate-limited); `PUT /:id`; `POST /:id/publish|unpublish`; `POST /:id/clone` → `201` / `200 {already:true}`; ops `POST /api/v1/ops/resume-formats/:id/remove` (audited); `POST /:id/report` (Turnstile). Thumbnails: blob random keys, placeholder renders only.

## Acceptance criteria

- Scrub drill: hostile publishes rejected with zero rows; weekly re-scan finds zero personal-data keys in published rows.
- Placeholder drill: author/gallery renders contain fictional content only, despite a populated author resume.
- Attribution drill: cards/detail/`based-on` correct; rename follows 301; deletion freezes the string.
- Clone drill: clone renders snapshot text in cloned layout; author-side changes never propagate; RESTRICT blocks hard-delete with clones.
- Security drill: cross-user 403 + RLS deny; injections stripped on all four surfaces; bursts throttled; uploads validated; secrets clean.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); builder/gallery screens (frontend plan); snapshot versioning (snapshot backend plan); built-in registry (templates backend plan); share links (sharing plans); backup/adapters (portability plans); shop/leads/domains; AI tailoring (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Moderation at launch — pre-publish queue or publish-then-report (see database plan Q1)?
- Q2: Version upgrade offers to reusers, or silent pin (BA Q2)?
