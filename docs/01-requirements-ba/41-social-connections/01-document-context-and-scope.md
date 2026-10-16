# Social Connections - Document Context and Scope (feature 41)

Phase 1 BA · Feature 41 of 53 — publish/automate 1/4. Holds the keys to seven platforms without ever leaking them: per-network OAuth in an encrypted vault, per-plan caps, silent refresh, honest health. Sources: `publish/accounts/social-connections.md`, `platform/delivery/publish-automate.md` (FR-2). Implemented by `docs/02-architecture-techlead/41-social-connections/`. Service: publish-service (new; port 8089, `publish_db`, Redis db 11).

## Context

Every sender in the publish/automate major depends on connections established here. The legacy vault (`cf-aes-gcm`, `TOKEN_ENC_KEY`, Workers) maps to the mandated stack: AES-256-GCM envelope encryption, master key via vault/env, Spring Boot service, PostgreSQL with RLS, Redis db 11. The legacy P4 gate maps to the baseline's E4 phasing: zero publish tables/jobs/endpoints exist in E0–E3 images (release-gate audit). The legacy monolith-vs-split debate is superseded by the microservices mandate; publish-service is the boundary, with fan-out workers in-service behind `DispatchPort` (baseline 01's extraction doctrine applies to any later split).

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Seven-network OAuth (closed set) with state + PKCE verification, publish-minimal scopes, per-network callback routes |
| OBJ-02 | Tokens encrypted at rest (envelope, versioned keys), confined to one audited module, never in logs/queues/analytics/error payloads |
| OBJ-03 | Per-plan caps enforced server-side at connect and at publish time; downgrades never silently break state |
| OBJ-04 | Health + refresh: proactive refresh with skew budgets; dead connections fail closed at schedule time with loud creator notice |
| OBJ-05 | Disconnect deletes vault rows + attempts remote revoke + cancels queued targets with notice; reconnect is a fresh OAuth dance |
| OBJ-06 | Quarterly key rotation with dual-read re-wrap and zero publish failures during the window |

## Scope

In scope (SC-01..SC-07): network set (YouTube Shorts, Instagram Reel Business/Creator, TikTok, Facebook Reel Page-linked, X, Threads, LinkedIn); OAuth connect/callback/disconnect/reconnect; `social_accounts` vault (V1); health states + refresh scheduler; per-plan caps; provider-side revoke detection; key rotation; ops health metadata read (never tokens); the E4 gate audit.

Out of scope: composer/captions/calendar (42); fan-out and per-target status (42); automations, contacts, DM delivery (43/44); agency Act-as-user cross-profile publishing (45–47); OAuth app review/approval processes (external); broadcast campaigns and audience lists (gated 48–53).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | C/R/U/D own connections; R health; D disconnect | Links accounts within caps; never sees token material |
| Publisher pipeline (internal) | R short-lived in-process leases only | Consumers resolve credentials via the vault module; never read vault rows directly |
| Security reviewer | R rotation + ciphertext evidence | Verifies encryption, log hygiene, rotatability |
| Ops | R health metadata via restricted view | No token columns exist in any ops surface |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Stage networks by API stability (41 Q1)? | Ship all seven at once; per-network pause switches (42's outage control) cover instability without staging the roster |
| Q-02 | Pro cap 10 per platform (41 Q2)? | Keep 10; caps live in `publish.quotas` config (ops-tunable, audited) — no invented ladder changes |
| Q-03 | Upload protocol (publish-automate Q1) | MinIO multipart with server-issued parts (S3-compatible); no Tus dependency (lands in 42) |
| Q-04 | First split: fan-out workers vs watcher (publish-automate Q2) | Superseded: microservices mandate fixes the service boundary now; in-service worker pools behind `DispatchPort`/`WatchPort`; any further split requires a spec amendment per baseline doctrine |
| Q-05 | IG personal accounts | Refused pre-config with in-UI copy (API limitation); only Business/Creator accounts connect |

## Constraints carried

Scopes are publish-minimal per network (never read-DMs/manage-ads); the crypto module is the only code path touching plaintext tokens (grep gate); ops sees health metadata only (restricted view enforces at the DB level); token material never enters queue messages, logs, error payloads, or analytics; secrets via vault/env only; every state change audits to the service's append-only `audit_log` (24-month retention, baseline 03).
