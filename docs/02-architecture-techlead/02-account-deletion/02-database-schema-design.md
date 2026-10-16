# Account Deletion - Database Schema Design (feature 02)

Phase 2 TL · `account_db` migration `V2__deletion_requests.sql` (feature 01 owns `V1`). BA sources: `docs/01-requirements-ba/02-account-deletion/` (BR-04, BR-05, BR-08, BR-09). Platform purge participant details live in each service's schema file.

## DDL

```sql
CREATE TABLE deletion_requests (
  id               TEXT PRIMARY KEY,
  user_id          TEXT NOT NULL UNIQUE,     -- logical reference to users(id); no FK (row outlives purge)
  status           TEXT NOT NULL CHECK (status IN ('grace','purged','cancelled')),
  requested_at     TIMESTAMPTZ NOT NULL,
  purge_due_at     TIMESTAMPTZ NOT NULL,     -- requested_at + 30 d
  purge_started_at TIMESTAMPTZ,              -- set on first run attempt
  purged_at        TIMESTAMPTZ,
  cancelled_at     TIMESTAMPTZ,
  reminder_sent_at TIMESTAMPTZ,              -- day 23; single send guard
  certificate_ref  TEXT,                     -- purge-certificates/{userId}/{runId}.json
  run_id           TEXT                      -- completing run
);

CREATE INDEX idx_deletion_due ON deletion_requests (purge_due_at) WHERE status = 'grace';
```

RLS: enabled; owner predicate `user_id = current_setting('app.user_id', true)` for read/insert/cancel; the runner uses the server-set `app.context = 'svc'` scope (feature 01 pattern) with no browser path. No UPDATE/DELETE grants beyond `status`/timestamp transitions by the owning service; the row is retained after purge (minimized proof: ids, dates, counts reference only - no email, no name).

## Retention behavior

| Row class | After purge | Note |
|---|---|---|
| `deletion_requests` (this table) | Retained (status `purged`, `certificate_ref`) | The deletion record; ids and timestamps only (NFR-10) |
| `users`, `linked_accounts`, `sessions` (feature 01) | Hard-deleted by this service's own purge handler | Counts returned to the runner |
| `audit_log` (feature 01) | Retained | Append-only baseline; deletion events included |
| Financial lines (features 32-40) | Retained minimized | Amounts/dates intact; buyer email hashed per Q-01 config |
| Legal-hold evidence | Retained while held | Certificate notes the hold (BR-05) |

## Participant contract data

Each participant returns `{tables: Map<String, Long>, files: Long}` from its purge handler; the certificate embeds these verbatim plus `userId`, `runId`, `startedAt`, `finishedAt` (schema in `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md`). Certificates are JSON files under `account/purge-certificates/` on the storage volume, server-side only (no download endpoints exist).

## Redis keys (db 7)

| Key | TTL | Purpose |
|---|---|---|
| `acct:job:purge` | run duration | Single-flight lock for the daily runner (SET NX PX, token-checked release) |
| `acct:delrem:{userId}` | 25 h | Reminder send guard mirror (DB `reminder_sent_at` is truth) |

Consumer-side caches (owned by their services): `profile.acctstat:{userId}` 60 s in profile-service Redis db 4 - resolve/sitemap parity; referenced in `docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md`.

## Verification drills

1. Fresh apply: `V2` clean on an 18.6 container; both CHECK constraints, partial index, and RLS policies present; re-apply idempotent (Flyway).
2. Uniqueness: a second `deletion_requests` row for the same user is rejected (`user_id UNIQUE`); cancel then re-request reuses the same row and resets `requested_at`/`purge_due_at` (prior cancel preserved in the audit trail).
3. Runner drill: due query returns only `grace` rows past `purge_due_at`; single-flight lock blocks a second runner process.
4. Retention probe: after a test purge, row exists with `certificate_ref`; no email/name columns exist to leak.
