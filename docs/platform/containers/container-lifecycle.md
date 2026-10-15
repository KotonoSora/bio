# Container Lifecycle (Docker and Podman, every release runnable)

PLAN ref: P0-2/P0-4 (`wrangler.jsonc` wiring, `npm run dev/build/preview`, `wrangler deploy`). DEVIATION FLAGGED: every dev and official release additionally ships as an OCI image runnable independently via Docker or Podman. Container run is the release acceptance, not an optional extra.

## Purpose

Guarantee any release runs identically from laptop to production with one image plus environment plus database: `image + env + PG DSN`. No release depends on a maintainer's laptop state, a mutable `latest` tag, or an edge-only deploy path.

## Users

- Developers: boot the full stack locally (app + PG 18.6) with one compose command.
- QA / release: promote the same digest dev → staging → prod; reproduce any bug by tag.
- Ops: start, stop, health-check, and roll back official deployments with Docker or Podman interchangeably.

## Functional requirements

1. Image build: multi-stage `Containerfile`/`Dockerfile` (build with Maven/Gradle + Eclipse Temurin JRE runtime, non-root user, minimal layer, no secrets baked). OCI-compliant so Docker and Podman both run it rootless. Tags immutable: `:<semver>-<git-sha>` plus channel alias (`:staging`, `:prod`) pointing at the same digest; `latest` never used for release decisions.
2. Local dev parity: `compose.yaml` defines `app` (Spring Boot 4.1.1 jar), `db` (`postgres:18.6`, named volume `pgdata`, healthcheck `pg_isready`), and supporting services only. `cp .env.example .env`, then `docker compose up --build` or `podman-compose up --build` boots green; `down -v` resets cleanly.
3. Runtime contract: config exclusively via environment (12-factor; `SPRING_DATASOURCE_URL`, `APP_*`, provider keys); secrets mounted from vault/env files, never committed or logged. Stdout JSON logs, `/actuator/health` for liveness/readiness, graceful shutdown on SIGTERM.
4. Release independence: `docker run --env-file .env <image>:<tag>` and the `podman run` equivalent both reach ready state against an empty PG 18.6 (Flyway migrates on boot; no manual SQL; no seed data in prod). Rollback is re-running the previous tag against the same DB contract (migrations backward-compatible or explicitly flagged breaking).
5. Supply chain: provenance + SBOM per release image, scanned before promotion; registry retention keeps every official tag reproducible.

## Data / API touches

- Ports: app `8080` (host-mapped in dev; reverse-proxied in prod); db `5432` loopback-only in dev. Volumes: `pgdata` (PG data), object/blob mounts where the major requires them. No app-local state that must survive restarts except through these mounts.
- Endpoints: `/actuator/health/liveness`, `/actuator/health/readiness` wired to container `HEALTHCHECK` and orchestrator probes. Migration state queryable via Flyway info at startup log.
- Env template `.env.example` documents every variable with safe defaults; `.gitignore` covers `.env`, `.wrangler/`-era artifacts where still present, and compose overrides.

## Acceptance criteria

- Per-release drill: fresh hosts run the tag under both `docker run` and `podman run` to ready; compose cold boot green; previous-tag rollback green.
- Parity drill: same tag + same seed passes API contract tests in dev and staging; digests match (`inspect` compared).
- Config drill: missing required env fails fast with an actionable message; secret grep finds no keys in image layers or logs.
- Baseline: anonymous app routes throttled (S-5); health endpoints unauthenticated but data-free; actuator full surface authenticated.

## Out of scope

- Monolith-vs-service placement (see `platform/architecture/architecture-decision.md`); Spring/PG version internals (see `platform/stack/*`); K8s manifests and P5 replica/ISR execution (deferred to scale-out specs).

## Open questions

- Q1: Registry choice for official images (GHCR vs Docker Hub vs private) — decide before first tagged release?
- Q2: K8s/Compose-only for first prod deploy — minimal supported orchestrator in v1?
