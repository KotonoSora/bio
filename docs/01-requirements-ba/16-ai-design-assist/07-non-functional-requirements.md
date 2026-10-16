# AI Design Assist - Non-Functional Requirements (feature 16) - GATED

Phase 1 BA · GATED: NFR-01 and NFR-10 are live now (gate enforcement); the rest bind the future build at the spec amendment. No numeric credit caps appear here - they are finance values set with feature 35's plan catalog.

## Gate enforcement (live now)

| # | Requirement | Target |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep must return zero assist surface; violation blocks promotion by tooling (AC-01) |
| NFR-10 | No-stub discipline | No assist table, route, job, chunk, or placeholder UI may exist in any other feature or release before the amendment |

## Cost and abuse (future)

| # | Requirement | Target |
|---|---|---|
| NFR-02 | Budget containment | Every generation draws from the shared plan-owned credit model (`kind='design-assist'`); per-user/day caps enforced as ledger counters, not application memory; exhausted -> `429 assist_quota_exhausted` |
| NFR-03 | Cost attack posture | Challenge + rate limit on all three suggestion endpoints; bursts held by caps with alerting; numeric run-rate target set at the amendment with finance |
| NFR-04 | Render isolation | Generation is async with progress; dashboard render p95 unaffected (never blocks on generation); cancel on navigation |

## Quality, safety, and privacy (future)

| # | Requirement | Target |
|---|---|---|
| NFR-05 | Injection robustness | User content treated as data; system instructions never overridable; outputs constrained to the closed suggestion set (AC-05) |
| NFR-06 | Output validity | 100% of applied suggestions pass the same validators as manual input (XSS/length/theme id/reorder); nothing inexpressible reaches Apply |
| NFR-07 | Human control | Zero auto-apply/auto-publish paths exist; audit actor = user on 100% of applied changes |
| NFR-08 | Privacy minimization | Model input limited to quiz answers, current bio/display-name, and link titles; prompts/outputs logged PII-minimized; no follower evidence, tokens, or cross-user data |
| NFR-09 | Locale and honesty | VN + EN output per creator locale (EN fallback); AI origin disclosed in-dashboard; low confidence flagged with fewer options |

## Reliability and consistency (future)

| # | Requirement | Target |
|---|---|---|
| NFR-11 | Failure isolation | AI outage or quota disables only the assist panel; manual editing availability 100%; no cached suggestion ever displayed as fresh |
| NFR-12 | Suggestion latency | Progress state from request start; server p95 target <= 10 s per generation; client cancellation always honored |
| NFR-13 | Consistency with existing systems | Applied suggestions behave as ordinary edits (publish + purge paths, rollback semantics on reorder conflicts) - no AI fast lane exists |
