# Link Blocks - Architecture and Tech Stack (feature 08)

Phase 2 TL · Extension of `profile-service` (module `com.app.profile.blocks`), same database `profile_db`. Implements `docs/01-requirements-ba/08-link-blocks/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| `profile_blocks` (13-type frozen superset), payload validation, dense-position reorder, windowed public read, per-block hide/schedule semantics | Profile identity/namespace -> feature 06 (same service, separate module); theme treatment + thumbnail/avatar pipeline -> feature 09; SSR render, cache, purge -> feature 10; per-block analytics -> feature 12; resume documents -> features 17-21 (logical ref via `documentId`, no cross-service FK) |

Boundary rationale: blocks belong to the bio-page authoring bounded context - block writes join profile ownership checks in one transaction, reorder is one batch update, and blocks scale 1:N with profiles (~20 avg, cap 250). No extraction trigger is present (independent scale, team ownership, or deploy independence); revisit if block writes ever exceed profile writes by >= 10x or per-block rendering moves to its own service. Extraction, when triggered, moves `profile_blocks` + validator + endpoints intact - the internal read API is the only consumer contract.

Cross-service rules: no new database, no new container, no cross-service table access; SSR consumes the internal windowed read (below); thumbnails are storage file names (`thumbnailKey`) resolved by the feature 09 pipeline, stored here as opaque strings.

## Runtime delta (compose slice unchanged from feature 06)

None: same container, same env, same Redis db 4 namespace pattern (`blocks:*` keys), the local storage volume reused for thumbnails. Nothing to add to `docker-compose.yml`; Podman parity remains release acceptance.

## build.gradle.kts delta (no new dependencies)

Unchanged versus `docs/02-architecture-techlead/06-public-profile/01-architecture-and-tech-stack.md`: Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1 wrapper; web, validation, data-jpa, security, flyway, actuator, micrometer already declared. No drag-drop or schema libraries are added server-side.

## React.js 19.3 integration topology

Route `/app/blocks?profile=` (owner-only; also reachable as the profile-editor tab). `BlockSchema.ts` hand-mirrors the server validator (13 types, `https:` rule, length caps) for instant hints only - divergence resolves in favor of the server. Drag reorder queues commits (one in-flight `POST reorder`); no position ever originates client-side. Field rules: `docs/01-requirements-ba/08-link-blocks/04-ui-data-dictionary.md`.

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| Position density | service-assigned dense ints; no DB exclusion (races would surface as 500s) | contention evidence shows duplicate positions |
| Reorder serialization | one transaction per profile (row lock on the profiles row) | reorder p95 > 400 ms under traffic |
| Public filtering | window predicate computed server-side in the query; renderer renders what it gets | renderer needs raw rows (never) |
| Payload checks | validator-only; DB keeps the type + window CHECKs as backstops | a bypassed-writer incident |
| Purge signaling | reuse `profile.published.v1` (no new event) | blocks-only consumers need separation |
| Block budget | 250/profile v1 (plan-configurable later) | plan tiers ship block budgets |
