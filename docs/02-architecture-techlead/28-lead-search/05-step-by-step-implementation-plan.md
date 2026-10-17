# Lead Search - Step-by-Step Implementation Plan (feature 28)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/28-lead-search/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V7__lead_workspace.sql`: pg_trgm + GIN indexes, five tables, owner + svc policies, grants, partial CHECKs | Feature 27 T-01 | TL 02 drills 1-6 green |
| T-02 | BE | Search endpoint: one predicate builder (filters closed set), exact-first ranking, 2-char minimum, cursor paging, query history trim + fire-and-forget logging | T-01 | AC-01, AC-02 (count half), AC-05 (search half) |
| T-03 | BE | Detail assembly: timeline merge (events + consent history + provenance + notes/tags); forward-section probe pattern (connected label / not-yet) | T-02 | AC-03 green |
| T-04 | BE | Notes + tags + vocabulary: append-only writes, sanitize, cap-50 guard under concurrency, foreign invisibility | T-01 | AC-04 (event half), AC-08 |
| T-05 | BE | Suppression action (rank-guarded, reason) + re-invite (token mint, pipeline enqueue, caps) + unsubscribe (token verify, shared machine) | T-04 | AC-04, AC-06 |
| T-06 | BE | Export: jobs table usage, worker streaming to the uploads volume, fixed CSV schema, HMAC grants, quota counters (Redis db 4), 7-day sweep + purge participant | T-02 | AC-02 (export half), AC-05 (export half), AC-07, AC-09 |
| T-07 | FE | Contacts workspace: search + recent queries, filter chips + live count, results table, detail timeline, note/tag/suppress/re-invite dialogs, read toggle, export dialog (count → job → grant link), unsubscribe page | T-02..T-06 | BA 04 states render; AC-06 (page half) |
| T-08 | DevOps/QA | 100k-lead perf fixture + EXPLAIN checks; scope leak probes; grant expiry drill; surface review (AC-10); purge drill; container drill (Docker + Podman; V6→V7 replay; rollback); secrets + log greps | T-03..T-07 | G4 + G5 + G7 green |

## Notes

- Build-order gate: forward timeline sections enable when their owners land (32-40 orders, 25 bookings — already shipped read-side contact points, 41-44 automation, monetize preference center); until then the probe reports not-connected and the UI labels it — recorded, never stubbed.
- The export surface ships here per L0 FR4 (DEVIATION FLAGGED, BA 28 Q3); the monetize batch consumes it and must not build a second exporter (surface-review row in AC-10).
- Performance fixture = labeled synthetic data in test databases only; no production seeds.
- Support-seat wiring is 45-47's task; the actor plumbing and the `support` author kind ship now so the seat batch adds only scoping.
- Retention sweep (7-day jobs/files + 20-query trim) runs as a scheduled task in `profile-service` — no new infrastructure.
