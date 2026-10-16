# Platform Baseline - Local File Storage and the Nginx Edge

Phase 2 TL · Cross-cutting baseline. DEVIATION FLAGGED (storage amendment, 2026-10-16; owner directive): MinIO and every object-store tool are retired - no AWS, no S3, no MinIO, no CDN, no external service of any kind. All files live on the VPS disk via Docker bind mounts/named volumes and are served by the Nginx edge. Companion files: data-layer rules `03-data-layer-conventions.md`; compose rows `04-containers-and-compose.md`; pins `02-stack-and-build-pins.md`.

## Policy (closed set)

Permitted storage mechanisms only:

- Docker named volumes / bind mounts under the data root (`./data/...` on the host).
- Service writes via `java.nio.file` streaming (temp file + atomic rename); no object-store client library exists in any `build.gradle.kts`.
- Static serving and gated byte delivery via the platform Nginx container (`internal` locations for gated paths).
- Short-TTL download grants minted and validated by the owning service (HMAC, owner/role-bound, single-use where the feature says so).

Prohibited: any object-store daemon, storage SDK, or external service; client access to raw file paths; directory listing anywhere (`autoindex off`).

## Namespaces (registered; extended additively by the owning feature)

| Namespace (host `./data/uploads/...`) | Owner | Serving | Retention (sweep) |
|---|---|---|---|
| `account/purge-certificates` | account-service | server-side reads only | 24 months (audit parity) |
| `profile/avatars` | profile-service | Nginx public `/media/profile/avatars` | replaced on change; orphans 24 h |
| `profile/evidence` | profile-service | gated grants only | ops review cycle; purge on deletion |
| `profile/exports/leads` | profile-service | gated grants only | 7 days |
| `resume/archives` | resume-service | server-side reads only | `>= 90 days` |
| `resume/gallery` | resume-service | Nginx public (placeholder renders) | 30 days |
| `shop/covers` | shop-service | Nginx public `/media/shop/covers` | product lifetime + 30 days |
| `shop/artwork` | shop-service | gated (buyer/print partner) | order + dispute window |
| `shop/digital`, `shop/courses` | shop-service | gated grants only | product lifetime |
| `links/qr` | link-service | gated grants only | 30 days (re-renderable) |
| `links/hit-raw` | link-service (write), analytics-service (repair) | server-side only | 90 days |
| `analytics/raw` | analytics-service | server-side only | 90 days |
| `publish/media` | publish-service | gated preview grants | post lifetime + 30 days |
| `publish/exports` | publish-service | gated grants only | 24 h (deleted at grant expiry) |

- A namespace without a declared retention cannot ship; retention runs as a scheduled sweep inside the owning service (counts + lag metrics logged).
- Names are server-generated (ULID-random, strict extension allowlist); paths never contain PII.

## Serving classes

- Public media: Nginx reads a read-only mount (`/srv/media/<namespace>`) directly; immutable cache headers on content-keyed names; unguessable names are the access control; no listing.
- Gated bytes: never a static path. The owning service validates the download grant, then answers `X-Accel-Redirect` into an Nginx `internal` location bound to its namespace; grant TTLs and single-use rules stay per feature spec. This replaces "presigned URL" semantics everywhere: TTL, owner binding, and counters unchanged.

## Uploads (resumable, local)

- Server-issued `uploadId` + chunk endpoints; parts land under `<namespace>/_tmp/<uploadId>/`; finalize verifies size/hash/magic bytes, then renames into the final path (atomic); abandoned temp dirs swept at 24 h.
- Validation gates (magic bytes, allowlists, size caps) run before any byte lands under the final namespace; client-chosen names or paths do not exist.

## Failure and durability

- Volume full/unavailable: uploads `503 storage_unavailable`; reads degrade per the feature matrix; partial final files cannot exist (temp + rename invariant).
- Account deletion: purge participants delete their namespace subtree and report counts; owner deletes are best-effort with sweep backstop.
- At-rest protection is host-level: service-uid ownership, `0700` directories, optional VPS disk encryption; no storage credentials exist - the mount is the capability.

## Nginx edge (compose slice)

- Container: pinned upstream Nginx image + platform config; ports 80/443; mounts the SPA build output, `/srv/media` (read-only upload substrees), and a cache-dir volume.
- Responsibilities: serve the React 19.3 SPA + `/media/*`; proxy `/api/*`, `/go/*`, `/:username` to the gateway; edge cache (`proxy_cache`) for public reads; security headers; `limit_req`. The gateway keeps session termination, internal JWT signing, and route-class rate rules.
- Degrade: Nginx down = full outage (single edge on a single VPS); its config boots under both Docker and Podman as release acceptance (`06-release-gates.md`).
