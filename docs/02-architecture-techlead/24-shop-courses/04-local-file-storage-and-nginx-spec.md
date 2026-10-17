# Course Builder - Local File Storage and Nginx (feature 24)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/24-shop-courses/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `shop/courses` | `./data/uploads/shop/courses/videos/{lessonId}.mp4`; `./data/uploads/shop/courses/materials/{lessonId}/{materialId}.{ext}` | gated grants only (enrollment-bound) | product lifetime |

## Write path

- Video upload: MP4/H.264 allowlist, ≤ 300 MB, bounded probes; materials: digital allowlist ≤ 500 MB each, ≤ 10 per lesson, no SVG/HTML. All writes go `_tmp/` → verify → rename; names server-random.
- Incomplete uploads never publish; row updates follow the verified rename only.

## Serving

- Streams and material downloads are service-mediated: the ticket is bound to enrollment + lesson, then the service answers `X-Accel-Redirect` into `/internal-media/shop/courses/`.
- No listing; guessed file names do not resolve; no storage URL reaches a client.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Uploads `503`; stream/material mints `503 stream_unavailable`; pages unaffected |
| Rejected upload | Feature error codes; zero bytes under the final namespace |
| Account purge | Seller scope deletes curriculum + files; buyer scope removes enrollments/progress (D-9) |

## Compose delta

- `shop-service` mounts `./data/uploads/shop` rw at `/app/uploads/shop`; the Nginx edge mounts `/srv/media/shop/courses` ro behind the `internal` location.
