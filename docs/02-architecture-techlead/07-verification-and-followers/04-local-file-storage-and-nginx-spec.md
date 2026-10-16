# Verification Badge and Follower Display - Local File Storage and Nginx (feature 07)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/07-verification-and-followers/` (BR-03). Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `profile/evidence` | `./data/uploads/profile/evidence/verification/{ulid}.{ext}` | gated grants only (`ROLE_OPS`, <= 10 min); sandboxed origin | ops review cycle; purged on profile deletion (feature 02 participant) |

## Upload path

- `POST /api/v1/profile/verify-evidence/upload-grant` returns a short-TTL (10 min) upload permit for a server-generated name; the client PUT streams the file (single file, <= 10 MB, PDF/PNG/JPEG).
- The service validates magic bytes + size before any byte lands under the final namespace (temp + rename); rejected uploads leave zero files.
- Creators never re-read evidence; no creator-facing download path exists.

## Reads (ops only)

- The viewer grant is minted for `ROLE_OPS` only (same 10 min TTL); the service validates the grant and answers `X-Accel-Redirect` into the Nginx `internal` location `/internal-media/profile/evidence/`; bytes never render inline.
- No static route, no listing, and no object-style URL exists for this namespace.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | upload grant `503`; existing applications unaffected; no orphan files created |
| Viewer grant expired or replayed after TTL | `403/410` per the feature code catalog; no bytes served |

## Compose delta

- `profile-service` mounts `./data/uploads/profile` rw at `/app/uploads/profile` (`APP_EVIDENCE_DIR=/app/uploads/profile/evidence`); the Nginx edge mounts the evidence subtree ro behind the `internal` location above.
