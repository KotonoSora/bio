# Platform Baseline - Stack and Build Pins

Phase 2 TL · Cross-cutting baseline for all features. Supersedes the legacy stack docs (stack pins are the mandated ones; no re-decisions per `AGENTS.md`). Topology: `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md`; containers: `04-containers-and-compose.md`.

## Version pins (closed set)

| Element | Pin | Notes |
|---|---|---|
| Spring Boot | `4.1.1` (BOM) | All services; Gradle plugin aligned to the same version |
| Kotlin | `2.4.21` | All `src/main` + `src/test`; no Java source, no second JVM language |
| Gradle | `9.8.1` wrapper-only | `gradlew`/`gradlew.bat`; system Gradle never used; wrapper checksum recorded in CI |
| JVM toolchain | 21 (LTS) | Recorded resolution of the legacy 17-vs-21 open pin; container runtime = Eclipse Temurin JRE 21 |
| PostgreSQL | `18.6` | Digest-pinned image; `SELECT version()` asserted at boot and in CI; conventions in `03-data-layer-conventions.md` |
| Redis | `7.4` (digest-pinned) | Platform instance; logical-db allocation in `01-architecture-and-service-topology.md` |
| Nginx | pinned upstream image + platform config | SPA + media static serving, edge cache, proxy to the gateway; storage rules in `09-local-storage-and-nginx-spec.md` |
| Node.js | `24` (LTS, digest-pinned) | Frontend build + CI runtime for the shell/MFE workspaces: `node:24-alpine` in `web/` and `deploy/edge/` images, `setup-node` on the same line in CI; the major line is fixed, never floats forward |
| React.js | `19.3` | SPA shell + MFE verticals under `/app/*` (composition: `10-web-micro-frontends.md`); committed workspace lockfile at repo root |
| Testcontainers | current line pinned in catalog | Integration tests run against PG `18.6` + Redis `7.4` images |

Patch bumps of infrastructure images (PG/Redis/Nginx/Node) are allowed within the pinned minor line; Boot/Kotlin/Gradle bumps require a baseline amendment.

## Service starter set (closed)

Every service uses these starters only: `web`, `validation`, `data-jpa`, `security`, `cache` (Redis), `actuator`, `micrometer` (OTel export), plus test starters (`test`, `testcontainers`). A new starter or third-party dependency requires a spec amendment with the container-safety rationale (pure-JVM, no native images, no headless browser) - precedent: `com.google.zxing` in feature 29.

## Kotlin and Spring conventions

- Data classes for DTOs and requests; Jackson serializes ISO8601 (`Instant`) and omits nulls where contracts say "absent".
- Bean Validation on all request DTOs; RFC 9457 `ProblemDetail` for every error, stable `code` strings (never renamed).
- Service layer returns Kotlin `Result<T>` (or sealed domain errors mapped at the API boundary); exceptions never cross service/mesh boundaries - the API layer maps to `ProblemDetail` with `traceId` only, no stack traces.
- JPA: Kotlin `no-arg`/`all-open` plugins; no lazy loading outside transactions; statement timeout 5 s default per service.
- Layered packages (`api`, `domain`, `data`, `config`); ArchUnit test enforces layer boundaries per service so an extraction stays a move, not a rewrite.
- Config is environment-only (12-factor): no profile-conditional behavior drift; secrets via env/vault only (`.env` never committed).
- IDs are `TEXT` (ULID-style, service-prefixed); no numeric ids in APIs or schemas.
- Money is VND integer (`Long`) everywhere; never decimals.

## Gradle build contract

- `gradle/libs.versions.toml` catalog pins Boot, Kotlin, plugins, and every dependency; no dynamic versions (`+`, `latest.release`) anywhere.
- Tasks: `./gradlew build` (unit + slice), `integrationTest` (Testcontainers), `bootJar`, `cyclonedxSbom` (SBOM artifact archived per tag). Release builds never skip tests.
- Reproducible archives: fixed file order/timestamps off; dependency output archived for audit.
- CI from clean checkout runs `build` + `integrationTest` + wrapper checksum verification; a checksum mismatch fails the build.
- OpenAPI: springdoc-generated spec per service; the diff gate (see `06-release-gates.md`) rejects breaking changes - `/api/v1` and `/internal/v1` are additive-only.

## Frontend integration

- SPA routes under `/app/*` (React 19.3), served statically by the Nginx edge; same-origin only - no CORS exists.
- Dashboard verticals are independently built micro frontends loaded at runtime (`10-web-micro-frontends.md`); each bundles its own React (no shared runtime); versioned bundles under `/app/mfe/<name>/<version>/`.
- Session cookie (HttpOnly, Secure, SameSite) at the edge + CSRF double-submit header on mutations; services trust only the internal JWT.
- Public pages and the micro-script are server-rendered/cached by `public-site-service` (features 10-11), never by the SPA.
- Turnstile-class challenge wiring at the gateway for abuse-prone routes; client never holds provider keys.
- PWA shell enables same-numbers parity across surfaces (feature 11); offline rules per `web/` service worker config, network-only for `/api/*`.

## Observability contract

- JSON logs to stdout (no file sinks): route, status, timing, trace id; never payloads, PII, or money values.
- Actuator: `/health/liveness` + `/health/readiness` wired to container healthchecks; metrics names are namespaced per service and stable.
- W3C `traceparent` propagated gateway -> service -> queue -> consumers; audit log append-only, 24-month retention.
