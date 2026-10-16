# Custom Domains - API Contracts and Payloads (feature 04)

Phase 2 TL · Owner + internal REST contracts. Auth/rate rules: `docs/02-architecture-techlead/04-custom-domains/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. Gate codes reuse the feature 01 snapshot contract.

## Endpoint map (owner, dashboard class)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/domains` | session | 200 list of `DomainResponse` | 401 |
| `POST /api/v1/domains` | session + CSRF + pro gate | 201 `DomainResponse` (`pending`; challenge shown once) | 403 `domain_forbidden`; 409 `domain_taken`; 422 `hostname_invalid`, `domain_denied`, `domain_quota_reached`; 429 `throttled` |
| `POST /api/v1/domains/{host}/verify` | session + CSRF | 200 `DomainResponse` | 404 `domain_not_found`; 429 |
| `POST /api/v1/domains/{host}/recheck` | session + CSRF | 200 `DomainResponse` | 404; 429 |
| `DELETE /api/v1/domains/{host}` | session + CSRF | 204 (immediate; serving halts at commit) | 404; 401 |

`user_id` is server-set; hostnames are normalized before any comparison (BR-02).

## Internal endpoints

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `GET /internal/v1/domains/{host}` | service JWT | 200 `ResolveResponse` | 401 |
| `GET /internal/v1/domains/by-user/{user_id}` | service JWT | 200 list `{host, status}` (empty when none; share derivation uses verified only - feature 20) | 401 |
| `POST /internal/v1/domains/{host}/reclaim` | service JWT, ops scope, audited | 202 `ReclaimResponse` (fresh round forced) | 404; 401 |
| `POST /internal/v1/domains/denylist` | service JWT, ops scope | 201 | 422 `hostname_invalid`; 401 |
| `DELETE /internal/v1/domains/denylist/{host}` | service JWT, ops scope | 204 | 404; 401 |
| (participant) `POST /internal/v1/account/purge` | service JWT | 200 counts incl. `custom_domains` + `domain_denylist`-safe rows | 401 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class AddDomainRequest(val hostname: String)
data class DomainResponse(val hostname: String, val displayHost: String, val status: String,
    val txtName: String? = null, val txtValue: String? = null,      // value only in the issuing response
    val challengeExpiresAt: java.time.Instant? = null, val verifiedAt: java.time.Instant? = null,
    val lastRecheckedAt: java.time.Instant? = null, val serving: Boolean)
data class ResolveResponse(val result: String,                      // profile | blocked | unknown
    val username: String? = null, val canonicalHost: String? = null)
data class ReclaimResponse(val hostname: String, val challengeIssuedAt: java.time.Instant, val expiresAt: java.time.Instant)
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `hostname_invalid` | 422 | input error (wildcard, length, normalization) |
| `domain_denied` | 422 | neutral deny message |
| `domain_taken` | 409 | dispute guidance + support path |
| `domain_forbidden` | 403 | Pro upsell (feature 03 copy) |
| `domain_quota_reached` | 422 | removal guidance |
| `domain_not_found` | 404 | list refresh |
| `throttled` | 429 | cooldown text |

## Serving contract (normative)

- Resolve semantics: `profile` -> serve SSR for the bound profile (only when `status=verified` AND snapshot `plan=pro`); `blocked` -> neutral error (unverified/suspended/plan-less); `unknown` -> neutral error. `blocked` and `unknown` render identical shape/timing (NFR-09) and differ only in internal telemetry.
- Canonical: bio canonical = `https://{host}/{username}` on a custom host (feature 10's tag row carries the host-aware case); `/cv` keeps `noindex` + canonical-to-bio; `/shop` mounts under the same host per profile settings.
- Cache: positive resolve 30 s, negative 60 s (edge layer, feature 10 namespace); SSR keys `ssr:{host}:{path}` tagged with the username so publish purge flushes both key families; `domain.state_changed.v1` purges the host's keys immediately.
- Unknown/unverified requests never touch the profile query path and never redirect (no open-redirect surface).

## Events

| Event | Payload | Consumers |
|---|---|---|
| `domain.state_changed.v1` | `hostname, username, state (verified\\|suspended\\|unverified\\|removed), at` | public-site-service (host key purge), abuse tooling, owner notices, TLS job (on `verified`) |

After-commit, at-least-once; lost events self-heal via the resolve cache TTLs; consumers idempotent.

Governance: additive-only; codes rename-frozen; no fee values; the feature 10 tag-contract edit lands in this change (host-aware canonical row).
