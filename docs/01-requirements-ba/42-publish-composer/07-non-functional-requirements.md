# Publish Composer - Non-Functional Requirements (feature 42)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/42-publish-composer/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Composer latency | Schedule confirm p95 ≤ 500 ms (validation included, dispatch excluded); calendar read p95 ≤ 400 ms at 10k posts |
| NFR-02 | Upload | Multipart parts ≤ 25 MB; resumable; 300 MB on 4G-class connections completes without restart; zero server-side transcode in v1 |
| NFR-03 | Dispatch | Scheduler tick ≤ 1 min; dispatch-to-first-status p95 ≤ 90 s for healthy networks; per-target deadline 15 min (config) |
| NFR-04 | Status freshness | Live N-of-7 ≤ 5 s behind the last target event (short-poll or stream); status read p95 ≤ 300 ms |
| NFR-05 | Queue discipline | Redis Streams consumer groups; XAUTOCLAIM reclaim ≤ 60 s; max-delivery bound before DLQ (N=5); queue depth/lag dashboards page before creator-visible staleness |
| NFR-06 | Retry bounds | Transient: ≤ 5 attempts, exponential backoff 1→16 min; permanent: zero retries; manual retry eligibility checked server-side |
| NFR-07 | Quota precision | One counting function for gate + meter + reconciliation; retried-once counts once; period math exact to the minute (30-day rolling / lifetime) |
| NFR-08 | Lock precision | Freeze at exactly `run_at − 5 min` (±1 s); pre-lock cancel guaranteed no-dispatch (drill-verified) |
| NFR-09 | Outage controls | Pause effect ≤ 5 s (fresh reads); resume replays in original order; poison quarantine ≤ 5 redeliveries |
| NFR-10 | Audit retention | Post/target transitions, schedule/cancel/reschedule, pause/resume, quarantine/replay retained 24 months; payloads = ids + reasons |
| NFR-11 | Privacy | No token material in messages/logs; media objects private (signed URLs ≤ 15 min for preview); public surfaces never expose drafts |
| NFR-12 | Scoping | Owner RLS on posts/targets/events; ops reads aggregates + ids only; cross-user reads zero rows |
| NFR-13 | Degrade | Provider API down → leg retries then honest failures, other legs unaffected; Redis down → dispatch halts loudly (no DB polling fallback fork); MinIO down → uploads fail honestly, existing posts unaffected; scheduler down → past-due delivers `late` on recovery |
| NFR-14 | Observability | Metrics: dispatch-to-publish p95 per network, failure classes, retry outcomes, queue depth/lag, quota refusals, late dispatches, quarantine count — zero token labels |
