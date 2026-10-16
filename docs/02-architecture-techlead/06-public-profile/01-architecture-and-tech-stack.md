# Public Profile - Architecture and Tech Stack (feature 06)

Phase 2 TL · Service: `profile-service`, dedicated database `profile_db`. Implements `docs/01-requirements-ba/06-public-profile/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| The `profiles` row (identity, visibility, presentation pointers, trust columns), the username namespace: claims, retirements, 301 redirects, the internal public-read API | Blocks -> feature 08; themes/avatar pipeline -> feature 09 (writes `theme_id`/`avatar_key` through this API, audited); SSR, cache, sitemap -> feature 10; analytics -> features 12-15; account deletion flow -> feature 02 (this service reacts); SSO/session -> feature 01 |

Rationale: claim/rename/toggle plus the redirect insert must be one transaction with one writer, and the username namespace must be a single point of truth (distributed ownership recreates collisions). Read load is cache-absorbed (feature 10), so no split pressure; not split further in v1.

Cross-service rules: no other service touches `profile_db`; the public-site service consumes the internal read API; reserved-slug enforcement lives here and is consumed by the routing rules in `docs/01-requirements-ba/29-short-links/02-user-stories-and-business-rules.md` (BR-01); `user_id` is an opaque key from the identity service - no cross-service joins.

## Runtime topology

Gateway (platform): session termination, rate rules, internal identity JWT; routes `/api/v1/profile*` to profile-service. Public `/:username` is served by the public-site service (feature 10), which resolves usernames via `GET /internal/v1/profiles/{username}` (service-to-service JWT) behind its Redis cache. Replicas stateless x3. Redis (platform instance, database 4) carries gateway counters and purge signaling.

## Compose slice (joins platform network `app`; Redis/MinIO come from the platform compose)

```yaml
services:
  profile-service:
    build: ./profile-service
    image: registry/profile-service:${TAG:-dev}
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://profile-db:5432/profile_db
      SPRING_DATASOURCE_USERNAME: app_role
      SPRING_DATASOURCE_PASSWORD: ${PROFILE_DB_PASSWORD:?set_me}
      SPRING_DATA_REDIS_URL: redis://redis:6379/4
      APP_INTERNAL_JWT_JWKS: http://gateway:8080/.well-known/jwks.json
    ports: ["8082:8080"]
    depends_on:
      profile-db: { condition: service_healthy }
    networks: [app]
  profile-db:
    image: postgres:18.6
    environment:
      POSTGRES_DB: profile_db
      POSTGRES_USER: migration_role
      POSTGRES_PASSWORD: ${PROFILE_DB_PASSWORD:?set_me}
    volumes: [profile-data:/var/lib/postgresql/data]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U migration_role -d profile_db"], interval: 5s, timeout: 3s, retries: 10 }
    networks: [app]
volumes: { profile-data: {} }
networks: { app: { external: true } }
```

Postgres digest pinned in CI per the platform baseline; Podman parity (`podman compose up`) is release acceptance.

## build.gradle.kts (Kotlin DSL; versions pinned in gradle/libs.versions.toml)

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    kotlin("plugin.jpa") version "2.4.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jsoup:jsoup:1.18.3")      // HTML stripping (HtmlSanitizer)
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
}
tasks.withType<Test> { useJUnitPlatform() }
```

## React.js 19.3 integration topology

`/app/profile` runs in the platform SPA (React 19.3, platform router + query conventions) and consumes `/api/v1/profile*` through the gateway with session cookie + CSRF; field rules in `docs/01-requirements-ba/06-public-profile/04-ui-data-dictionary.md`; error map in `03-api-contracts-and-payloads.md`. No username regex or reserved list in the client bundle (grep gate, AC-08 family). Public `/:username` has no SPA - SSR only.

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| Username availability reads | direct indexed PG lookups, no cache (correctness over speed) | check p95 > 150 ms at sustained load |
| Rename propagation | event + 60 s cache TTL bound (event loss self-heals) | stale > 2 min observed in prod |
| Public data path | internal read API only (no cross-service DB reads) | never (mandate) |
| Second profile | guard `profile_limit_reached`; limit from the entitlement capability `profiles.limit` | `docs/02-architecture-techlead/03-tier-presentation/01-architecture-and-tech-stack.md` (D-3) |
