# Account Deletion - Local File Storage and Nginx (feature 02)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/02-account-deletion/` (BR-04, BR-05). Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `account/purge-certificates` | `./data/uploads/account/purge-certificates/{userId}/{runId}.json` | server-side reads only; no download endpoint exists | 24 months (audit parity), then sweep |

## Write path

- On full participant success, the runner writes the certificate JSON to `<namespace>/_tmp/<runId>.json`, verifies it, then renames into the final path; `certificate_ref` is set only after the rename succeeds.
- A write failure keeps the run retryable (`grace`); a certificate is never half-written or emitted without file verification.

## Reads

- Support and ops tooling read certificates through audited `account-service` paths only (S-4); no grants, no static routes, and no Nginx location exist for this namespace.
- Purge participants delete their own namespaces and report `files` counts (the certificate embeds them verbatim).

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume full/unavailable | run fails bounded-retryable; no certificate emitted; ops alert |
| Temp file left by a crash | `_tmp` sweep at 24 h; no final file exists |

## Compose delta

- `account-service` mounts `./data/uploads/account` rw at `/app/uploads/account`; the Nginx edge never mounts this namespace (not servable by design).
