# Social Connections - Architecture and Tech Stack (feature 41)

Phase 2 TL · Service: `publish-service` (new; port 8089, `publish_db`, Redis db 11 — hosts 41–44). Implements `docs/01-requirements-ba/41-social-connections/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| The connections vault (`social_accounts`), OAuth dances, health/refresh scheduler, per-plan caps, key rotation, ops health read | Plan identity for caps -> 35 (plan name via its internal read; ladder values in `publish.quotas`); account purge duty -> 02's purge rows; notification enqueue -> 32's `POST /internal/v1/notifications`; queue conventions -> baseline 03 (Redis Streams, consumer groups, XAUTOCLAIM, DLQ) |

Publish-service's own `audit_log` + `ops_config` land in V1 with the same shapes as billing's (33 TL02); no cross-service table access.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.publish.social`: `OAuthDance`, `TokenVault` (the only plaintext-touching code), `HealthScheduler`, `CapGuard`; consumers receive in-process leases (`TokenVault.lease(accountId, purpose)`) — never tokens in queue messages or HTTP | One audited crypto path; lease pattern satisfies BR-03 mechanically |
| D-2 | `V1__social_connections.sql`: `social_accounts` + `audit_log` + `ops_config` + restricted view `v_social_health` (excludes token columns) | Single DDL owner; ops visibility enforced at the DB level (BR-10) |
| D-3 | Crypto: AES-256-GCM envelope — per-row DEK wrapped by versioned master `TOKEN_ENC_KEY` (vault/env); `key_version` column; dual-read on rotation; re-wrap job batched ≤ 500 | Rotatable without deploy; ciphertext-only at rest |
| D-4 | OAuth: per-network apps; `state` single-use + PKCE (`S256`); callback verifies both before exchange; connect-start rate-limited | Redirector-abuse and CSRF safe |
| D-5 | Caps: `CapGuard` reads `publish.quotas` (free 1 / pro 10 per platform) and enforces at connect + target validation; downgrade blocks new connects/publishes, never deletes rows | Server-side, bypass-proof (BR-04) |
| D-6 | Health: states `healthy|expiring|expired|revoked|error`; refresh scheduler with per-network skew (default 24 h) + backoff ≤ 6; provider revoke webhooks (where offered) verified per the standard webhook discipline | Honest, bounded repair |
| D-7 | Disconnect: delete row + best-effort remote revoke (≤ 5 s timeout) + emit `connection.disconnected.v1` for 42's target cancellation | One atomic intent; consumers react |
| D-8 | Rotation: `ops_config` key-version flip (audited); dual-read window; re-wrap job idempotent per row; completion verified before old-key retirement | BR-09/NFR-08 |
| D-9 | Config: `publish.quotas` {`freeConnectionCap` 1, `proConnectionCap` 10, …} and `publish.policy` (see 42/44) in publish-service's `ops_config`, 5 s flips, audited | One tunable surface for the whole major |
| D-10 | E4 gate: zero publish tables/jobs/endpoints in E0–E3 images (release-gate grep audit per baseline 05) | The legacy P4 gate, mechanized |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | publish-service row (port 8089, `publish_db`, Redis 11, notes); public paths gain OAuth callbacks + IG webhook (lands with 42/44) |
| `MIGRATION.md` | service map row + batch 10 rows at the batch tail |
| 35 / 02 / 32 | No edits: plan read, purge row, and notification contract are consumed as-is |
