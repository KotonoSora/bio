# Resume Documents - Local File Storage and Nginx (feature 17)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/17-resume-documents/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `resume/archives` | `./data/uploads/resume/archives/{document_id}.ndjson` | server-side reads only; no HTTP surface exists | `>= 90 days` (retention sweep) |

## Write path

- The archive job appends one JSON line per version; the first create writes to `_tmp/<document_id>.ndjson`, verifies, then renames into the final path (move-then-delete: the PG delete happens only after the file write is verified).
- Appends are single-writer (Redis db 8 lock); a crashed append leaves the previous good lines intact.

## Reads

- Archive bytes are read server-side only (restore path); no grants, no static route, and no Nginx location exist for this namespace.
- The purge participant deletes the namespace subtree by `user_id` and reports `files` counts.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Archive job pauses (nothing deleted); retry completes |
| Crash between file write and PG delete | Retry re-verifies the file then deletes; no data loss |
| Temp file left by a crash | `_tmp` sweep at 24 h; final path never references it |

## Compose delta

- `resume-service` mounts `./data/uploads/resume` rw at `/app/uploads/resume` (`APP_RESUME_ARCHIVE_DIR=/app/uploads/resume/archives`); the Nginx edge never mounts this namespace.
