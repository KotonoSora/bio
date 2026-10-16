# Lead Search - Security, Logging, and Rate Limiting (feature 28)

Phase 2 TL · BA sources: `docs/01-requirements-ba/28-lead-search/` (BR-10..BR-16, NFR-09..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scope: every search/read/write is pre-scoped (`user_id` + RLS); foreign ids return 404 with zero rows (no existence oracle); leak probes with overlapping fixtures | AC-01, AC-07; RLS probes |
| S-2 | Export safety: owner-bound HMAC grants (15 min), service-mediated download, private objects, fixed CSV columns, no second exporter (surface review) | AC-02, AC-07, AC-10 |
| S-3 | Suppression integrity: reason required; rank-guarded writes; idempotent; propagation via the shared eligibility check before any send | AC-04; rank + eligibility drills |
| S-4 | Token safety: re-invite/unsubscribe tokens single-purpose, 72 h, HMAC; POST-only state change; tamper → `unsubscribe_invalid` with zero change; scanner-safe page | AC-06; tamper matrix |
| S-5 | Injection/sanitization: note bodies and tags sanitized before storage; no HTML rendering of visitor-derived fields in the timeline; CSP applies | AC-03/04; payload probes |
| S-6 | Append-only integrity: no UPDATE/DELETE paths for notes/tags/events; alerts on statement attempts; vocabulary deletes absent | AC-04; alert drill |
| S-7 | Abuse budgets: search/action envelopes per session; export quota (20/day, 2 concurrent) enforced in Redis db 4 + DB; re-invite 3/day per lead | AC-05/06; burst drills |
| S-8 | Attribution: every action event actor-stamped (`creator:{userId}` now, `support:{seatId}` forward); no anonymous write path exists | AC-04, AC-10 |
| S-9 | PII/retention: jobs/objects purge at 7 days and with the account; grants dead afterward; logs carry ids/never raw emails; query history private per account | AC-09; purge + log greps |
| S-10 | Secrets: MinIO credentials, grant and token keys env/vault only; no bucket URLs or credentials reach clients | secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Search / detail / filter | 120/min per session (reads) | 429 |
| Notes / tags / suppress / read | 60/min per session | 429 |
| Re-invite | 3/day per lead | 429 `reinvite_limited` |
| Export enqueue | 20/day, 2 concurrent per account | 429 `export_quota` |
| Download with grant | 10/min per job | 429 |
| Unsubscribe | 30/min per IP | 429 |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| MinIO down | Exports fail with retry + `failed` status after 3 attempts; search/detail unaffected; no partial files serve |
| Redis (db 4) down | Quota degrades to DB-only checks (still bounded by job rows); caps stay enforced at gateway |
| Notification pipeline down | Re-invite enqueues and retries; no send without the token minted server-side |
| Forward seam down (32-40/25/41-44) | Timeline section reports `not yet connected`; the rest of the record renders |
| Export worker down | Jobs stay `queued`; enqueue still 202s (durable); backlog alert at 50 queued |
| DB read failure | 503; never serve stale or unscoped data |

## Threat checklist

Cross-tenant search/read/action, existence probing via 404 timing, grant sharing/leakage, export data exfiltration via bucket URLs, suppression bypass before a send, consent-grant forgery (no endpoint exists), tag vocabulary blindness across accounts, CSV formula injection in exported cells (email/name sanitized + prefixed for `=+-@`), query-history leakage, token replay.

## Gates

- G4: OpenAPI diff additive; codes registered; read routes session-scoped; download route grant-scoped (not session-scoped).
- G5: S-1 leak probes, S-2 grant matrix, S-4 tamper matrix, S-6 append-only alerts.
- G7: AC-01..AC-10 attach to the tag; container drill (Docker + Podman) per baseline release gates; 100k fixture run recorded.
- Greps: zero bulk-message endpoints, zero consent-grant writers, zero second exporters, zero raw emails in logs, secrets clean.
