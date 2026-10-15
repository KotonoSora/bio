# Verification Followers Backend Plan (badge review + cached totals — server build steps)

PLAN ref: P1a-2 (profile identity extension; P4-1 OAuth vault explicitly NOT built) + S-1/S-2/S-5/S-6/S-7. BA source: `docs/link-to-bio/profile/verification-followers.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B0). Schema in `docs/link-to-bio/profile/verification-followers-database-plan.md` (`verification_requests`, written once) plus trust columns in `docs/link-to-bio/profile/public-profile-database-plan.md`. Screens in the verification frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for trust display: entities, verification state machine, follower-total caching with the 24h refresh job, socials validation, ops endpoints, and tests. Follow steps 1–8 in order. Token vaulting, posting, and automations are never built here (P4-gated; grep-audited).

## Users

- Backend developer: executes steps 1–8 in package `com.app.bio.trust`.
- QA: runs fake-badge, state, follower, and race drills.
- Ops reviewer: uses the decide endpoints (role-gated, audited).

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith. Monolith pros: badge transitions share the profile transaction + audit; the refresh job reads the same RLS baseline; SSR reads cached totals in-request. Microservices cons avoided: extracting trust would split badge state from profile state (rename must hide the badge atomically) for an ops-low-volume path. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, flyway, scheduling, actuator, micrometer.
2. Step 1 — schema: run the verification database plan script once (after the profile script). No other DDL in this slice.
3. Step 2 — entities: `VerificationRequestEntity` 1:1 with the table; `profiles` trust columns mapped in `ProfileEntity` (owned by the profile backend plan — extended here by field only, no second mapping).
4. Step 3 — `service/VerificationService.kt`: `request(userId)` = assert profile owned → reject if an open (`pending`) request exists (`409 verify_already_pending`) → store `evidence_ref` blob key (uploaded separately, random key) → insert `pending` → audit; `status(userId)` → current state + decidedAt; ops `decide(id, approve|reject|revoke, reason)` behind `ROLE_OPS` = load → transition (`pending→verified` sets `profiles.verification_status + verified_at`; revoke sets `revoked` + clears `verified_at`) → audit with actor + reason → publish purge event. Rename hook `onUsernameChange` (called by `ProfileService`): verified profile → status back to `pending`, badge hidden until re-approved.
5. Step 4 — `service/FollowerService.kt`: `updateSocials(userId, links[], selfCounts)` = validate `https:` + per-network handle patterns (`bad_social_url|bad_handle` → 422) → cap self-reported counts (≤100M, over → `422 follower_count_capped`) → version history row → flag 10x jumps (`follower_jump_review`, review decides, count still stored) → recompute `follower_total_cached + breakdown_json + follower_updated_at`; `refreshAuto(profileId)` = server-side keyless fetch only (RestClient, per-network budget, NO tokens anywhere) → update cache → on failure keep last-good and set stale flag (public page never slows, never errors).
6. Step 5 — `job/FollowerRefreshJob.kt` `@Scheduled(cron="0 0 */24 * * *")`: iterate verifiable profiles, call `refreshAuto`, swallow per-profile failures with error metric. Never triggered by visitor load.
7. Step 6 — controllers: `TrustController` (`POST /api/v1/profile/verify-request` → 201; `GET verify-status` → 200; `PUT /api/v1/profile/socials` → 200 totals; `POST followers/refresh` rate-limited, keyless only) with session `userId`; `OpsVerifyController` (`POST /api/v1/ops/verify/:id/{approve,reject,revoke}` + `{reason}`) behind `ROLE_OPS`. Badge renders from `verification_status==verified` through a platform-owned SVG component — bio markup can never fake it (renderer test asserts emoji/SVG in bio renders as text).
8. Step 7 — near-match guard: `UsernamePolicy.nearMatchClaim(new)` flags Levenshtein ≤2 against verified usernames into an ops review queue (advisory, no auto-block). Security: owner scoping + RLS (S-1/S-6, public reads get badge + totals only, never evidence); handle/URL strip + CSP (S-2); rate limits on request/refresh/socials (S-5); evidence blobs under random keys.
9. Step 8 — tests + container: state-machine test (pending→verified→revoked→pending, rename-hides-badge); follower tests (cap, versioning, jump flag, outage keeps last-good + stale); `@WebMvcTest` (403 cross-user, 403 non-ops on ops endpoints); Testcontainers IT (RLS deny, cascade purge); `./gradlew build` green; same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`; grep audit proves zero P4 vault tables.

## Data / API touches

- Schema: database plan above (single `verification_requests` CREATE) + trust columns in the profile database plan. Endpoints: `POST verify-request` → `201 {status:pending}` / `409`; `GET verify-status` → `200`; ops decide → `200` + audit; `PUT socials` req `{links:[{network,handle,url}], selfCounts:{…}}` → `200 {total, breakdown, stale}`; `POST followers/refresh` → `200` cached totals.

## Acceptance criteria

- Fake-badge drill: badge emoji/SVG in bio renders as text, never the badge component.
- State drill: pending hides badge; verified shows within purge SLA; revoked removes within SLA; verified rename hides until re-approved.
- Follower drill: self-report versioned; 10x jump flags review; auto-refresh never slows public p95; outage keeps last-good + stale.
- IDOR drill: cross-user writes 403 + RLS deny; ops endpoints refuse creator credentials.
- Audit drill: every decide in `audit_log` with actor + reason.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); review/socials screens (verification frontend plan); block authoring; themes/avatar; SSR/cache; per-click analytics; OAuth vault/posting/automations (P4, never stubbed); shop/leads/domains.

## Open questions

- Q1: Auto-verification signals in v2, or manual review permanently?
- Q2: 24h follower cache acceptable at launch, or faster for top profiles?
