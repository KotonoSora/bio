# Advertising (Inventory, Serving, Creator Share, Retargeting) - Exception Handling and Errors (feature 50) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No visitor identity, advertiser secrets, or cross-scope data leak beyond the caller's own scope.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Prohibited-vertical creative submitted | Approval | Rejected at pre-approval; never served | Ops: "Prohibited vertical — see the checklist." |
| Creator category-block conflict | Serving | Excluded from eligibility; never served | None (silent by rule) |
| Empty inventory / decision timeout | Render | Slot collapses ≤ 50 ms; zero layout shift | None |
| Decision service down | Render | Slot collapses; page proceeds | None |
| Below-viewport impression | Measurement | Uncounted | Advertiser sees it in filter transparency |
| Refresh/double-click storms | Measurement | Deduped to one (10-min window) | None |
| Click without landing validation | Measurement | Uncounted | Filter transparency |
| Velocity flood | Measurement | Quarantined from billing aggregates pending review | Advertiser: exclusion counts shown |
| Measurement outage | Pipeline | Events queue with bounded retry; sweep-and-repair; no loss, no doubles | None |
| Takedown action | Serving | Creative paused everywhere ≤ minutes; audited | None |
| Creator opts out mid-flight | Serving | Serving stops immediately; no penalty; earnings stand | "Ads are off for your page." |
| Advertiser A reads advertiser B reports | API | 403/404 via RLS; zero rows | "Not found." |
| Creator attempts flight edits | API | No route exists (by absence) | "Not available." |
| Pro page ad probe | Serving | Never forced — no path exists | None |
| Declined-consent visitor | Serving | Contextual-only or collapsed per config; page never blocked | Disclosure explains the basis |
| Quarantined traffic later cleared | Ledger | Re-enters aggregates, never earnings | None (payout stays dispute-proof) |
| Invalid traffic found post-close | Ledger | Tainted share → `clawed_back` with evidence + notice | "A clawback applies to part of this period." |
| Self-farming detected | Ledger | Tainted earnings voided; ladder warn → restrict → opt-in revocation | Notice per ladder |
| Tax fields incomplete | Payout | Line blocked from batch with fix-it notice; earnings intact | "Complete tax details to receive payout." |
| Segment spans creators | System | Impossible — per-creator membership only | None |
| Off-platform export attempted | API | 404/422 (surface absent, never stubbed) | "Not available." |
| Marketing variant to transactional-only recipient | Reach | Refused by eligibility with reason logged; lifecycle variant only | None (server-side arbitration) |
| Frequency cap reached | Reach | Suppressed across surfaces; count visible in report | None |
| Lift claimed without control | Reporting | Impossible — no lift without control slice | Report shows control baseline |
