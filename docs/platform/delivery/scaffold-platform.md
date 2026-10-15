# Scaffold Platform (execution plan E0 — repo, build, runtime baseline)

PLAN ref: P0-1…P0-4 (scaffold, wiring, WAF/Turnstile keys, dev/build/preview/deploys) + §0 stack/cloud decisions + Security checklist S-1…S-7 (applied from day one). BA source: `docs/PLAN.md` P0; stack baselines in `docs/platform/stack/*`; runnable contract in `docs/platform/containers/container-lifecycle.md`. DEVIATION CARRIED: containerized Spring Boot + PG `18.6` from P0 instead of Workers/Hono + Neon/Hyperdrive per `docs/platform/architecture/architecture-decision.md`; contract frozen so later increments never re-scaffold.

## Purpose

Stand up one reproducible foundation every later increment builds on: monorepo layout, pinned toolchain, empty-boot database with Flyway V1, health/observability endpoints, CI build plus per-release image and SBOM. E0 exits with a clean-checkout compose boot green under both Docker and Podman and no domain tables beyond identity — the smallest releasable artifact the gate accepts.

## Users

- Backend developers: clone, boot compose, and implement `bio`/`resume`/`billing` modules against fixed starters.
- QA / release: reproduce any build and promote the same digest dev → staging → prod.
- Ops: start, stop, health-check, and roll back official deployments with Docker or Podman interchangeably.

## Functional requirements

1. Stack and topology (mandatory): PostgreSQL LTS `18.6` (digest-pinned `postgres:18.6` image, `SELECT version()` asserted at boot and in CI), Spring Boot `4.1.1` via BOM, Kotlin `2.4.21` (all-open/no-arg plugins, Jackson ISO8601, servlet MVC default), Gradle `9.8.1` wrapper-only (`gradlew`/`gradlew.bat`, checksum-validated; system Gradle never used). Architecture verdict for E0 — modular monolith: single deployable, multi-module (`resume`, `bio`, `billing`, `shop`, `leads`, `platform`) with ArchUnit package-boundary tests. Monolith pros: one transaction scope, one RLS set, one release train at 10k–100k concurrent; cons accepted: single blast radius (mitigated by backward-compatible migrations + previous-tag rollback). Microservices rejected: no team-ownership contention, isolation need, or ≥10x scaling asymmetry exists at scaffold time — a split now would buy distributed auth/audit/backup surface for zero measured gain.
2. Repo layout: Gradle version catalog (`libs.versions.toml` pins Boot/Kotlin/Flyway/Testcontainers/ArchUnit/lint), `application.yaml` + env overrides (`SPRING_*`, profiles `dev|staging|prod`), `db/migration/V1__identity.sql` (users, linked SSO accounts, sessions, `audit_log` append-only), closed starter set (`web`, `validation`, `data-jpa`, `security`, `actuator`, `flyway`, `micrometer-registry-prometheus`; `testcontainers` test-only). Reproducible builds (locked deps, deterministic jar ordering); `./gradlew build` runs compile + lint + unit/slice + Testcontainers PG integration + ArchUnit + `bootJar`; test-skipping release builds rejected.
3. Runtime contract: multi-stage `Containerfile`/`Dockerfile` (Gradle build → Eclipse Temurin JRE, non-root, no secrets baked), OCI image tagged `:<semver>-<sha>` plus channel alias (never `latest` for releases); `compose.yaml` defines `app` (8080) + `db` (PG 18.6, `pgdata` volume, `pg_isready` healthcheck). Config exclusively via environment (12-factor); secrets from vault/env files, never committed or logged; `.gitignore` covers `.env`, `.wrangler/`-era artifacts, compose overrides. Stdout JSON logs; `/actuator/health/liveness|readiness` wired to container HEALTHCHECK; graceful SIGTERM shutdown.
4. Security from day one: managed + rate WAF rules on anonymous routes; Turnstile keys in `.dev.vars`; cookie-session auth skeleton (server `user_id`, `HttpOnly`+`Secure`+`SameSite=Lax`); RLS on identity tables + least-privilege app vs migration roles; `user_id TEXT NOT NULL` + index everywhere; problem-JSON (RFC 9457) errors; strict security headers + CSP + actuator lockdown in `prod`.
5. CI/release: clean-checkout `./gradlew build` green, CycloneDX SBOM per `bootJar` scanned (high/critical blocks promotion), image published per tag, OpenAPI generated with additive-only `/v1` diff gate. Migration gate: tag boots against empty PG `18.6` via Flyway with zero manual SQL.

## Data / API touches

- DDL: V1 identity only (`users`, SSO link rows, sessions, `audit_log`); TEXT ids, `timestamptz` ISO8601; single logical DB with per-module schemas (`resume`, `bio`, `billing`, `shop`, `leads`); single-PG rule (no second OLTP).
- Surface: `/api/v1/*` (domain), `/api/billing/*` (money, stubbed until E3), `/actuator/health`, `/actuator/prometheus` (authenticated). Migration state queryable via Flyway info in startup log; `.env.example` documents every variable with safe defaults.
- Ports/volumes: app `8080` host-mapped in dev (reverse-proxied in prod); db `5432` loopback-only in dev; `pgdata` persists PG data.

## Acceptance criteria

- Pin drill: `./gradlew --version` reports `9.8.1` from wrapper; `dependencies` shows Boot `4.1.1` + Kotlin `2.4.21` only; all envs report PG `18.6` with the closed extension set.
- Boot drill: `cp .env.example .env` then compose up under Docker AND `podman-compose` both green; `down -v` resets cleanly; missing env fails fast with an actionable message.
- Gate drill: per-release `docker run` + `podman run` reach ready on empty PG; digests match; previous-tag rollback green; secret grep over layers/logs clean; SBOM present and scanned.
- Contract drill: OpenAPI diff clean; ArchUnit zero cross-module violations; health endpoints data-free unauthenticated, full actuator authenticated.
- Baseline: anonymous routes throttled (S-5); cross-user access 403 + RLS deny (S-1/S-6); validation problem-JSON; staging-mirror migrate + preview green (S-7).

## Out of scope

- All domain behavior (B0–B2 bio, R0–R2 resume, M0–M2 monetize, and the E2/E4 plans below — built on this scaffold, never in it); per-release promotion mechanics (see `docs/platform/delivery/release-gates.md`); K8s manifests and P5 replicas/ISR (see `docs/platform/delivery/scale-out.md`).

## Open questions

- Q1: JVM toolchain minor 17 vs 21 for the Kotlin compile target — confirm before P0 exit?
- Q2: Registry + signing backend (GHCR + cosign proposed) — confirm before first tagged release?
