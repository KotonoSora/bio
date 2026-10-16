# Profile Analytics - Local File Storage and Nginx (feature 12)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/12-profile-analytics/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `analytics/raw` | `./data/uploads/analytics/raw/y=YYYY/m=MM/d=DD/h=HH/part-<n>.ndjson` | server-side only; no HTTP surface exists | 90-day sweep (drain job) |

## Write path

- The hour drain flushes one NDJSON file per drain per shard: write to `analytics/raw/_tmp/<drainId>.ndjson`, verify, then atomic rename into the dated path.
- A flush failure is retried at the next drain; aggregates are unaffected (intake stays O(1) Redis ops). Partial files cannot exist (temp + rename invariant).

## Reads

- Raw files are read server-side only: ops forensics and the feature 31 repair path. Dashboards never read raw — any join is a layering violation.
- No grants, no static route, and no Nginx location exist for this namespace.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Flush retried next drain; aggregates continue; `rawgap` risk flagged by feature 31 |
| Temp file left by a crash | `_tmp` sweep at 24 h; no final file exists |

## Compose delta

- `analytics-service` mounts `./data/uploads/analytics` rw at `/app/uploads/analytics` (`APP_ANALYTICS_RAW_DIR=/app/uploads/analytics/raw`); the Nginx edge never mounts this namespace (not servable by design).
