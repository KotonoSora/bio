# Short Links - Architecture and Tech Stack (feature 29)

Phase 2 TL · Service: `link-service`, dedicated database `links_db`. Implements `docs/01-requirements-ba/29-short-links/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| Code minting (random + vanity), destination + lifecycle state, resolve state machine, report/suspend ops, quota counters (Redis), hit-event emission | Usernames + reserved slugs -> profile service (feature 06); hit aggregates + fraud -> analytics service (feature 31); QR render log -> feature 30; campaigns + attribution -> features 48-50; billing -> features 32-40 |

Rationale: resolve is the highest-QPS anonymous path (10,000 rps target) and must scale and fail independently; abuse isolation (spam infrastructure) is cleanest per service; the table is quota-bounded, so one writer suffices. Not split further in v1: one aggregate, one table, one writer.

Cross-service rules: no service reads another's tables; the profile service owns reserved-slug enforcement (`go`, `r`, `a`); analytics consumes `link.hit.v1` only; suspend publishes `link.suspended.v1` for cache purge.

## Runtime topology

Gateway (platform, Spring Cloud Gateway): terminates dashboard sessions (SSO/OIDC), enforces WAF + rate rules, signs the internal identity JWT; routes `/api/v1/links*` to link-service; public `GET /go/*` (plus `/r/`, `/a/`) reaches link-service anonymously. CDN in front; `/go/*` responses are `no-store` by design (no edge caching of redirects). Replicas: stateless, 3 baseline, autoscale on rps. Redis (platform instance, database 3) + MinIO (platform) complete the runtime.

## Compose slice (joins platform network `app`; Redis/MinIO come from the platform compose)

```yaml
services:
  link-service:
    build: ./link-service
    image: registry/link-service:${TAG:-dev}
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://links-db:5432/links_db
      SPRING_DATASOURCE_USERNAME: app_role
      SPRING_DATASOURCE_PASSWORD: ${LINKS_DB_PASSWORD:?set_me}
      SPRING_DATA_REDIS_URL: redis://redis:6379/3
      APP_INTERNAL_JWT_JWKS: http://gateway:8080/.well-known/jwks.json
    ports: ["8083:8080"]
    depends_on:
      links-db: { condition: service_healthy }
    networks: [app]
  links-db:
    image: postgres:18.6
    environment:
      POSTGRES_DB: links_db
      POSTGRES_USER: migration_role
      POSTGRES_PASSWORD: ${LINKS_DB_PASSWORD:?set_me}
    volumes: [links-data:/var/lib/postgresql/data]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U migration_role -d links_db"], interval: 5s, timeout: 3s, retries: 10 }
    networks: [app]
volumes: { links-data: {} }
networks: { app: { external: true } }
```

Postgres digest is pinned in CI per the platform baseline. Podman parity (`podman compose up`) is release acceptance.

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
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
}
tasks.withType<Test> { useJUnitPlatform() }
```

## React.js 19.3 integration topology

`/app/links` runs in the platform SPA (React 19.3, platform router + query conventions) and consumes `/api/v1/links*` through the gateway with session cookie + CSRF + Turnstile; field rules in `docs/01-requirements-ba/29-short-links/04-ui-data-dictionary.md`; error-code map in `03-api-contracts-and-payloads-part-2.md`. The redirect path is server-side only - no React, no SPA bundle on `/go/*`.

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| Resolve cache | Redis `links:code:<code>`, TTL 300 s, purge on suspend | hit ratio < 90% at peak |
| Counting | queue events only; zero PG writes on resolve | analytics SLA miss (feature 31) |
| 301 usage | explicit-permanent vanity only | never |
| Further split (dedicated edge redirector) | deferred | resolve p95 gate fails at measured load |
