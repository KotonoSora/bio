# Social Connections - User Stories and Business Rules (feature 41)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/41-social-connections/`. Scope: `docs/01-requirements-ba/41-social-connections/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | link social accounts within my plan's caps and see health | I know what will publish and what needs reconnecting | High |
| US-02 | Creator | disconnect cleanly and have queued work cancelled with notice | revocation never leaves ghost posts | High |
| US-03 | Publisher pipeline | receive short-lived usable credentials per job | tokens stay confined to one audited module | High |
| US-04 | Security reviewer | verify ciphertext-only storage, zero-log tokens, and no-deploy rotation | seven platforms' keys stay safe | High |
| US-05 | Ops | see connection health metadata (never tokens) for support | diagnose dead connections without key access | Medium |

## Business rules

- **BR-01 Network set (closed).** YouTube Shorts, Instagram Reel (Business/Creator only — personal accounts refused pre-config with in-UI copy), TikTok, Facebook Reel (Page-linked), X, Threads, LinkedIn. Each has its own OAuth app credentials, publish-minimal scopes, and callback route; no generic catch-all network exists.
- **BR-02 OAuth discipline.** Connect starts an OAuth dance with `state` + PKCE; the callback verifies both before exchanging; replayed/mismatched callbacks are refused with no side effects; connect-start is rate-limited (redirector-abuse vector).
- **BR-03 Vault.** `social_accounts` stores access/refresh tokens encrypted (AES-256-GCM envelope: per-row DEK wrapped by versioned master `TOKEN_ENC_KEY` via vault/env). Encryption/decryption happens in exactly one module; tokens never appear in logs, error payloads, queue messages, or analytics events.
- **BR-04 Caps.** Free = 1 account per platform; pro = 10 per platform (values in `publish.quotas`). Checks run server-side at connect and at schedule/dispatch time; the API cannot be bypassed by direct calls. Downgrade: over-cap connections remain visible read-only; new connects and publishing from over-cap accounts are blocked until the user trims.
- **BR-05 Health states (closed).** `healthy|expiring|expired|revoked|error` per account; expiring/expired/revoked accounts are unselectable as targets and fail closed at schedule time with creator notice — a post targeting a dead connection errors loudly, never silently drops.
- **BR-06 Refresh.** Proactive refresh before expiry (scheduled, per-network skew budgets); refresh is transparent to composers; refresh failure flips health and notifies; refresh never blocks on user action.
- **BR-07 Provider-side revoke.** Detected via refresh failure or provider webhook where available; status flips within the next health cycle; queued targets for that account cancel with notice.
- **BR-08 Disconnect.** Owner disconnect deletes the vault row immediately, attempts the provider-side revoke where the API offers one, and cancels queued targets for that account with creator notice. Reconnect is a fresh OAuth dance — never a resurrection of stored material.
- **BR-09 Rotation.** `TOKEN_ENC_KEY` is versioned; rotation uses a dual-read window (new writes on the new key; old rows re-wrapped by a migration job) with zero publish failures during the window; quarterly cadence.
- **BR-10 Ops visibility.** Ops reads health metadata only, via a restricted view that excludes token columns at the DB level; no UI, export, or debug surface can select token columns.
- **BR-11 Agency preview.** Rows are per-profile; no cross-profile token sharing exists in v1; Act-as-user publishing waits for 45–47.
- **BR-12 Audit.** Connect, disconnect, refresh-flip, revoke detection, rotation, cap refusals append to the service's `audit_log` (actor, action, detail, 24-month retention); token material never appears in audit detail.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/41-social-connections/06-acceptance-criteria.md`: vault (ciphertext-only + zero-token grep), caps (403 + bypass-proof), refresh (silent, loud-fail on revoke), disconnect (row deleted + revoke attempted + queued cancelled), rotation (zero publish failures). GWT forms live there.
