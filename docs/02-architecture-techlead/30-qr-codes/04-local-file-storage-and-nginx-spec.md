# QR Codes - Local File Storage and Nginx (feature 30)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/30-qr-codes/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `links/qr` | `./data/uploads/links/qr/{userId}/{renderId}.{png\|svg}` | gated grants only (owner-bound) | 30 days (re-renderable cache) |

## Write path

- A render writes both artifacts to `_tmp/`, verifies, then renames into the final path; the log row is inserted only after both renames succeed (`object failure → no row` semantics hold as `file failure → no row`, `503 render_unavailable`).
- Names are server-derived (`renderId`); no client-chosen paths exist.

## Serving

- The service validates the owner-bound HMAC grant (`renderId|userId|exp`, 15 min) and answers `X-Accel-Redirect` into `/internal-media/links/qr/`; downloads are service-mediated streams.
- No static route and no listing exist; direct-guess probes return nothing.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | `503 render_unavailable`; no log row without stored bytes; re-render later |
| Missing/expired file | Re-renders from the log row's code (bytes are cache, the log is history) |
| Account purge | Rows + files under `links/qr/{userId}/` deleted with `files` counts |

## Compose delta

- `link-service` mounts `./data/uploads/links` rw at `/app/uploads/links` (`APP_LINK_QR_DIR=/app/uploads/links/qr`); the Nginx edge mounts that subtree ro behind the `internal` location.
