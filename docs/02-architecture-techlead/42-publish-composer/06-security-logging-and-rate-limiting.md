# Publish Composer - Security, Logging, and Rate Limiting (feature 42)

Phase 2 TL · BA sources: `docs/01-requirements-ba/42-publish-composer/` (BR-01..BR-14, NFR-01..NFR-14). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: owner RLS on posts/targets/events; calendar and status reads owner-scoped; public surfaces never expose drafts (by absence); ops sees aggregates + ids only | AC-05 IDOR + RLS probes |
| S-2 | Media safety: server-issued parts, server-random file names, magic-byte + allowlist gates pre-queue; no client-chosen paths; preview grants ≤ 15 min | AC-01, AC-11 |
| S-3 | Credential confinement: dispatch resolves credentials via 41's in-process leases; messages carry ids + opaque `leaseRef` only; zero token material in streams, logs, events | Message-hygiene scan + grep gate |
| S-4 | Quota integrity: single SQL counting function (gate = meter = reconciliation); server-side at schedule; direct API cannot bypass; retried-once counts once | AC-02 + grep gate |
| S-5 | Idempotency: dedupe record per `(post, network, account)`; provider idempotency keys where offered; ambiguous-timeout retries gated by explicit confirm | AC-07 |
| S-6 | Lock integrity: freeze exactly at `run_at − lockMinutes`; pre-lock cancel produces zero dispatch (scheduler only sees `scheduled`); edits versioned (revision + audit) | AC-04 + lock boundary drill |
| S-7 | Outage controls: pause/resume audited with reason; fresh-read ≤ 5 s; resume replays in `run_at` order; DLQ replay dual-control on mass | AC-09 |
| S-8 | Retry honesty: transient auto-retry bounded (≤ 5 attempts, backoff 1→16 min); permanent terminal with fix guidance; `unknown` never auto-retries | AC-08 |
| S-9 | Webhook discipline: provider confirmations/revokes verified per the standard battery (signature, replay, tamper); idempotent reconciliation only | Webhook battery |
| S-10 | Privacy: caption/media are creator content (owner-scoped); no provider payload bodies stored (ids + reasons only); metrics carry zero content labels | Data-minimization audit |

## Rate limits (gateway + app; Redis db 11 `pub:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| Upload init / complete | 60/h/user + 10/h/IP | 429 `throttled` |
| Post create / patch / delete | 120/h/user | 429 |
| Post/calendar reads | 600/h/user | 429 |
| Retry / confirm-repost | 30/h/user + 10/h/post | 429 |
| Ops pause / DLQ replay | 60/h/operator | 429 |
| `POST /webhooks/social/{network}` | 600/min/IP + 120/min per network key | Refusal + clamp |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Provider API down | That leg retries then fails honestly; other legs unaffected; queue depth pages before creator-visible staleness |
| Redis down | Dispatch halts loudly (no DB-polling fallback fork exists); scheduled posts wait; UI shows honest delay |
| Storage volume down | Uploads fail honestly; existing posts and dispatch unaffected |
| Scheduler down | Past-due dispatches deliver `late` with notice on recovery; alert after 2 missed ticks |
| Vault unavailable (41) | Dispatch refuses with dead-connection honesty; no stale-credential publish attempts |
| Notification pipeline down | Creator notices queue with backlog; status truth unaffected |

## Audit and observability

Audits (24-month): `post.created/scheduled/edited/cancelled`, `post.locked_refusal`, `target.transition` (state + class), `post.late`, `network.pause/resume`, `dlq.quarantined/replayed`, `quota.refused`. Metrics: `dispatch_to_publish_seconds{network}`, `target_failures{network,class}`, `retry_outcomes`, `queue_depth/lag{network}`, `quota_refusals`, `late_dispatches`, `quarantine_count`. Zero token or content labels.

## Support answers

"Why didn't my post go out?" → per-target reason + class + fix guidance; never "try again" for permanent classes. "Did it double-post?" → dedupe/idempotency trace: one provider ref per target, or the confirm gate held. "Why is it late?" → outage timeline + `late` flag; dispatch order preserved. "Why can't I edit?" → lock window state with exact freeze time. "Why am I out of posts?" → quota meter reading the same counting function + reset/upgrade path.
