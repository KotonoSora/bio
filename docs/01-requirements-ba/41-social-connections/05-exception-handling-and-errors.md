# Social Connections - Exception Handling and Errors (feature 41)

Phase 1 BA · Problems follow RFC 9457 (`code` field). Token material never appears in any message or log line.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Connect at plan cap | API | 403 `cap_exceeded`; server-side at connect and schedule time; direct calls cannot bypass | "Your plan allows {cap} {network} account(s). Upgrade or remove one." |
| Personal IG account attempted | API | 422 `network_unsupported` (Business/Creator only) | "Instagram publishing needs a Business or Creator account." |
| OAuth callback with bad/missing `state` or PKCE mismatch | API | 400 `oauth_state_invalid`; no exchange, no row, metric; rate-clamped | "Connection attempt expired — try again." |
| Callback replay (same state) | API | Refused after first use (single-use state); zero side effects | Same as above |
| Provider denies authorization | Callback | Redirect back with a neutral banner; no row | "Authorization wasn't completed." |
| Token exchange succeeds but scopes insufficient | Callback | Row refused; message names missing publish scope class | "We need publish permission to connect this account." |
| Refresh fails transiently (5xx) | Scheduler | Retry with backoff; health stays `expiring` | None (silent while retries remain) |
| Refresh fails with auth error | Scheduler | Status `revoked`/`expired`; queued targets cancel with notice | "Reconnect {network} — its login expired." |
| Provider-side revoke detected | Health cycle | Status flips `revoked` within the cycle; same cancel + notice | Same copy |
| Dead connection selected in composer | API (42) | 409 `connection_dead` at schedule time (fail closed, loud) | "This account needs reconnecting before it can publish." |
| Disconnect while targets queued | API | Row deleted; queued targets cancelled with notice; remote revoke attempted | "Disconnected. {n} scheduled posts targeting it were cancelled." |
| Rotation mid-flight | System | Dual-read window; zero publish failures; in-flight refresh retried | None |
| Master key unavailable at boot | System | Service fails closed for vault operations (health/connect/refresh); no plaintext fallback exists | Ops: "Vault unavailable — connections paused." |
| Ciphertext integrity failure (GCM tag mismatch) | System | Row marked `error`; alert; never guessed/recovered by fallback | "Reconnect {network} — stored credentials are unreadable." |
| Ops attempts token read | System | Impossible by construction (restricted view); any attempt is a code defect | None |
| Account deleted (profile purge) | System (02's purge) | Vault rows deleted; remote revokes attempted; audit | None (account gone) |
| Duplicate connect for the same account ref | API | Idempotent upsert: existing row refreshed, not duplicated | "Account already connected — credentials refreshed." |
| Connect-start flood | Gateway | Rate limit (redirector abuse) | 429 "Too many attempts — wait a moment." |
