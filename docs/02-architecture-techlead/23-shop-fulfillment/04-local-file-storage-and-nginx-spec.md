# Shop Fulfillment - Local File Storage and Nginx (feature 23)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/23-shop-fulfillment/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespaces

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `shop/artwork` | `./data/uploads/shop/artwork/{designId}/v{version}.{ext}` | gated grants (seller, ops, print partner) | order + dispute window |
| `shop/digital` | `./data/uploads/shop/digital/{productId}/v{fileVersion}/{fileId}.{ext}` | gated buyer download grants only | product lifetime |

## Write path

- Design upload: 20 MB cap, PNG/JPEG/WebP/PDF magic-byte allowlist → `_tmp/` → verify → rename; names server-random. Digital deliverables use the same temp + rename path.
- Versioned: superseding a design/file writes a new file name; rows pin versions; the old file is removed best-effort once unreferenced.

## Serving

- Downloads never expose a static path: the service validates the single-use grant (TTL ≤ 15 min, order + file version + buyer) and answers `X-Accel-Redirect` into `/internal-media/shop/digital/`; artwork previews likewise via `/internal-media/shop/artwork/`.
- No listing, no client-chosen names, no storage URL reaches a client.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Uploads `503`; downloads fail closed (no grant minted) |
| Guessed file name | Does not resolve; no directory listing |
| Account purge | Seller scope deletes rows + files; buyer scope scrubs buyer fields (D-8) |

## Compose delta

- `shop-service` mounts `./data/uploads/shop` rw at `/app/uploads/shop`; the Nginx edge mounts the two subtrees ro behind their `internal` locations.
