# Publish Composer - Local File Storage and Nginx (feature 42)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/42-publish-composer/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `publish/media` | `./data/uploads/publish/media/{userId}/{mediaId}.mp4` + `{mediaId}.jpg` (poster thumbnail) | gated grants only | post lifetime + 30 days |

## Write path

- Resumable chunked upload: server-issued `uploadId` + part endpoints; parts land under `publish/media/_tmp/<uploadId>/`; finalize verifies size/hash/magic bytes (MP4/H.264, ≤ 300 MB, duration ≤ 3 min), then renames into the final path (atomic).
- File names are server-random; no client-chosen paths exist; incomplete uploads expire unpublishable and their temp dirs are swept at 24 h.

## Serving

- Preview playback is service-mediated: the service validates the grant (≤ 15 min) and answers `X-Accel-Redirect` into `/internal-media/publish/media/`.
- No static route, no listing, and no direct storage URL exists; only publish-service names files.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Uploads fail honestly (feature matrix codes); existing posts and dispatch unaffected |
| Rejected upload | Magic-byte/allowlist gates run before the queue; zero bytes land under the final namespace |
| Account purge | Participant deletes rows + files by `user_id` with `files` counts |

## Compose delta

- `publish-service` mounts `./data/uploads/publish` rw at `/app/uploads/publish` (`APP_PUBLISH_MEDIA_DIR=/app/uploads/publish/media`); the Nginx edge mounts that subtree ro behind the `internal` location.
