# Social Connections - API Contracts and Payloads (feature 41)

Phase 2 TL · Owner + callback contracts. Auth/rate rules: `docs/02-architecture-techlead/41-social-connections/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457. Registry rows land in the service's own route map (publish-service is new; no shared registry file exists).

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/social/{network}/connect` | session + CSRF | - | `302` to provider authorize URL (`state` + PKCE stored server-side) | 403 `cap_exceeded`; 422 `network_unsupported`; 429; 503 `vault_unavailable` |
| `GET /api/v1/social/{network}/callback` | anonymous (single-use `state` + PKCE) | - | `302` back to settings with outcome banner | 400 `oauth_state_invalid`; provider-denied → neutral banner |
| `GET /api/v1/social/accounts` | session | - | `200 List<AccountView>` (health; no token fields exist) | 401; 429 |
| `DELETE /api/v1/social/accounts/{id}` | session + CSRF | - | `200 {cancelledTargets: Int}` | 404 `account_not_found`; 403; 429 |
| `POST /webhooks/social/{network}` | provider scheme (verified) | provider payload | `200` after durable handling | `401` bad signature; `5xx` transient |

```kotlin
data class AccountView(val id: String, val network: String, val accountRef: String,
  val health: String, val scopesSummary: List<String>, val expiresAt: Instant?,
  val lastHealthyAt: Instant?)
// no token field exists in any DTO; the restricted view backs ops reads
```

## Events (publish-service stream `publish.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `connection.created.v1` | `{accountId, profileId, network}` | connect upsert (create branch) | audit/ops readouts |
| `connection.dead.v1` | `{accountId, profileId, network, health}` | health flips to `expired|revoked|error` | 42 (target cancellation) |
| `connection.disconnected.v1` | `{accountId, profileId, network}` | disconnect | 42 (target cancellation) |

Emitted post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Connect: cap check server-side inside the start transaction (refusal audited `connection.cap_refused`); IG personal-account refusal surfaces pre-config copy; the provider URL carries minimal publish scopes per network.
- Callback: single-use `state` + PKCE `S256` verified before exchange; replay/tamper → `oauth_state_invalid` with zero side effects; exchange success upserts by `(profile_id, network, account_ref)` idempotently.
- Health: refreshes use skew budgets (default 24 h); auth failure flips `expired`/`revoked` and emits `connection.dead.v1`; GCM tag mismatch flips `error` (never guessed).
- Disconnect: row delete + best-effort remote revoke (≤ 5 s) + post-commit event; the response counts cancelled targets (42's consumer completes the cancellation ≤ 60 s).
- Webhooks (revoke signals where offered): verified per the standard discipline; unknown/mismatched payloads refuse; valid signals flip health through the same transition path (no direct status writes).
- No token ever appears in a URL, DTO, event payload, or problem detail; leases are in-process only.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `cap_exceeded` | 403 | Cap + upgrade/trim path |
| `network_unsupported` | 422 | IG Business/Creator explainer |
| `oauth_state_invalid` | 400 | "Connection attempt expired — try again." |
| `account_not_found` | 404 | Refresh list |
| `vault_unavailable` | 503 | Honest "connections paused" banner; retry |

Reused: `throttled`, `validation_failed`.
