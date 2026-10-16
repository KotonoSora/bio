# Social Connections - Step-by-Step Implementation Plan (feature 41)

Phase 2 TL · Package `com.app.publish.social`. BA sources: `docs/01-requirements-ba/41-social-connections/`. Order: schema + vault first — nothing else may touch credentials before the crypto path exists.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V1__social_connections.sql`: `social_accounts` + `audit_log` + `ops_config` + `v_social_health` + RLS + drills | service scaffold | V1 drills green |
| T-02 | BE | `TokenVault`: seal/unseal (AES-256-GCM envelope), key versions, dual-read, `lease(accountId, purpose)`; grep gate: single crypto module | T-01 | AC-01, AC-06 |
| T-03 | BE | `OAuthDance`: per-network apps, `state`+PKCE start, callback verification, idempotent upsert, scopes-minimal | T-02 | AC-07 |
| T-04 | BE | `CapGuard` + `publish.quotas` config (free 1 / pro 10; plan read from 35) | T-01 | AC-02, AC-03 |
| T-05 | BE | `HealthScheduler`: skew refresh, backoff ≤ 6, health transitions, `connection.dead.v1` emission | T-02 | AC-04 |
| T-06 | BE | Disconnect: delete + remote revoke attempt + `connection.disconnected.v1` + response count | T-02 | AC-05 |
| T-07 | BE | Rotation: key-version flip (audited), re-wrap job (≤ 500/batch, idempotent), completion gate | T-02 | AC-06 |
| T-08 | BE | Revoke webhooks `POST /webhooks/social/{network}` (verified) → health transitions | T-05 | AC-04 |
| T-09 | FE | Connections page: network cards, health chips, caps, reconnect/disconnect confirm flows | T-03, T-06 | UI flows complete |
| T-10 | BE/DevOps | Cross-edits: baseline 01 service row + public paths; MIGRATION at batch tail | T-01 | Diff review green |
| T-11 | BE/DevOps | Drills: vault grep gate, cap race, callback replay matrix, rotation with live refresh, restricted-view probes | T-02..T-08 | AC-01..AC-09 |
| T-12 | DevOps | Metrics + alerts (health by network, refresh outcomes, revoke detections, cap refusals, rotation progress, secret age) | T-05..T-11 | NFR-13 |

## Sequencing notes

1. T-01/T-02 gate everything: no OAuth code path exists before the vault module does; the grep gate lands with T-02.
2. T-04 consumes 35's plan read only — publish-service never copies plan tables.
3. T-07 rotation drills include a live refresh mid-re-wrap (zero failures is the acceptance).
4. Feature gate: E4 scope audit — zero publish tables/jobs/endpoints in E0–E3 images (release-gate grep).

## Definition of done

AC-01..AC-09 green; vault grep gate green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 41 flips to `done` at the batch tail with `publish/accounts/social-connections.md` deleted.
