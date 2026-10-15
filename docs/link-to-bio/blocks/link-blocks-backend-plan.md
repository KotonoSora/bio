# Link Blocks Backend Plan (CRUD, ordering, scheduling — server build steps)

PLAN ref: P1b-1 (frozen 13-type superset) + P1b-2 (CRUD, drag-order, sizes, carousel, thumbnails) + S-1/S-2/S-6/S-7. BA source: `docs/link-to-bio/blocks/link-blocks.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B0). UI contract in `docs/link-to-bio/blocks/link-blocks-frontend-plan.md`. Profile ownership read from `bio.profiles` (DDL in `docs/link-to-bio/profile/public-profile-backend-plan.md`).

## Purpose

Give the backend developer an ordered procedure for the block list: schema from the database plan, entity, per-type validator, service (CRUD + atomic reorder), controller, security, audit, tests, container. Follow steps 1–9 in order. This file owns the server truth for block data; rendering and dashboard screens live elsewhere.

## Users

- Backend developer: executes steps 1–9 in package `com.app.bio.blocks`.
- QA: runs persistence, scheduling, validation, and race drills.
- Reviewer: checks the frozen enum is closed (adding a 14th type requires a migration note + spec amendment).

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith. Monolith pros: block writes join profile ownership checks in one transaction; reorder re-sequencing is one `UPDATE` batch; one RLS set. Microservices cons avoided: a block service would split the authoring transaction and add per-call cross-service IDOR checks with no ≥10x asymmetry (blocks scale 1:N with profiles). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, flyway, actuator, micrometer.
2. Step 1 — create the table once from `docs/link-to-bio/blocks/link-blocks-database-plan.md`: run its single `CREATE TABLE` (after the profile database plan, which holds the FK target; no versions, no ALTERs). Done when the script applies clean on empty PG `18.6` and a bad-type insert violates `chk_block_type`.
3. Step 2 — `entity/BlockEntity.kt` (`@Entity @Table(name="profile_blocks", schema="bio")`, fields as DDL, `payloadJson: Map<String, Any?>` via JSONB user-type) + `api/BlockType.kt` closed enum of the 13 values (unknown string → 422 before touching the DB).
4. Step 3 — `service/BlockPayloadValidator.kt` (pure, table-tested): per-type schema table — link `{url https: required, title ≤120, size ∈ small|medium|big|button, thumbnailKey?}`, social `{network, url https:}`, embed `{provider, url https:}` (renderer owns markup; no HTML fields accepted), resume `{documentId TEXT only}`, gallery `{items[{url https:, alt}]}`, video/music/event/form/product/course/tip/contact `{title?, url? https:}` with length caps. Returns `Ok(normalized)` or `Err(code)`: `unknown_block_type|bad_url_scheme|title_too_long|payload_too_long|bad_schedule_window`.
5. Step 4 — `service/BlockService.kt`: `listMine(userId, profileId)` (first assert `profile.userId == userId`, else 403 — never filter by profile alone); `create` = ownership → validate → `position = max+1` → save → audit → publish event; `patch` = owner-load → merge allowed fields → re-validate → save → audit → event; `delete` → remove → audit → event; `duplicate` = load → copy with new id at `position+1` → shift followers (one batch update) → audit; `reorder(userId, profileId, ids)` `@Transactional` = assert set(ids) == set(profile block ids) exactly (missing/foreign id → 422, nothing written) → batch-update positions 0..n → audit once → event once.
6. Step 5 — `api/BlockController.kt`: `GET /api/v1/blocks?profile_id=` → 200 list with computed `hiddenReason` (`visible|hidden|scheduled_future|expired`); `POST` → 201; `PATCH /:id` → 200; `DELETE /:id` → 204; `POST /api/v1/blocks/reorder {profileId, ids:[…]}` → 200. `userId` from session only. `@ControllerAdvice` → RFC 9457 problem JSON. Public visibility rule (enforced for SSR reads): `is_visible AND (from NULL OR now≥from) AND (until NULL OR now<until)`, order `position ASC, id ASC`.
7. Step 6 — security: owner scoping in service + RLS (S-1/S-6); sanitizer strips script/iframe/event-handlers from every string payload field (S-2); CSRF + rate-limit on write endpoints (S-5).
8. Step 7 — side effects: synchronous `audit_log` per mutation; `@Async` purge event to the share-seo plan's `CachePort`; `@Timed` on `reorder`; ArchUnit boundary on `com.app.bio.blocks`.
9. Step 8 — tests: validator table (every type × valid/invalid); `@WebMvcTest` (each 422 code, 403 cross-user, reorder with foreign id → 422 + positions untouched); Testcontainers IT (CHECK violation on bypass, RLS deny, concurrent reorders serialize with no duplicate positions); `./gradlew build` green.
10. Step 9 — container: same image; `docker run` + `podman run` from empty PG (baseline schema scripts applied once), rollback green per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: owned entirely by `docs/link-to-bio/blocks/link-blocks-database-plan.md` — the single `CREATE TABLE` for `bio.profile_blocks`, written once and never duplicated here. `BlockEntity` maps 1:1; the endpoints below are the only writers.
- Endpoints: `GET /api/v1/blocks?profile_id=` → `200 [{id, type, position, payload, isVisible, hiddenReason}]`; `POST` req `{profileId, type:"link", payload:{url:"https://…", title:"…", size:"button"}}` → `201`; `PATCH /:id` partial → `200`; `DELETE /:id` → `204`; `POST reorder` req `{profileId, ids:[…]}` → `200` re-sequenced list / `422` on foreign id with zero writes.

## Acceptance criteria

- Reorder drill: drag → reload identical order dashboard + public; concurrent reorders serialize, no duplicate positions.
- Scheduling drill: hidden/future/expired absent publicly, present in dashboard with correct `hiddenReason`.
- Validation drill: unknown type, `http:`/`javascript:` URL, inverted window → exact `422 code`, nothing written.
- Security drill: cross-user → 403 + RLS deny; XSS stripped; writes throttled.
- Container drill: digest pair boots, compose green (baseline scripts applied once), rollback green, contract diff clean.

## Out of scope

- Table definition and RLS DDL (`docs/link-to-bio/blocks/link-blocks-database-plan.md`); screens and drag-drop UI (`docs/link-to-bio/blocks/link-blocks-frontend-plan.md`); profile identity (`docs/link-to-bio/profile/public-profile-backend-plan.md`); themes/avatar; SSR/cache (share-seo plans); analytics; resume/shop/leads/domains; P4/P5.

## Open questions

- Q1: Max blocks per profile on free vs pro — unlimited v1 or a cap?
- Q2: Carousel autoplay and thumbnail dimensions — platform-fixed or per-block?
