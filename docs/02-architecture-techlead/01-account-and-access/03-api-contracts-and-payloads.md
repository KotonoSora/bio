# Account and Access - API Contracts and Payloads (feature 01)

Phase 2 TL · Browser + internal REST contracts. Auth/rate rules: `docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. BA sources: `docs/01-requirements-ba/01-account-and-access/`.

## Endpoint map (browser-facing)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `POST /api/auth/sign-in/social` | anonymous, throttled | 302 to IdP (`state`+`nonce`+PKCE) | 422 `validation_failed`; 429 `throttled` |
| `GET /api/auth/callback/{provider}?code=&state=` | anonymous | 302 to `callbackURL`/`/app`; sets `rb_session` | 302 `/auth/error` (`auth_failed`) |
| `POST /api/auth/step-up` | session + CSRF | 302 to IdP (mode `step_up`) | 401; 429 |
| `POST /api/auth/link/{provider}` | session + CSRF + step-up `<= 10 min` | 302 to IdP (mode `link`) | 403 `step_up_required`; 409 `already_linked`; 429 |
| `POST /api/auth/unlink/{provider}` | session + CSRF + step-up | 204 | 403 `step_up_required`; 409 `last_link_forbidden`; 404 `not_linked` |
| `POST /api/auth/sign-out` | session + CSRF | 204, cookie cleared | 401 `unauthenticated` |
| `GET /api/v1/me` | session | 200 `MeResponse` | 401 |
| `GET /api/v1/me/sessions` | session | 200 `SessionListResponse` | 401 |
| `DELETE /api/v1/me/sessions/{sid}` | session + CSRF | 204 | 404 `session_not_found`; 401 |
| `DELETE /api/v1/me/sessions` | session + CSRF | 204 (all others revoked) | 401 |
| `POST /api/v1/me/contact-email` | session + CSRF | 202 code sent | 409 `email_conflict`; 422 `validation_failed`; 429 |
| `POST /api/v1/me/contact-email/verify` | session + CSRF | 200, verified | 422 `email_code_invalid`; 429 |

`user_id` comes from the session only (BR-08). Password/reset paths do not exist; probes get `404 auth_disabled`.

## Internal endpoints

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `GET /internal/v1/entitlements/{userId}` | service JWT | 200 `EntitlementSnapshotResponse` | 401 |
| `GET /internal/v1/users/{userId}/email` | service JWT | 200 `{email, emailVerified}` (transactional notices; 36's reminders) | 401; 404 `user_absent` |
| `GET /internal/v1/sessions/{sid}/introspect` | gateway-signed service JWT (`sub=edge:gateway`) | 200 `SessionIntrospectResponse` | 404 `session_not_found`; 401 |
| `POST /internal/v1/account/recovery-link` | service JWT, ops scope | 201 `RecoveryLinkResponse` | 422 `evidence_required`; 401 |
| consumes `GET /internal/v1/profiles/by-user/{user_id}` | profile-service (additive row registered in `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`) | 200 `{username}` or 404 `profile_absent` | - |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class SocialSignInRequest(val provider: String, val callbackURL: String)
data class StepUpRequest(val returnTo: String)
data class MeResponse(val id: String, val name: String?, val email: String?, val emailVerified: Boolean,
    val emailConflict: Boolean, val contactEmail: String?, val contactVerified: Boolean,
    val imageKey: String?, val createdAt: java.time.Instant)
data class SessionSummary(val sid: String, val current: Boolean, val deviceLabel: String?, val ipClass: String?,
    val createdAt: java.time.Instant, val lastActiveAt: java.time.Instant)
data class SessionListResponse(val sessions: List<SessionSummary>)
data class ContactEmailRequest(@field:Email val email: String)
data class ContactEmailVerifyRequest(@field:Pattern(regexp = "\\d{6}") val code: String)
data class SessionIntrospectResponse(val userId: String, val authTime: java.time.Instant, val absoluteExpiresAt: java.time.Instant)
data class EntitlementSnapshotResponse(val userId: String, val plan: String, // "free" | "pro"
    val capabilities: Map<String, String>, val resolvedAt: java.time.Instant)
data class RecoveryLinkRequest(val userId: String, val evidenceRef: String)
data class RecoveryLinkResponse(val token: String, val expiresAt: java.time.Instant)
```

## Problem body and code catalog

```json
{ "type": "https://api.example.com/problems/step_up_required",
  "title": "Re-authentication required", "status": 403,
  "detail": "Confirm it's you to continue.", "code": "step_up_required",
  "reauthPath": "/api/auth/step-up" }
```

| `code` | HTTP | UI mapping |
|---|---|---|
| `auth_failed` | 302 `/auth/error` | generic sign-in failure notice; retry |
| `auth_disabled` | 404 | password probes only; nothing renders |
| `step_up_required` | 403 | step-up dialog; `reauthPath` consumed |
| `last_link_forbidden`, `already_linked`, `not_linked` | 409 / 409 / 404 | providers list hint / no-op refresh |
| `session_not_found` | 404 | refresh session list |
| `email_conflict`, `email_code_invalid`, `validation_failed` | 409 / 422 / 422 | contact-email form states |
| `throttled` | 429 | cooldown text from `Retry-After` |
| `unauthenticated` | 401 | login screen |

## Edge session and JWT contract (normative)

- Cookie `rb_session`: `HttpOnly` + `Secure` + `SameSite=Lax`; CSRF double-submit on mutations; cleared on logout; no JS-readable token exists.
- Gateway per request: GET `edge:session:{sid}` (db 2) -> mint JWT, refresh TTL to remaining min(idle, absolute); miss -> introspect (above) -> rebuild key; 404 -> 401. `sessions` registry is truth; db 2 is cache only.
- JWT claims: `iss=gateway`, `sub=userId`, `aud=<service>`, `iat`, `exp=iat+60 s`, `jti`, `auth_time` (last SSO authentication; refreshed by step-up). Services check `now - auth_time <= 600 s` for the six sensitive actions (BR-07); nothing else gates them.
- Revocation: registry `revoked_at` + edge key delete; in-flight JWTs expire within 60 s (NFR-03).

## Entitlement snapshot (normative)

- `plan` in `{free, pro}`; `capabilities` is a reserved string map whose keys are registered by each owning feature spec (feature 03 registers `profiles.limit`); the ledger projection (feature 35) resolves the real plan - the free floor is materialized, never invented.
- Consumers MUST cache no longer than 30 s and MUST treat any miss/error/timeout as free (fail-closed), incrementing `entitlement_snapshot_miss`. Plan changes take effect within 30 s; no event is emitted - cache TTL self-heals (baseline).

Governance: OpenAPI from springdoc; additive-only diff gate (G4); codes above are stable contract and never renamed; this feature emits no events.
