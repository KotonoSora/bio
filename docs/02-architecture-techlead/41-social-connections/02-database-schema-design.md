# Social Connections - Database Schema Design (feature 41)

Phase 2 TL · `publish_db` migration `V1__social_connections.sql` (first migration of the service; hosts 41–44). BA sources: `docs/01-requirements-ba/41-social-connections/`.

## Flyway V1 (`V1__social_connections.sql`)

```sql
CREATE TABLE social_accounts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  network TEXT NOT NULL CHECK (network IN
    ('youtube','instagram','tiktok','facebook','x','threads','linkedin')),
  account_ref TEXT NOT NULL,
  access_token_enc BYTEA NOT NULL,
  refresh_token_enc BYTEA,
  key_version INT NOT NULL,
  scopes TEXT[] NOT NULL,
  expires_at TIMESTAMPTZ,
  health TEXT NOT NULL DEFAULT 'healthy'
    CHECK (health IN ('healthy','expiring','expired','revoked','error')),
  last_healthy_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_sa_account UNIQUE (profile_id, network, account_ref)
);
CREATE INDEX idx_sa_user ON social_accounts (user_id);
CREATE INDEX idx_sa_health ON social_accounts (health, expires_at);

-- audit_log + ops_config: same shapes as billing's V1 (33 TL02); append-only, 24-month retention

CREATE VIEW v_social_health AS
  SELECT id, user_id, profile_id, network, account_ref, scopes, expires_at,
         health, last_healthy_at, updated_at
  FROM social_accounts;
-- token columns are absent from the view by construction (BR-10)
```

## RLS and grants

```sql
ALTER TABLE social_accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY sa_owner ON social_accounts FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY sa_svc ON social_accounts FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('service','ops'))
  WITH CHECK (current_setting('app.context', true) IN ('service','ops'));
GRANT SELECT, INSERT, UPDATE ON social_accounts TO app_role;   -- DELETE via disconnect only
GRANT SELECT ON v_social_health TO app_role;
```

Owner reads go through the restricted view or column-limited selects; ops contexts read `v_social_health` only — token columns have no ops grant path.

## Semantics

- Vault write path: `TokenVault.seal(plaintext) -> (ciphertext, keyVersion)` inside the connect/refresh transaction; unseal only in `TokenVault.lease` for a named purpose (`publish`, `refresh`, `automation`); leases are in-process, short-lived, and never serialized.
- Health transitions: `healthy → expiring` by skew; `expiring → expired` on refresh auth failure; `revoked` on provider signals; `error` on GCM tag mismatch (never guessed). Every flip audits (`connection.health_flip`) and dead states emit `connection.dead.v1` for 42's target cancellation.
- Connect upsert: same `(profile_id, network, account_ref)` refreshes credentials + scopes idempotently (`uniq_sa_account`), audited as `connection.refreshed` vs `connection.created`.
- Cap check inside the connect transaction: `COUNT(*) WHERE profile_id + network` vs `publish.quotas` value; refusal audited (`connection.cap_refused`).
- Rotation: re-wrap selects `WHERE key_version < :newVersion` batches ≤ 500 `FOR UPDATE SKIP LOCKED`, decrypt-old → encrypt-new, same transaction; idempotent; `ops_config` flip audited; old key retires only after `COUNT(*) WHERE key_version < :newVersion = 0`.
- Disconnect: `DELETE` + best-effort remote revoke + `connection.disconnected.v1` post-commit (queued-target cancellation is 42's consumer duty).
- No new Redis keys: connect-start throttle uses `pub:rl:*` (Redis db 11).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V1 runs clean; table + view + RLS visible; audit_log/ops_config shapes match billing's V1 |
| Ciphertext only | Dump inspection: token columns contain GCM ciphertext + `key_version`; no plaintext path exists |
| Log hygiene | Captured connect/refresh/publish flows: zero token material (grep gate) |
| Restricted view | Ops context querying `social_accounts` token columns → permission denied; `v_social_health` returns metadata only |
| Rotation | Dual-read window: new writes use the new version; re-wrap completes idempotently; zero publish failures in the drill |
| Cap race | Concurrent connects at the cap boundary: exactly the allowed count persists; refusals audited |
| Single crypto module | grep: no AES/GCM calls outside `TokenVault` |
| RLS probes | Cross-user reads zero rows; owner reads own rows only; service context behaves per policy |
