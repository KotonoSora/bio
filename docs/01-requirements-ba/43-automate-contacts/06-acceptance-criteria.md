# Automate Contacts - Acceptance Criteria (feature 43)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/43-automate-contacts/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Boundary drill | A contact captured via a keyword automation (transactional class) | A marketing-class send is attempted through the eligibility check | Refused (`marketing_consent_required`); transactional sends still work; no consent class ever upgrades |
| AC-02 | Suppression drill | Active contacts; all three removal mechanisms available | `OPTOUT`, removal-center, and platform-button paths each run | Each lands `suppressed` + reason + audit; eligibility excludes everywhere immediately; repeat visits are idempotent |
| AC-03 | Export drill | A filtered contact set | CSV export + leads handoff run | CSV is owner-scoped, audited, expiring ≤ 24 h; handoff uses `POST /internal/v1/leads` with automation source + actual consent class; counts match the filter |
| AC-04 | Reconciliation drill | Duplicates, platform-blocked rows, and a 12-month-idle row | Hygiene jobs run | One row per identity (strictest consent kept); blocked pruned; stale parked; a fresh comment reactivates with new provenance |
| AC-05 | IDOR drill | Creator B refs against creator A contact/export/handoff ids; ops raw-list attempt | Each runs | 403/404 with zero rows; ops route does not exist; removal-center tokens are per-contact and expiring |
| AC-06 | Quota drill | Free 100/year and pro 100k/month fixtures; a repeat recipient | Sends cross the boundary | Unique recipient counts once; over-quota holds as `quota_held` (44's surface); gate, meter, and reconciliation agree (one function) |
| AC-07 | Capture drill | A matched comment whose DM is accepted vs one whose send fails | Capture runs | Accepted → one row (idempotent per automation+comment); failed send → no row; repeat comments append events, never duplicate |
