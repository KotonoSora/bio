# Short-Link Analytics - Local File Storage and Nginx (feature 31)

Phase 2 TL · Storage spec (amendment A2, 2026-10-16; MinIO retired per owner directive). BA sources: `docs/01-requirements-ba/31-short-link-analytics/`. Platform rules: `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.

## Namespace

| Namespace | Host path | Serving | Retention |
|---|---|---|---|
| `links/hit-raw` (shared with feature 29) | `./data/uploads/links/hit-raw/<yyyy>/<mm>/<dd>/*.ndjson`; markers `./data/uploads/links/hit-raw/.state/<day>.done` | server-side only; no HTTP surface exists | 90-day sweep |

## Write path

- link-service writes raw parts (feature 29 spec); this consumer writes only `.state/<day>.done` markers after a verified drain, and repair rewrites bad parts via `_tmp/` + atomic rename.
- A marker is never written before the day's parts verify; completeness is the marker's only meaning.

## Serving

- Server-side only: repair/replay reads raw parts directly. No grants, no static route, and no Nginx location exist.
- Dashboards read aggregates only — never raw files.

## Failure handling

| Condition | Behavior |
|---|---|
| Storage volume down | Repair unavailable; aggregates keep serving; `rawgap` risk flagged until raw is verifiable; no counting loss |
| Corrupt part | Repair rewrites from the Redis stream replay; marker withheld until verified |
| 90-day sweep | Dated directories + markers deleted; aggregates untouched |

## Compose delta

- `analytics-service` mounts `./data/uploads/links` rw at `/app/uploads/links` (repair + markers); the Nginx edge never mounts this namespace.
