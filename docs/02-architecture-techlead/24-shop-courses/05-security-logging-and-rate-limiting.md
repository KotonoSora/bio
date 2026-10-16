# Course Builder - Security, Logging, and Rate Limiting (feature 24)

Phase 2 TL · BA sources: `docs/01-requirements-ba/24-shop-courses/` (BR-10..BR-17, NFR-09..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: owner by `user_id`, buyer by `buyer_user_id`, `svc` only on syllabus read + ticket mint file read | AC-06; direct-PG probes |
| S-2 | Gate: every gated lesson/material mint checks an active enrollment; preview lessons are the only anonymous stream path; zero gated bytes in preview/syllabus responses | AC-01, AC-08 |
| S-3 | Ticket limits: video TTL ≤ 4 h multi-request (range-safe), material single-use ≤ 15 min; bound to enrollment + lesson; guessed object keys do not resolve; no bucket listing | AC-01, AC-09 |
| S-4 | Upload safety: bounded reads, MP4 container + duration parse, digital allowlist for materials, random keys, sanitized bodies | AC-04; upload matrix |
| S-5 | Progress integrity: server clamps position; `completed` sticky; auto90 computed server-side; client cannot mark others' progress (RLS) | AC-05, AC-10 |
| S-6 | Certificate integrity: mint-once DB guarantee; tamper-evident code; revocation on refund → 404 ≤ 60 s; codes not enumerable (128-bit) | AC-03, AC-10 |
| S-7 | Abuse: heartbeat 600/h per enrollment (coalesced regardless); mints 120/h per buyer; builder saves 120/h; enroll/revoke cycles audited | AC-05; burst drills |
| S-8 | Audit: enrollment, revocation, certificate mint/revoke, publish attempts append-only, 24-month retention; positions never logged to audit | BR-13 |
| S-9 | Scope guard: no fee values here (26); no AI paths (16 gated); secrets env/vault only; buyer emails never stored (D-5) | Fee/AI greps; secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Heartbeats | 600/h per enrollment | 429; coalescing unaffected |
| Stream/material mints | 120/h per buyer | 429 |
| Builder saves | 120/h per course | 429 |
| Uploads | 30/h/user | 429 + `Retry-After` |
| Internal reads | service budget | 429 + trace |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Redis (db 9) down | Heartbeats answer 503 (client retries; positions already in DB stay authoritative); mint single-flight unnecessary here — mints are PostgreSQL-checked |
| Flusher down | Resume reads stale DB state until recovery; no data loss beyond unflushed window (client replays last position) |
| MinIO down | Uploads 503; stream/material mints 503 `stream_unavailable`; pages unaffected |
| Event bus down | Paid enrollments/refunds delayed; free path unaffected; sweeps unaffected |
| public-site down | Certificate verification unavailable (503); course playback is SPA-side, unaffected |
| Analytics down | No dependency in this feature |

## Threat checklist

Unenrolled byte fetch, guessed object keys, ticket sharing (TTL-bounded, anomaly-flagged; DRM not claimed), IDOR on progress/enrollment, certificate forgery (code entropy + mint-once), builder CSRF (session + CSRF on mutations), heartbeat spam (envelope + coalescing), refund-then-replay (revoked + cert 404), purge of Redis overlay keys.

## Gates

- G4: OpenAPI diff additive; codes registered; `/c/{code}` route + cache key present.
- G5: S-2 gate drill, S-3 ticket matrix, S-4 upload matrix, S-6 forgery probes.
- G7: AC-01..AC-12 attach to the tag; container drill (Docker + Podman) per baseline release gates.
- Greps: zero fee literals in this directory; no `source='ai'`; secrets clean; no buyer email columns anywhere.
