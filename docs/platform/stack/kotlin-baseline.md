# Kotlin Baseline (language 2.4.21)

PLAN ref: §0 Backend decision Option B + `platform/stack/spring-boot-baseline.md` (framework) + `platform/stack/gradle-build.md` (build). CLARIFICATION FLAGGED: all server-side source is Kotlin `2.4.21`; the JVM runtime still follows the Spring baseline toolchain. No Java-source or second JVM language in `src/main`.

## Purpose

Pin one Kotlin language baseline so Spring Boot 4.1.1 services compile, serialize, and persist identically on every machine and in every release image. Version drift, mixed-language entities, and reflection-hostile constructs are rejected at build time.

## Users

- Backend developers: write all service, API, and entity code in Kotlin against Spring conventions.
- Reviewers / CI: enforce the pin and the JPA/serialization rules below on every change.

## Functional requirements

1. Version pin: Kotlin `2.4.21` via the Gradle version catalog + `kotlin("jvm")` and `kotlin("plugin.spring")` / `kotlin("plugin.jpa")` / `kotlin("plugin.serialization")` as needed; `kotlin-stdlib` aligned to the same version (no ad-hoc stdlib bumps). `kotlinc` version asserted in CI.
2. Spring compatibility: entities and `@Configuration`/`@Component` classes stay `open` via the all-open/no-arg plugins (no manual `open` sprawl); `data class` for DTOs, regular/`@Entity` classes for JPA; null-safety honored at API boundaries (`?` + Bean Validation, never platform-type leakage into public signatures).
3. Serialization: Jackson Kotlin module (or kotlinx.serialization where a spec mandates it) with explicit null/unknown-field policy; `Instant` ISO8601 everywhere; no reflection-based tricks that break Graal/native assumptions (native image itself is out of scope — rule exists to keep the door open).
4. Coroutines discipline: servlet MVC stays the default (per Spring baseline); `suspend` functions and `Flow` only inside a split spec that justifies WebFlux/reactive; never mix blocking JPA with unstructured coroutine dispatchers.
5. Quality bar: `warnings-as-errors` for production sourcesets in CI; ktlint/detekt configuration checked in (fail on new violations, grandfather existing ones explicitly if any).

## Data / API touches

- Layout: `src/main/kotlin/**` (code), `src/main/resources/db/migration/V*.sql` (Flyway SQL unchanged), `src/test/kotlin/**` (Testcontainers PG 18.6 tests). No `src/main/java` additions without a recorded exception.
- Persistence: JPA entities map the single-PG schemas from `platform/stack/postgres-baseline.md` (`resume`, `bio`, `billing`, `shop`, `leads`); TEXT ids, `timestamptz`, `amount_vnd INT`, `orderCode` UNIQUE per billing rules.
- API: problem-JSON errors (RFC 9457), OpenAPI generated from Kotlin sources; additive-only `/api/v1` evolution.

## Acceptance criteria

- Version drill: build log and dependency report show Kotlin `2.4.21` only; clean-checkout wrapper build green.
- Entity drill: all-open/no-arg present; JPA metamodel loads without `final class` errors; Jackson round-trips nullable DTOs without platform-type NPEs.
- Style drill: ktlint/detekt pass; production warnings fail the build.
- Baseline: IDOR 403 probes and validation problem-JSON behavior identical to the Spring baseline contract.

## Out of scope

- Framework starters and profiles (see `platform/stack/spring-boot-baseline.md`); wrapper/tasks/SBOM (see `platform/stack/gradle-build.md`); PG DDL/backup (see `platform/stack/postgres-baseline.md`); container run/promotion (see `platform/containers/container-lifecycle.md`).

## Open questions

- Q1: ktlint vs detekt as the single mandatory gate (or both, with which rule sets)?
- Q2: kotlinx.serialization for any boundary (webhooks, queues) or Jackson-only in v1?
