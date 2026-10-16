# Shop Fulfillment - Non-Functional Requirements (feature 23)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Dispatch hook p95 ≤ 500 ms per order line after grant consumption; exactly-once under replay. |
| NFR-02 | Access-link mint p95 ≤ 300 ms; signed-URL TTL ≤ 15 min; single-use enforced. |
| NFR-03 | Partner callback ack (`202`) p95 ≤ 300 ms; status applied ≤ 30 s after ack via the queue. |
| NFR-04 | Locked-body fragment p95 ≤ 400 ms; never cached (`private, no-store`). |

## Limits and quotas

| # | Requirement |
|---|---|
| NFR-05 | Artwork ≤ 20 MB (PNG/JPEG/WebP/PDF); digital files ≤ 500 MB (ebook/audio/preset/zip allowlist); no total quota in v1. |
| NFR-06 | Access window default 30 days + 5 fetches; extension caps 90 days / +20 downloads; mint rate per buyer 10/min, 100/day. |
| NFR-07 | Callback rate 120/min per partner, dedupe window 24 h on event id; queue depth alert at 1 000. |
| NFR-08 | Job status transitions ≤ 6 per job (the closed status set); terminal states never re-enter. |

## Security and data

| # | Requirement |
|---|---|
| NFR-09 | Signed URLs bound to order + file version + buyer; blob keys random and unguessable; no listing on any bucket path. |
| NFR-10 | Entitlement checks server-side on every mint and every locked-body request; preview responses contain zero locked bytes (automated byte check). |
| NFR-11 | Buyer-scoped PII: ship-to minimized, encrypted at rest; buyer account deletion scrubs job rows; partner receives fulfillment fields only. |
| NFR-12 | Audit: dispatch, transitions, extensions, revocations append-only, 24-month retention; never content or codes. |
| NFR-13 | Idempotency: grant consumption, callbacks, and mints are replay-safe (unique keys); double delivery collapses, never doubles. |

## Reliability and localization

| # | Requirement |
|---|---|
| NFR-14 | Partner adapter failures: bounded retry with backoff (3 attempts), then `failed` + creator alert; no infinite retries. |
| NFR-15 | Degrade: partner unreachable → orders accepted, jobs queue as `pending_print`; analytics/queue down never blocks payment or pages. |
| NFR-16 | vi/en copy; VND integers only; all displayed dates labeled partner-provided; accessibility: status timeline readable by screen readers. |
