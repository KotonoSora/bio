# Public Profile Backend Plan (identity + visibility — server build steps)

PLAN ref: P1a-2 (profile row, reserved slugs, visibility) + P1a-3 (username-change 301, free one-profile) + S-1/S-2/S-5/S-6/S-7. BA source: `docs/link-to-bio/profile/public-profile.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B0). UI contract in `docs/link-to-bio/profile/public-profile-frontend-plan.md` (screens call the endpoints below; error-code mapping lives there).

## Purpose

Give the backend developer an ordered, no-decisions-left procedure for profile identity: schema from the database plan first, then entities, repositories, policy, service, controller, security, audit, observability, tests, container. Follow steps 1–11 in order; each step's done-criteria gates the next. Frontend behavior is referenced, never redefined — this file owns the server truth.

## Users

- Backend developer: executes steps 1–11 against the `bio` Gradle module.
- QA: runs the drill commands in Acceptance criteria against PG `18.6`.
- Reviewer: checks each step's artifact (SQL file, class, test) exists as named.

## Functional requirements

1. Stack and topology (mandatory): PostgreSQL LTS `18.6` + Spring Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith, package `com.app.bio.profile`, schema `bio`. Monolith pros: claim/rename/toggle share one transaction with the redirect insert; joins `users → profiles` stay local; one RLS set; one audit log. Microservices cons avoided: a profile service would distribute rename atomicity (profile + redirect + purge) and widen the IDOR surface with no ≥10x asymmetry (reads-vs-writes asymmetry is solved by cache in `docs/link-to-bio/public-page/share-seo-backend-plan.md`). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters used here: web (MVC), validation, data-jpa, security, flyway, actuator, micrometer-registry-prometheus. No new starters.
2. Step 1 — create the schema once from `docs/link-to-bio/profile/public-profile-database-plan.md`: run its single baseline script (Flyway executes it on an empty database; there are no incremental versions and no ALTER chains because there is no existing database to migrate). Done when the script applies clean on empty PG `18.6` and `\d bio.profiles` lists both RLS policies.
3. Step 2 — entities in `entity/`: `ProfileEntity` (`@Entity @Table(name="profiles", schema="bio")`, fields `id: String @Id`, `userId: String @Column(name="user_id")`, `username: String`, `displayName`, `bio`, `avatarKey: String?`, `themeId`, `isPublic: Boolean`, `showResume: Boolean`, `seoTitle: String?`, `seoDesc: String?`, `createdAt/updatedAt: Instant`) and `UsernameRedirectEntity` (`oldUsername @Id CITEXT`, `newUsername`, `createdAt`). No validation annotations on entities — validation lives on DTOs.
4. Step 3 — repositories in `repository/`: `ProfileRepository : JpaRepository<ProfileEntity, String>` with `findByUserId(userId: String): ProfileEntity?`, `findByUsername(username: String): ProfileEntity?` (public-read path only), `existsByUsername(username: String): Boolean`; `UsernameRedirectRepository` with `findByOldUsername(old: String): UsernameRedirectEntity?`. Owner reads add explicit `@Query("… WHERE p.userId = :userId …")` — RLS is the second layer, never the only one. Statement timeout 5s via datasource config.
5. Step 4 — `service/UsernamePolicy.kt` (pure object, no Spring): `normalize(raw)` = trim → NFKC fold → lowercase; then checks in fixed order length 3–30 → charset `^[a-z0-9_]+$` → reserved set (`app,api,admin,static,r,cv,shop,login,signup,pricing,blog,go,a`) → availability via `existsByUsername`. Returns `Ok(normalized)` or `Err(code)` with codes `username_too_short|username_too_long|username_bad_chars|username_reserved|username_taken`. Unit table of 40 cases (done-criteria: all green, including raced-taken mapped from constraint violation, never a 500).
6. Step 5 — `service/ProfileService.kt`: `getMine(userId)` (single owner-scoped read); `updateMine(userId, dto)` = sanitize (`HtmlSanitizer.strip` on displayName/bio/seo; reject `javascript:` links) → Bean-Validation lengths → save → `audit(Target.profile)` → `events.publish(ProfilePublished(username))`; `checkUsername(raw)` = normalize + policy, no write; `changeUsername(userId, newRaw)` `@Transactional` = `SELECT … FOR UPDATE` lock profile → re-run policy (state may have changed) → `INSERT redirect(old,new)` → `UPDATE profile.username` → audit → publish event. Old names are never deleted (permanent reservation). Second profile per `user_id` → `422 profile_limit_reached` (plan gate server-side).
7. Step 6 — `api/ProfileController.kt` + `api/ProfileDto.kt`: `GET /api/v1/profile` → 200; `PUT /api/v1/profile` (`@Valid UpdateProfileRequest`) → 200 / 422; `POST /api/v1/profile/username-check` → 200 `{username, available, reason?}` (never creates); `POST /api/v1/profile/username-change` → 200 / 422. `userId` comes from the server session only (method arg resolver, never request body). `@ControllerAdvice` maps violations to RFC 9457 `ProblemDetail` `{title, detail, code}`; cross-user → 403; anonymous private read → 404 (existence oracle closed).
8. Step 7 — security wiring: request filter sets `app.user_id` from session before any repository call; Spring Security config requires auth on `/api/v1/profile*`, CSRF tokens for dashboard POST/PUT (SPA sends the `X-CSRF-TOKEN` header), rate-limit interceptor (cache-layer token bucket) on check/change endpoints (S-5); Turnstile verified on claim/change. Secrets via env/vault only.
9. Step 8 — side effects: audit writes are synchronous inside the service transaction (append-only `audit_log`); cache purge is an `@Async @EventListener(ProfilePublished)` calling `CachePort.purge` (purge mechanics owned by the share-seo plan). Micrometer `@Timed` on `changeUsername`/`updateMine`; Actuator health includes the `db` indicator; Flyway version logged at boot.
10. Step 9 — tests (all in `./gradlew build`): `UsernamePolicyTest` (40-case table); `@WebMvcTest ProfileControllerTest` (each 422 code, 403 cross-user, CSRF rejection); Testcontainers PG `BioProfileIT` (constraint names on bypass, RLS deny as `app_role` with foreign `app.user_id`, concurrent double-claim → exactly one winner); ArchUnit (no `com.app.bio.profile` outgoing deps except ports; no `customCss|fontUrl` field names anywhere).
11. Step 10 — container: multi-stage build (Gradle → Temurin JRE, non-root), tag `:<semver>-<sha>`; `docker run` and `podman run` reach ready against empty PG `18.6` (baseline schema scripts applied once by Flyway, zero manual SQL); previous-tag rollback green per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: owned entirely by `docs/link-to-bio/profile/public-profile-database-plan.md` — the single `CREATE TABLE` for `bio.profiles` + `bio.username_redirects` with CHECKs, indexes, and RLS, written once and never duplicated here. The entities in step 3 map 1:1 to those tables; the endpoints below are the only writers.
- Endpoints (`/api/v1`, cookie session): `GET /api/v1/profile` → `200 {id, username, displayName, bio, avatarKey, themeId, isPublic, showResume, seoTitle, seoDesc}`; `PUT` req `{displayName, bio, isPublic, showResume, seoTitle, seoDesc}` → `200` / `422 {code: display_name_too_long|bio_too_long|seo_too_long}`; `POST username-check` req `{username:"Alice!"}` → `200 {username:"alice!", available:false, reason:"username_bad_chars"}`; `POST username-change` req `{newUsername:"alice-dev"}` → `200 {username:"alice-dev"}` / `422 {code}`.
- Public read contract (served by the share-seo plan, constrained here): anonymous `GET /:username` = `profiles WHERE username=:u AND is_public=1` else 404; `GET /:old` = redirect lookup → 301; private rows never cached publicly.

## Acceptance criteria

- Identity drill: `curl -b session …/username-check` for uppercase/duplicate/reserved/short/bad-chars returns each exact `422 code`; direct SQL bypass violates the named DB CHECK/unique (asserted in `BioProfileIT`).
- Rename drill: `POST username-change {"newUsername":"alice-dev"}` → 200 in one transaction; `GET /alice` → 301; re-claim `alice` → `username_taken`; concurrent double-claim → one 200, one 422.
- Visibility drill: `isPublic=false` → anonymous `GET /:username` 404 + sitemap absence; dashboard `GET /api/v1/profile` still 200.
- Security drill: user A on user B rows → 403; `psql "SET app.user_id='A'" SELECT * FROM bio.profiles` returns zero B rows; XSS payloads stored stripped; burst check/change throttled; secrets grep clean.
- Contract drill: OpenAPI diff additive-only; problem JSON per code; audit row per mutation; purge listener invoked (mock verified).
- Container drill: same digest under Docker AND Podman, compose cold boot green, rollback green, full `./gradlew build` green.

## Out of scope

- Table definitions and RLS DDL (`docs/link-to-bio/profile/public-profile-database-plan.md`); UI screens and flows (`docs/link-to-bio/profile/public-profile-frontend-plan.md`); block CRUD (BA `docs/link-to-bio/blocks/link-blocks.md`); themes/avatar bytes (BA `docs/link-to-bio/appearance/themes-avatar.md`); badge/followers (BA `docs/link-to-bio/profile/verification-followers.md`); SSR/cache mechanics (share-seo plan); beacon/aggregates (beacon plan in `docs/link-to-bio/analytics/profile-analytics-backend-plan.md`); shop/leads/domains; P4/P5.

## Open questions

- Q1: Second profile on pro — keep unique index on `user_id` for v1 or allow N profiles now?
- Q2: Permanent username reservation confirmed, or time-boxed release with a purge job?
