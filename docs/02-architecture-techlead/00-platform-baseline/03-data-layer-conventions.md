# Platform Baseline - Data Layer Conventions (PostgreSQL, Redis, Local Disk)

Phase 2 TL · Cross-cutting baseline. DEVIATION FLAGGED (in force since migration start): database-per-service - each service owns its schema, roles, migrations, and backups; the legacy single-PG/schema-per-module pattern is superseded. No cross-service joins, FKs, or shared tables; cross-service truth moves by API/event only (`01-architecture-and-service-topology.md`).

## PostgreSQL (18.6)

- Image digest-pinned; `SELECT version()` asserted at boot and in CI; extensions closed set: `pgcrypto`, `pg_trgm`, `citext` - additions recorded here by amendment.
- Flyway on boot per service; versions `V<major>` sequential within the service; backward-compatible default (expand/contract: add nullable -> backfill -> enforce -> drop later); a destructive migration needs a flagged amendment + restore point.
- Roles: `migrator` (DDL, migration only) and `app` (DML under RLS, no DDL, no superuser); app DSN never carries migration rights.
- Every business table: `id TEXT PK`, `user_id TEXT NOT NULL` + index, `created_at`/`updated_at timestamptz`, and RLS enabled. RLS policies key on the server-set request user (session variable per request); deny-by-default when unset. Money columns are VND integers. Soft-delete flags only where a feature says so; otherwise rows are immutable or hard-deleted in the same transaction as their dependents.
- Audit rows are append-only (`audit_log` shape recorded per service), 24-month retention, never edited or reused for business logic.
- Pooling: Hikari per service replica; pool sizes set in each service's application config (default 10) and never exceed the DB's connection budget; statement timeout 5 s.
- Durability: WAL archiving/PITR with a 7-day window; targets RPO <= 15 min, RTO <= 4 h; a quarterly restore drill to a scratch instance proves RTO and is logged. Backup/restore commands live with ops runbooks, not app code.

## Redis (7.4) - cache, counters, queues

- One platform instance; logical-db allocation table in `01-architecture-and-service-topology.md` (0-2 reserved edge/platform, 3+ sequential per service). A service touches only its own db.
- Key namespaces start with the service + domain (`rt:{profileId}:{minute}`, `seen:{profileId}:{visitorKey}`, `beacon:q`) and never collide across services; cross-service keys are forbidden - shared state moves through APIs/events.
- TTL discipline: every key class declares a TTL (no unbounded keys except explicit queue streams); eviction of a cache key is a correct zero, never an error path (features 12-14 rely on this).
- Queues use Redis Streams with consumer groups; consumers are idempotent (replay-safe), use `XAUTOCLAIM` for pending-entry reclaim, and enforce a max-delivery bound before parking to the dead-letter stream. Queue loss is degradable, never silent-data-loss: floor protections per service spec (e.g. analytics raw NDJSON flush).
- Counters for throttling/quotas are Redis-backed with TTLs bounded by their window; no unbounded counter growth.

## Local disk - file storage (no object-store tools)

- DEVIATION FLAGGED (storage amendment, 2026-10-16; owner directive): MinIO/S3/object-store tools are retired. Files live on the VPS disk via Docker bind mounts/named volumes; namespaces and serving rules in `09-local-storage-and-nginx-spec.md`.
- Carried-over rules: private by default, no listing anywhere; names are server-generated and paths never contain PII; retention is declared per namespace in the owning feature spec; a namespace without a declared retention cannot ship.
- Deletion: owner-initiated deletion removes files within the owning feature's SLA; erased profiles purge their namespaces via the owning service job (feature 02 participant).

## Cross-cutting rules

- A service restart must never need manual data repair: migrations self-apply, caches rebuild lazily, queues replay via consumer groups.
- Deploy ordering: migrations are backward-compatible one release back (rollback = previous image + current schema), per `06-release-gates.md`.
- Drills that gate releases: fresh-apply migration replay, restore-to-scratch, queue-replay collapse, cache-flush boot. Results are archived with the tag.
