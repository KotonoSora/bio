# Lead Search - Local File Storage and Nginx (feature 28)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/28-lead-search/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `profile/exports/leads` | `./data/uploads/profile/exports/leads/{userId}/{jobId}.csv` | gated grants only (owner-bound) | 7-day sweep |

## Write path

- The export worker streams the fixed CSV schema to `_tmp/{jobId}.csv`, verifies, then renames into the final path; the job flips to `done` and sets `file_path` only after the rename succeeds.
- Quota counters ride Redis db 4 as before; no PG write on the download path exists.

## Serving

- The download is service-mediated: the service validates the HMAC grant (`jobId|userId|exp`, 15 min, owner-bound) and answers `X-Accel-Redirect` into `/internal-media/profile/exports/leads/`.
- No static route, no listing, and no direct storage URL exists; non-owner requests never resolve.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Export retries; `failed` status after 3 attempts; search/detail unaffected; partial files never serve |
| Grant expired or replayed | `403/410` per the feature catalog; re-grant possible while the job lives |
| 7-day sweep / purge | Files + rows deleted; certificate counts files |

## Compose delta

- `profile-service` mounts `./data/uploads/profile` rw at `/app/uploads/profile` (`APP_LEAD_EXPORT_DIR=/app/uploads/profile/exports/leads`); the Nginx edge mounts that subtree ro behind the `internal` location.
