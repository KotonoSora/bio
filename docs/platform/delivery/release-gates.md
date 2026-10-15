# Release Gates (per-tag Docker and Podman acceptance)

PLAN ref: P0-4 (dev/build/preview/deploy) + P5-3 (k6 gates) + Security checklist S-1…S-7. Consumed by every increment in `platform/delivery/execution-overview.md`. Stack: Boot 4.1.1 + Kotlin 2.4.21 + Gradle 9.8.1 + PG 18.6 per `platform/stack/*`; runnable contract per `platform/containers/container-lifecycle.md`.

## Purpose

Define the single acceptance path every release tag passes before promotion: same digest verified under both Docker and Podman, migrated from empty, contract-tested, probed, and traceable. No tag reaches staging or prod on a partial gate.

## Users

- QA / release: run the gate and sign the promotion.
- Ops: promote and roll back by digest only.
- Developers: reproduce any gate failure from the tag alone.

## Functional requirements

1. Build gate: CI `./gradlew build` green from clean checkout (Kotlin `2.4.21`, Boot `4.1.1`, Gradle `9.8.1` asserted); `dependencies` output archived; SBOM (CycloneDX) generated and scanned — high/critical blocks promotion.
2. Migration gate: tag boots against empty PG `18.6` via Flyway with zero manual SQL; `SELECT version()` + extension list asserted; backward-incompatible migrations flagged and paired with the compatibility window from the execution overview.
3. Container gate (both runtimes): `docker run` and `podman run` reach ready via `/actuator/health/readiness`; compose cold boot green; previous-tag rollback green; digests compared and recorded. Missing-env fails fast with an actionable message; secret grep over layers/logs clean.
4. Contract + data gate: OpenAPI diff additive-only; checkout double-submit yields one `orders` row; idempotent webhook replay grants once; refund retry is a no-op; no E4/BACKLOG tables or jobs present before their increment (migration audit).
5. Security + load gate: S-1/S-6 IDOR 403 + RLS deny as app role; S-2 XSS strip + CSP; S-3 webhook bad-sig/replay/tamper; S-4 upload rejection; S-5 throttle on anonymous routes; S-7 staging-mirror migrate + preview. Load gate per increment (P1 10k / P3 100k / P5 1M+100k) where PLAN assigns one.
6. Promotion rule: same digest dev → staging → prod with signed release note (tag, SHA, Flyway version, BA docs covered, gate evidence). Hotfix cuts a new tag from the release branch — never mutates a promoted tag.

## Data / API touches

- Tag identity: `:<semver>-<git-sha>` + channel alias; registry retention keeps every official tag. Release note links image digest, SBOM, migration version, and gate logs.
- Probes: `/actuator/health/liveness`, `/actuator/health/readiness` (data-free, unauthenticated); full actuator + Prometheus authenticated. Gate scripts hit versioned `/api/v1/*` and `/api/billing/*` only.
- Audit: every promotion/rollback appends `audit_log{actor, action, target, at}` (or the release-log equivalent pre-E4); secret material never appears in notes or logs.

## Acceptance criteria

- Gate-completeness drill: a tag missing any gate artifact (SBOM, digest pair, migration log, probe report) is unpromotable by tooling, not just policy.
- Runtime-parity drill: Docker-pass/Podman-fail (or reverse) blocks promotion; flaky-pass retries are bounded and logged.
- Rollback drill: previous tag re-runs against the current DB contract within the documented window; breaking-migration tags carry their tested forward-fix instead.
- Phase drill: E0→E5 promotions each carry the increment's BA-doc list and PLAN-step coverage from the execution overview.

## Out of scope

- Increment sequencing and backlog gating (see `platform/delivery/execution-overview.md`); Dockerfile/SBOM generation internals (see `platform/containers/*` and `platform/stack/gradle-build.md`); domain acceptance detail (owning BA majors).

## Open questions

- Q1: Registry + signing backend (GHCR + cosign proposed) — confirm before first tagged release?
- Q2: Gate automation runner (CI-native vs release-tool) and required approvers per environment?
