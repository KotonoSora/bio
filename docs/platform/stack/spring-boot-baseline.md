# Spring Boot Baseline (framework 4.1.1)

PLAN ref: §0 Backend decision Option B (external Spring API + Cloudflare proxy) + §0 contract frozen (JSON REST, cookie session, server `user_id`, idempotent webhooks). DEVIATION FLAGGED: Spring Boot 4.1.1 is the mandatory framework for all server-side Java, replacing the Workers-Hono default for API + SSR-origin duties.

## Purpose

Pin one reproducible Spring baseline so every module builds, configures, observes, and fails the same way. Version drift, per-module starter sprawl, and env-dependent behavior are rejected at build time.

## Users

- Backend developers: implement modules against fixed starters and conventions.
- QA / Ops: reproduce any build and run it via the container contract.

## Functional requirements

1. Version pin: Spring Boot `4.1.1` via the BOM; language Kotlin `2.4.21` per `platform/stack/kotlin-baseline.md`; build tool Gradle `9.8.1` wrapper per `platform/stack/gradle-build.md` (Maven not used). JVM toolchain LTS (17 minimum, 21 recommended — record the chosen minor before P0 exit). Reproducible builds; test-skipping release builds rejected (see Gradle baseline).
2. Starters (closed set; additions need a spec note): `web`, `validation`, `data-jpa`, `security`, `actuator`, `flyway`, `testcontainers` (test), `micrometer-registry-prometheus`. No WebFlux unless a split spec justifies it; servlet MVC is the default.
3. Conventions: layered modules (`api → service → repository`), Bean Validation on all inputs, centralized exception → RFC 9457 problem responses, cookie-session auth with server-side `user_id` scoping, idempotency keys on checkout/refund create paths, statement timeout 5s on OLTP queries.
4. Config: `application.yaml` defaults plus environment overrides only (`SPRING_*`); secrets never in repo. Profiles `dev|staging|prod`; `prod` enables strict security headers, CSP, and actuator lockdown.
5. Test bar: unit + slice + Testcontainers PG 18.6 integration for every repository/grant path; ArchUnit boundary tests per `platform/architecture/architecture-decision.md`.

## Data / API touches

- Surface: `/api/v1/*` (domain), `/api/billing/*` (money), `/actuator/health` (probes), `/actuator/prometheus` (metrics, authenticated). OpenAPI generated per build; additive-only `/v1` changes.
- Persistence: Spring Data JPA + Flyway migrations (`db/migration/V*.sql`), Hikari pool, least-privilege app role at runtime vs migration role at boot. All SQL portable; TEXT ids, ISO8601 timestamps per PLAN §0.
- Frontend contract: SPA under `/app/*` consumes the same REST surface; SSR origins proxy through the same controllers so A↔B stays interchangeable.

## Acceptance criteria

- Version drill: `dependencies` output shows Boot `4.1.1` only; wrapper build reproducible on clean checkout.
- Contract drill: OpenAPI diff has no breaking `/v1` change without a major note; idempotent checkout double-submit yields one `orders` row.
- Security drill: unauthenticated cross-user access 403 (S-1/S-6); validation rejects malformed input with problem JSON; secrets grep clean.
- Observability drill: health + metrics endpoints respond per profile; request logs carry module + correlation id.

## Out of scope

- Language rules (see `platform/stack/kotlin-baseline.md`); wrapper/tasks/SBOM (see `platform/stack/gradle-build.md`); container run/promotion (see `platform/containers/container-lifecycle.md`); PG image/backup/RLS DDL (see `platform/stack/postgres-baseline.md`); domain rules (owning majors).

## Open questions

- Q1: JVM toolchain minor for the Kotlin compile target (17 vs 21) — confirm before P0 exit?
- Q2: WebFlux exception criteria — which future split spec justifies reactive (default stays servlet MVC)?
