# Social Connections (OAuth vault, per-plan caps)

PLAN ref: P4-1 — P4 scope, flagged explicitly. Do NOT build in P1–P3 (no token storage, no OAuth routes, no SDK stubs before P4). First doc of the new `publish` major; every sender in this major depends on connections established here.

## Purpose

Hold the keys to seven platforms without ever leaking them: per-network OAuth connections in an encrypted vault, per-plan account caps, and silent refresh — so composing (see `composer/compose-schedule.md`) and fan-out (see `delivery/fanout-status.md`) never touch raw tokens, logs never contain them, and revoking on one side is honored within hours, not weeks.

## Users

- Creator: links social accounts (one per platform on free, up to ten per platform on pro), sees connection health, disconnects cleanly.
- Publisher pipeline (internal consumer): receives short-lived usable credentials per job without ever reading the vault directly.
- Security reviewer: verifies tokens are encrypted at rest, never logged, and rotatable without a deploy.

## Functional requirements

1. Networks (closed v1 set): YouTube Shorts, Instagram Reel (Business/Creator accounts only — personal accounts cannot publish via API, stated in-UI before the user starts), TikTok, Facebook Reel (Page-linked), X, Threads, LinkedIn. Each network has its own OAuth app credentials, scopes (publish-minimal, never read-DMs/manage-ads), and callback route.
2. Vault: `social_accounts{profile_id, network, account_ref, access_token_enc, refresh_token_enc, scopes, expires_at, status}` encrypted with `cf-aes-gcm` + `TOKEN_ENC_KEY` (a `wrangler secret`, quarterly rotation per P5-4). Encryption/decryption happens in one audited module; tokens never appear in logs, error payloads, queue messages, or analytics events ( Dispatch passes opaque job leases, see delivery doc).
3. Connection caps by tier (VN-adapted from link.me's 1-vs-10 ladder; no Plus tier copied): free = 1 account per platform; pro = up to 10 per platform. Cap checks run at connect time (server-side, not UI-only); downgrades keep existing connections read-only-visible but block new connects and publishing from over-cap accounts until the user trims.
4. Health + refresh: proactive refresh before expiry (scheduled, per-network skew budgets); status `healthy|expiring|expired|revoked|error` visible per account; expired/revoked accounts fail closed at schedule time with creator notice (a post targeting a dead connection never silently drops — it errors loudly, see delivery doc).
5. Disconnect + platform-side revoke: user disconnect deletes vault rows immediately (plus provider-side revoke call where the API offers one); provider-side revokes detected via refresh failure or webhook where available, flipping status within the next health check cycle. Reconnect is a fresh OAuth dance, never a resurrection of stored material.
6. Multi-profile agency use (preview): rows are per-profile; Act-as-user publishing across client profiles waits for the P4 agency doc — no cross-profile token sharing in v1.

## Data / API touches

- New table `social_accounts` (above shape) plus standard `user_id TEXT NOT NULL` + index + RLS (owners manage own; ops sees health metadata only — never token columns, enforced by a restricted view). All PG via `worker/db/*`; token crypto confined to `worker/auth/token-vault.*` (single module, no ad-hoc encrypt calls elsewhere).
- Endpoints: `POST /api/v1/social/:network/connect` (OAuth start), provider callback routes (state + PKCE verified), `GET /api/v1/social/accounts` (health list, no token fields), `DELETE /api/v1/social/accounts/:id` (disconnect + remote revoke attempt). All authenticated; connect route rate-limited (OAuth-start abuse is a classic redirector vector).
- Rotation story: `TOKEN_ENC_KEY` versioned with dual-read window (new writes on new key, old rows re-wrapped by a migration job) so quarterly rotation never invalidates live connections.

## Acceptance criteria

- Vault drill: database dump inspection shows ciphertext only; application logs from connect/publish/refresh flows contain zero token material (grep gate in CI).
- Cap drill: free user connecting a second account on one platform gets 403 with the upgrade path; direct API call cannot bypass; pro connects ten, eleventh refused.
- Refresh drill: near-expiry tokens refresh silently with no failed publish; revoked-provider-side account flips to `revoked` by next health cycle and its scheduled posts error loudly (never silent-drop).
- Disconnect drill: row deleted, remote revoke attempted, scheduled queued jobs for the account cancelled with creator notice.
- Rotation drill: key rotation re-wraps all rows with zero publish failures during the dual-read window.

## Out of scope

- Composer, captions, media specs, calendar (see `composer/compose-schedule.md`); queue fan-out, per-platform status, retries (see `delivery/fanout-status.md`).
- IG comment/DM automations (separate P4 automation scope); OAuth app review/approval processes (external dependencies); agency Act-as-user (P4 agency scope).

## Open questions

- Q1: Threads/X API volatility (pricing/rate changes) — ship all seven at P4 entry or stage networks by API stability?
- Q2: Pro cap of 10 per platform — keep link.me parity exactly, or lower (e.g. 5) until vault-ops experience warrants 10?
