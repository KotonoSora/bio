# Gradle Build (tool 9.8.1)

PLAN ref: P0 (scaffold/build) + `platform/stack/spring-boot-baseline.md` + `platform/stack/kotlin-baseline.md` + `platform/containers/container-lifecycle.md` (image consumes the jar). CLARIFICATION FLAGGED: Gradle `9.8.1` wrapper is the single mandated build tool; Maven is not used. Resolves Spring-baseline Q2.

## Purpose

Make every build — laptop, CI, release image — run the same tool, same dependency versions, and same verification tasks from a clean checkout. Wrapper drift, unpinned plugins, and test-skipping release builds are rejected.

## Users

- Backend developers: build, test, and cut the boot jar locally with the wrapper.
- CI / release: reproduce the jar and its SBOM byte-identically per tag.

## Functional requirements

1. Wrapper pin: `gradle-wrapper.properties` distribution pins `gradle-9.8.1`; `gradlew`/`gradlew.bat` only (system Gradle never used); wrapper checksum validated in CI (`--write-verification-metadata` record checked in).
2. Version catalog: `gradle/libs.versions.toml` pins Spring Boot `4.1.1` (BOM), Kotlin `2.4.21`, Flyway, Testcontainers PG, ArchUnit, ktlint/detekt; Dependabot/Renovate grouped updates only, never floating ranges.
3. Canonical tasks: `./gradlew build` (compile + ktlint/detekt + unit/slice + Testcontainers PG 18.6 integration + ArchUnit + `bootJar`), `./gradlew test`, `./gradlew bootJar`, `./gradlew dependencies` (audit). Release image stage runs `./gradlew bootJar -x test` only after the full `build` already passed in CI — never skip tests into a tag.
4. Reproducibility: locked dependencies, fixed archives (`preserveFileTimestamps=false`, deterministic jar ordering), build cache enabled but remote-cache writes restricted to mainline. SBOM (CycloneDX) generated per `bootJar` and stored with the release.
5. Container handoff: multi-stage `Containerfile` build stage runs the wrapper and copies exactly one `*.jar` to the Temurin JRE runtime stage; no Gradle daemon or caches leak into the runtime layer.

## Data / API touches

- Artifacts: `build/libs/app-*.jar` (single boot jar), `build/reports/**` (tests), `build/sbom/*.json` (CycloneDX). Image tags derive from `git describe` + SHA per the container lifecycle.
- Config inputs: `gradle.properties` (non-secret flags only), environment for secrets; `application.yaml` profiles `dev|staging|prod` owned by the Spring baseline.
- Toolchain: Gradle JVM runs on the Spring-baseline LTS toolchain; Kotlin compile `jvmTarget` recorded in the catalog comment (align with the chosen 17/21 minor).

## Acceptance criteria

- Pin drill: `./gradlew --version` reports `9.8.1` from wrapper on clean checkout; system-Gradle invocation fails the documented way.
- Repro drill: two clean `build` runs produce identical jar hashes (excluding SBOM timestamp field) and identical `dependencies` output.
- Gate drill: `build` runs ktlint/detekt, unit, Testcontainers PG, ArchUnit; any red task blocks the tag; skipped-test tags rejected by the release gate.
- Supply drill: SBOM present per release jar/image; vulnerability scan wired before promotion (see `platform/delivery/release-gates.md`).

## Out of scope

- Language rules (see `platform/stack/kotlin-baseline.md`); starters/config/actuator (see `platform/stack/spring-boot-baseline.md`); image run/promotion (see `platform/containers/container-lifecycle.md`).

## Open questions

- Q1: Remote build-cache backend (none in v1 vs shared cache) — decide before CI setup?
- Q2: Boot `bootBuildImage` vs Dockerfile `bootJar` copy as the canonical image path (Dockerfile copy proposed)?
