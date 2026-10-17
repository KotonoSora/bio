# Automate Contacts - Local File Storage and Nginx (feature 43)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/43-automate-contacts/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `publish/exports` | `./data/uploads/publish/exports/{userId}/{exportId}.csv` | gated grants only (owner-bound) | deleted at grant expiry (`<= 24 h`) |

## Write path

- Export builds the filter-aware CSV to `_tmp/{exportId}.csv`, verifies, then renames into the final path; the audit row (row count + filter class) is written only after the rename succeeds.
- Empty filters are refused before any write exists.

## Serving

- The service validates the owner-bound grant (TTL ≤ 24 h) and answers `X-Accel-Redirect` into `/internal-media/publish/exports/`.
- No static route, no listing, and no direct storage URL exists; the file is deleted at expiry.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Exports fail honestly; contact data unaffected |
| Export never downloaded | Expiry sweep deletes the file + audit notes the expiry |
| Account purge | Rows + files by `user_id` deleted with `files` counts |

## Compose delta

- `publish-service` mounts `./data/uploads/publish` rw at `/app/uploads/publish` (`APP_CONTACT_EXPORT_DIR=/app/uploads/publish/exports`); the Nginx edge mounts that subtree ro behind the `internal` location.
