# Resume Format Sharing - Local File Storage and Nginx (feature 19)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/19-resume-format-sharing/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `resume/gallery` | `./data/uploads/resume/gallery/{formatId}.png` | Nginx public `/media/resume/gallery` (immutable cache) | 30 days (re-renderable by the rescan job) |

## Write path

- Thumbnails are auto-generated placeholder renders only (D-7); the fixture-only pipeline renders PNG bytes, writes to `_tmp/`, then renames into the final path. No upload route exists; names are server-generated.
- `thumbnail_key` is set only after the rename succeeds.

## Serving

- Public static serving via the Nginx edge; unguessable names are the access control; no listing (`autoindex off`).
- Gallery visibility (`status='published'`/`removed`) is a service-side policy filter; bytes carry no PII and no user content.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down during render | Publish/re-scan proceeds with `thumbnail_key` null; the gallery shows the built-in glyph; rescan re-renders later |
| Temp file left by a crash | `_tmp` sweep at 24 h; no final file exists |
| Account purge | Participant deletes the namespace subtree by `user_id` with `files` counts |

## Compose delta

- `resume-service` mounts `./data/uploads/resume` rw at `/app/uploads/resume` (`APP_RESUME_GALLERY_DIR=/app/uploads/resume/gallery`); the Nginx edge mounts `/srv/media/resume/gallery` ro.
