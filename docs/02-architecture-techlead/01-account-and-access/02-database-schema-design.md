# Account and Access - Database Schema Design (feature 01)

Phase 2 TL · `account_db` (PostgreSQL 18.6, per-service, Flyway-on-boot, RLS on). BA sources: `docs/01-requirements-ba/01-account-and-access/` (BR-02, BR-03, BR-06, BR-11). Conventions: `docs/02-architecture-techlead/00-platform-baseline/03-data-layer-conventions.md`.

Roles: `migration_role` owns DDL; `app_role` is RLS-limited (no DDL). TEXT ids; timestamps `timestamptz`; no money columns; no cross-service FKs. `Flyway V1__account_core.sql` creates everything below.

## DDL

```sql
CREATE TABLE users (
  id             TEXT PRIMARY KEY,
  email          TEXT,                      -- IdP-verified sync target
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  email_conflict BOOLEAN NOT NULL DEFAULT FALSE,
  contact_email  TEXT,
  contact_verified_at TIMESTAMPTZ,
  name           TEXT,
  image_key      TEXT,                      -- avatar prefill target (feature 09 rules)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE linked_accounts (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider_id   TEXT NOT NULL,
  provider_sub  TEXT NOT NULL,
  email_at_link TEXT,
  linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT linked_unique UNIQUE (provider_id, provider_sub)
);

CREATE TABLE sessions (
  sid                 TEXT PRIMARY KEY,     -- opaque, >= 128 bit
  user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at          TIMESTAMPTZ NOT NULL,
  last_active_at      TIMESTAMPTZ NOT NULL,
  auth_time           TIMESTAMPTZ NOT NULL, -- step-up freshness source
  idle_expires_at     TIMESTAMPTZ NOT NULL, -- 7 d sliding
  absolute_expires_at TIMESTAMPTZ NOT NULL, -- 90 d hard stop
  device_label        TEXT,                 -- coarse UA family only
  ip_class            TEXT,                 -- hashed /24 (v4) or /48 (v6)
  revoked_at          TIMESTAMPTZ
);

CREATE TABLE audit_log (                    -- append-only; no UPDATE/DELETE grant
  id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id         TEXT NOT NULL,            -- actor
  subject_user_id TEXT,                     -- subject when different
  action          TEXT NOT NULL,            -- login|link|unlink|step_up|recovery|session_revoke|contact_email|role
  target          TEXT,
  at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_class        TEXT
);
```

Indexes: `linked_accounts (user_id)`; `sessions (user_id, revoked_at)`, `sessions (idle_expires_at)` for sweeper; `users lower(email)` (collision checks; non-unique by design — BR-02); partial unique `users (lower(contact_email)) WHERE contact_verified_at IS NOT NULL` (BR-03).

## RLS and access paths

- Deny-by-default on all four tables (`current_setting('app.user_id', true)` keyed). Owner predicate: `users.id = app.user_id`, else `user_id = app.user_id`.
- Two server-set service contexts, never client-influenced: `app.context = 'svc'` for internal handlers (introspect by `sid`, entitlement by `userId`, purge by `userId` — each handler applies its own equality predicate), and `app.context = 'ops'` for the audited recovery-link route (existence + delivery target only; response is a token).
- `audit_log`: INSERT/SELECT grants only; 24-month retention (baseline); audit rows survive account purge (feature 02).

## Redis keys (own db 7)

| Key | TTL | Semantics |
|---|---|---|
| `acct:authstate:{state}` | 600 s | Single-use (GETDEL) `{provider, nonce, codeVerifier, callbackURL, mode: login\|link\|step_up}` |
| `acct:cemail:{userId}` | 900 s | Contact-email code hash + attempt counter (max 5) |
| `acct:relink:{token}` | 900 s | Single-use `{userId, evidenceRef}` for recovery invitations |
| `acct:slact:{sid}` | 3600 s | Coalesces `last_active_at` writes (<= 1/hour/session) |

Edge contract (platform db 2, managed here, read-only for the gateway): `edge:session:{sid}` hash `{userId, authTime, absExp}`, TTL = min(idle remaining, absolute remaining); refreshed by the gateway on activity; deleted on revoke/logout; rebuilt by introspect on miss. Loss of db 2 is a cache loss, never a grant.

## Deletion and retention semantics (consumed by feature 02)

Purge hard-deletes `users`, `linked_accounts`, `sessions` rows for the account (idempotent per `user_id`); `audit_log` rows are retained minimized (append-only, actor/target text is the proof). No soft-delete flags here: deactivation state lives in feature 02's `deletion_requests` and gates access at the session layer.

## Verification

1. Fresh apply: `flyway migrate` clean on empty 18.6; both unique constraints and the partial index present.
2. RLS drill: `SET app.user_id` to userA returns zero userB rows on all four tables; unset context returns zero rows.
3. Constraint probe: duplicate `(provider_id, provider_sub)` rejected; duplicate verified contact email rejected; collision on `email` accepted with flag.
4. Sweeper test: expired idle/absolute sessions revoked; edge key deleted; revocation visible `<= 60 s` (BR-06).
