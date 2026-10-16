# Agency Roster - Non-Functional Requirements (feature 45)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/45-agency-roster/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Membership checks | Per-request evaluation ≤ 10 ms p95 against the in-service roster (no cross-service lookup on the hot path) |
| NFR-02 | Effect latency | Role/removal/suspension/unlink effects visible on the next request (≤ 60 s cache bound, none beyond token TTL) |
| NFR-03 | Invite tokens | Signed, hashed at rest, single-use, 7-day expiry; acceptance ≤ 300 ms p95; re-invite limit 3/day/username |
| NFR-04 | Act-as start | All gates + presence lock + token mint ≤ 500 ms p95; step-up freshness ≤ 10 min at start |
| NFR-05 | Act-as sessions | 30-min idle TTL sliding with activity; 8-h hard stop; ≤ 3 concurrent per staffer; one active per profile (presence lock) |
| NFR-06 | Revocation | Client revoke kills live sessions ≤ 5 s; in-flight mutations fail closed per request; org-wide suspend ≤ 5 s |
| NFR-07 | Audit | Every mutation + session start/end retained 24 months (append-only, no UPDATE/DELETE grants); before/after for content edits; client-visible history matches support view |
| NFR-08 | Privacy | Invite tokens hashed; zero token material in logs/errors/events; org data RLS-scoped; client history exposes only their own profiles |
| NFR-09 | Rate limits | Invite send 30/h/org; accept 30/h/IP; act start 20/h/staffer; revoke 10/h/client; roster reads 600/h/user; 429 with `Retry-After` |
| NFR-10 | Degrade | Step-up provider down → act starts refuse (fail closed); notification pipeline down → invites queue with backlog (tokens remain valid); Redis down → rate limits degrade to conservative fixed windows; roster correctness unchanged |
| NFR-11 | Observability | Metrics: orgs, members by role/status, invites by state, act sessions active/denied by condition, revocations, complaint freezes, velocity alerts — zero token labels |
