# Themes and Avatar - Local File Storage and Nginx (feature 09)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/09-themes-and-avatar/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `profile/avatars` | `./data/uploads/profile/avatars/{ulid}.{ext}` | Nginx public `/media/profile/avatars/` (immutable, 1-year cache) | replaced on change; orphans swept 24 h |
| `profile/thumbnails` | `./data/uploads/profile/thumbnails/{ulid}.{ext}` | Nginx public `/media/profile/thumbnails/` | referenced by feature 08; swept with avatars |

Theme assets (`themes/{id}-{version}.{ext}`) are code-shipped bundle files, not uploads - they never touch the storage volume.

## Write path (avatars)

- Server-proxied multipart, bounded read 2 MB + 1 byte; the magic-byte sniff decides the type (JPEG/PNG/WebP/MP4 only); validation completes before any byte lands under the namespace.
- Accepted flow: server-generated name -> temp write + rename -> profile row update -> previous file delete -> audit -> event. A crash leaves one orphan file, never a blocked request (BR-07).
- Extension and claimed MIME are never trusted; rejected requests leave zero files.

## Serving

- Avatars are public by nature (profile pages): Nginx serves directly from the read-only mount with immutable cache headers; unguessable ULID names plus `autoindex off` are the access control; no listing endpoint exists.
- `X-Content-Type-Options: nosniff` on every response; the served content type is the sniffed type.

## Sweep and failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | upload `503`, zero row change; delete clears the row; the sweep retries the file |
| Orphan file (crash mid-flow) | daily sweep (`APP_AVATAR_SWEEP_CRON`) deletes unreferenced files older than 24 h; dry-run metric first |

## Compose delta

- `profile-service` mounts `./data/uploads/profile` rw at `/app/uploads/profile`; the Nginx edge mounts `./data/uploads/profile/avatars` ro at `/srv/media/profile/avatars`.
