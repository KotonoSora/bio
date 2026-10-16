# Short Links - Local File Storage and Nginx (feature 29)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/29-short-links/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `links/hit-raw` | `./data/uploads/links/hit-raw/<yyyy>/<mm>/<dd>/*.ndjson` | server-side only; no HTTP surface exists | 90-day sweep |

## Write path

- At resolve, the ~60 s batched flush writes each part to `links/hit-raw/_tmp/<partId>.ndjson`, verifies, then renames into the dated path. Partial files cannot exist (temp + rename invariant).
- The redirect path itself stays untouched: `/go/*` responses are `no-store` and never wait on the flush.

## Reads

- Raw files are read server-side only: feature 31's repair/replay consumer. Dashboards never read raw.
- No grants, no static route, and no Nginx location exist for this namespace.
- Account purge deletes rows + files by `user_id` with `files` counts.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Flush retries; resolve unaffected; feature 31 flags `rawgap` risk until raw is verifiable |
| Crash mid-flush | `_tmp` sweep at 24 h; dated path never references a partial part |
| 90-day sweep | Old dated directories deleted with counts + lag metrics logged |

## Compose delta

- `link-service` mounts `./data/uploads/links` rw at `/app/uploads/links` (`APP_LINK_HITRAW_DIR=/app/uploads/links/hit-raw`); `analytics-service` also mounts it rw for repair + markers (feature 31 spec); the Nginx edge never mounts it.
