# Postgres Baseline (LTS 18.6, containerized primary)

PLAN ref: §0 Database decision (Neon + Hyperdrive, RLS on, PITR, least-privilege roles; D1 disqualified; Single-PG rule) + P5-1/P5-4 (replicas, restore drill). DEVIATION FLAGGED: primary is containerized PostgreSQL LTS `18.6` (same version dev→prod) instead of Neon scale-to-zero. RLS, PITR, single-PG rule, and size math are retained.

## Purpose

Give every environment one durable truth with identical semantics: same major version, same extensions, same RLS posture, same migration path. Eliminate works-on-my-PG drift between laptop, staging, and official releases.

## Users

- Backend developers: develop against the compose `db` service, never an embedded substitute for grant-path tests.
- Ops / Security: backup, restore, rotate, and audit one database.

## Functional requirements

1. Version pin: image `postgres:18.6` digest-pinned in compose and prod manifests; `SELECT version()` asserted at boot and in CI. Extensions closed set (record additions in-spec): `pgcrypto`, `pg_trgm`, `citext` (usernames/search); others need justification.
2. Topology: one logical database, per-module schemas (`resume`, `bio`, `billing`, `shop`, `leads`); single-PG rule holds — no Mongo/MySQL/second OLTP. Growth killers stay out of PG per PLAN §0 (aggregates only for clicks/views, `snapshot_json` history >30d to blob, raw logs to blob with TTL).
3. Security posture: RLS enabled on every business/ledger table plus app-level `user_id` scoping (both, always); runtime app role (least privilege) vs migration role; `user_id TEXT NOT NULL` + index everywhere; reserved-slug/username checks in app + DB constraint. Secrets (app/migration passwords) via env/vault, rotated quarterly.
4. Durability: PITR via WAL archiving + scheduled base backups to durable blob (target RPO/RTO recorded before first prod deploy); quarterly restore drill per P5-4 retained. Migrations via Flyway only, backward-compatible by default; breaking migrations flagged and paired with app compatibility window.
5. Operations: Hikari sizing + 5s statement timeout on OLTP paths; single-flight/coalescing on hot public reads; slow-query log reviewed at each load gate.

## Data / API touches

- Connection: `SPRING_DATASOURCE_URL` (JDBC), pool metrics via Micrometer; migrations in `db/migration/` owned by the Spring baseline. No direct DB from clients or beacons — queue/consumer or server path only.
- DDL conventions: TEXT ids, `timestamptz` ISO8601, `amount_vnd INT`, `orderCode` numeric int64 UNIQUE where billing requires; audit-relevant transitions append to `audit_log`.
- Compose volume `pgdata` for data; WAL/blob destinations configured per environment, never on the container writable layer for prod durability.

## Acceptance criteria

- Version drill: all envs report `18.6`; extension list matches the closed set.
- RLS drill: direct PG connection as app role reading another user's row → denied (S-6); app-level IDOR probe 403 (S-1).
- Durability drill: base backup + WAL replay restores to a point-in-time on a clean volume; quarterly drill logged.
- Gate drill: size math re-checked at each exit (20–50KB/user → 200–500GB at 10M); overflow paths (aggregates/blob offload) evidenced, not just documented.

## Out of scope

- Read replicas / ISR pre-render (P5 scale-out specs); Spring/Hikari tuning beyond the defaults above (service specs); non-Postgres stores (rejected by single-PG rule).

## Open questions

- Q1: WAL/blob destination for PITR (R2-compatible vs volume snapshots) — confirm before first prod deploy?
- Q2: Compose `db` vs external managed PG for staging — keep container-primary everywhere or allow managed staging?
