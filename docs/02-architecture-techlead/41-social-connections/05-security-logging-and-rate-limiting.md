# Social Connections - Security, Logging, and Rate Limiting (feature 41)

Phase 2 TL · BA sources: `docs/01-requirements-ba/41-social-connections/` (BR-01..BR-12, NFR-01..NFR-13). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Crypto confinement: `TokenVault` is the only plaintext-touching module (seal/unseal/lease); AES-256-GCM envelope; versioned master key via vault/env; no plaintext fallback exists | Single-module grep + ciphertext dump drill |
| S-2 | Log hygiene: tokens never in logs, errors, queue messages, analytics, events, or audit detail; leases are in-process and non-serializable | Captured-flow grep gate (AC-01) |
| S-3 | Ops exclusion: ops reads use `v_social_health`; token columns have no ops grant path; attempts are permission errors, not masked outputs | Restricted-view probes |
| S-4 | OAuth integrity: single-use `state` + PKCE S256; callback verification before exchange; connect-start rate-limited (redirector abuse) | AC-07 matrix |
| S-5 | Cap enforcement: server-side at connect and dispatch; direct API cannot bypass; refusals audited | AC-02 cap race |
| S-6 | Rotation safety: dual-read ≥ 24 h, idempotent re-wrap, old key retires only after zero-remaining verification; quarterly drill | AC-06 |
| S-7 | Fail-closed vault: key unavailable → connect/refresh/publish paths refuse honestly; dead states block targets loudly (never silent drops) | AC-09 + degrade drill |
| S-8 | Scoping: owner RLS on rows; cross-user reads zero rows; service context restricted to the vault purpose paths; disconnect is owner-only | RLS probes |
| S-9 | Provider signals: revoke webhooks verified per the standard discipline (signature, replay, tamper battery); unknown payloads refuse | Webhook battery |
| S-10 | Privacy: account refs are non-secret labels; no provider PII beyond the ref is stored; ops views mask refs | Data-minimization audit |

## Rate limits (gateway + app; Redis db 11 `pub:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| `POST /api/v1/social/{network}/connect` | 20/h/user + 60/h/IP | 429 `throttled` |
| `GET /api/v1/social/{network}/callback` | 60/h/IP | 429; state stays single-use |
| `GET /api/v1/social/accounts` | 120/h/user | 429 |
| `DELETE /api/v1/social/accounts/{id}` | 20/h/user | 429 |
| `POST /webhooks/social/{network}` | 600/min/IP + 120/min per network key | Refusal + clamp |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Provider OAuth down | Connect attempts fail honestly; existing connections unaffected; no queue of half-dances exists |
| Master key unavailable | Vault operations fail closed (`vault_unavailable`); publishing halts loudly, never with stale plaintext |
| Redis down | Rate limits degrade to conservative fixed windows; correctness unchanged |
| Health scheduler down | Connections go `expiring` visibly; no silent publishes with expired tokens (dispatch re-checks) |
| Notification pipeline down | Creator notices queue with backlog; connection state changes unaffected |

## Audit and observability

Audits (24-month): `connection.created/refreshed/cap_refused/health_flip/disconnected`, `vault.rotation_started/completed`, `webhook.rejected` (class only). Metrics: `connections{network,health}`, `refresh{outcome,network}`, `revokes_detected`, `cap_refusals`, `rotation_progress_pct`, `secret_age_days`. Zero token material and zero raw handles in labels.

## Support answers

"Why can't I connect?" → cap state + upgrade path; IG personal-account explainer. "Why did my post fail?" → health chip history + reconnect path (dead connections never silently drop). "Why was I disconnected?" → disconnect audit (owner action vs provider revoke detection). "Is my token safe?" → ciphertext-only storage + single-module confinement + rotation cadence, stated without exposing internals.
