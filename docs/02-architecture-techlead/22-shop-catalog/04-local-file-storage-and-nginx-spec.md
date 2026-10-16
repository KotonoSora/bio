# Shop Catalog - Local File Storage and Nginx (feature 22)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/22-shop-catalog/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `shop/covers` | `./data/uploads/shop/covers/{productId}.{ext}` | Nginx public `/media/shop/covers` (immutable cache) | product lifetime + 30 days |

## Write path

- Cover upload: bounded 2 MB read → magic-byte sniff (JPEG/PNG/WebP/AVIF; SVG rejected) → write to `shop/covers/_tmp/`, verify, then rename to the server-random final name. No client-chosen paths exist.
- Replace/delete updates the row first, then removes the old file best-effort; the sweep is the backstop.

## Serving

- Static serving via the Nginx edge; unguessable names are the access control; no listing.
- The anonymous shop page is edge-cached SSR; only `/media/shop/covers/*` is static.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Upload `503`; existing covers unaffected |
| Rejected upload | `422` per the feature code catalog; zero bytes land under the final namespace |
| Account purge | Participant deletes rows + cover files with `files` counts |

## Compose delta

- `shop-service` mounts `./data/uploads/shop` rw at `/app/uploads/shop` (`APP_SHOP_COVER_DIR=/app/uploads/shop/covers`); the Nginx edge mounts `/srv/media/shop/covers` ro.
