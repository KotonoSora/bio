# Platform Baseline - Containers and Compose

Phase 2 TL · Cross-cutting baseline. Every dev and official release ships as an OCI image runnable independently: `image + env + PG DSN` is sufficient (DEVIATION FLAGGED vs the legacy edge-only deploy default - container run is the release acceptance; Docker and Podman are interchangeable).

## Image contract

- Multi-stage `Containerfile`: Gradle `9.8.1` build stage (wrapper) produces the boot jar; runtime stage is Eclipse Temurin JRE 21, non-root user, minimal layers, no build tools, no secrets, no `.env` baked.
- One image per service, one image per web build (SPA shell + versioned MFE assets), plus the gateway and Nginx edge images; the platform DB/Redis/Nginx images are pinned upstream images (see `02-stack-and-build-pins.md`).
- Tags: `:<semver>-<gitsha>`; promotion is by digest only; `latest` never appears in staging/prod manifests; a tag without a signed release note (see `06-release-gates.md`) cannot promote.
- Runtime: config from environment only; secrets from env/vault; stdout JSON logs; `SIGTERM` graceful shutdown (drain in-flight requests, close queue consumers); read-only root filesystem where the service allows it.
- Health: `/health/liveness` + `/health/readiness` wired as compose/manifest healthchecks; readiness fails closed on unreachable own DB (queue/caches degrade instead where specs say so).

## Compose slice (single source for local + drill runs)

| Component | Image | Host port (dev) | Notes |
|---|---|---|---|
| gateway | platform build | 8080 (443 edge in prod) | Routes `/api/v1/*`, `/internal/v1/*`, `/:username`, `/go/*` |
| account-service | platform build | 8081 | Owns `account_db`; manages edge session keys (Redis db 2) |
| profile-service | platform build | 8082 | Owns `profile_db` |
| link-service | platform build | 8083 | Owns `links_db` |
| public-site-service | platform build | 8084 | Stateless (cache + purge only) |
| analytics-service | platform build | 8085 | Owns `analytics_db` (queue consumer + rollups) |
| resume-service | platform build | 8086 | Owns `resume_db`; archive files under `resume/archives` on the uploads volume |
| web | platform build | 8090 (dev only) | Shell + MFE build output baked into the Nginx edge image in prod |
| postgres | `postgres:18.6` (digest) | 5432 | One logical database per service on the shared cluster image in dev; separate instances in prod |
| redis | `redis:7.4` (digest) | 6379 | Logical dbs per `01-architecture-and-service-topology.md` |
| nginx | pinned upstream image + platform config | 80 / 443 | Serves the React SPA shell + MFE bundles + `/media/*` (read-only uploads subtrees); edge cache; proxies `/api/*`, `/go/*`, `/:username` to the gateway |

- `cp .env.example .env` then one compose command boots the full stack green under **both** `docker compose` and `podman-compose`; a clean-checkout boot completes within 5 minutes on the developer baseline machine.
- Startup ordering uses healthchecks (services wait for DB readiness); drill targets (k6, fixtures) run as one-shot compose profiles - never part of the serving stack.
- Port conflicts are resolved by env override; no service hard-codes a host port.
- Persistent state is bind-mounted under the data root: `./data/postgres`, `./data/uploads` (all files per `09-local-storage-and-nginx-spec.md`), `./data/logs` (rotated service logs); no object-store container exists anywhere in the slice.

## Podman parity

- Every release gate boots the same digests under Docker and Podman rootless and compares health + smoke output; a parity failure blocks promotion.
- Rootless caveats (volume ownership, port binding below 1024) are documented in `deploy/README` and must not change behavior between runtimes.
- Swarm/Kubernetes deployment is out of scope for v1; the compose slice plus manifests-by-digest is the contract.

## Configuration and secrets

- `.env.example` documents every variable with defaults; real `.env`, compose overrides, and secrets are never committed (repo grep gate in CI).
- Secrets (SSO client secrets, JWT signing keys, model/API keys, storage credentials) come from environment/vault; rotation is documented per secret and exercised in a drill before first production release.
- The internal JWT signing key is rotated with a dual-key window (old key verifies until expiry, new key signs) so services roll without downtime.

## Supply chain and release independence

- CycloneDX SBOM per image, generated in CI and archived with the tag; high/critical findings block promotion (exceptions require a written, time-boxed waiver in the release note).
- Provenance: build inputs (commit, wrapper checksum, catalog lock) recorded; images are reproducible from the tag alone.
- A single service redeploys with zero changes to others: migrations are backward-compatible one release (see `03-data-layer-conventions.md`), contracts are additive, and events are versioned.
- Rollback: previous digest + current schema is always a valid pair; rollback is a compose/manifest digest swap plus cache purge where specs declare one.
