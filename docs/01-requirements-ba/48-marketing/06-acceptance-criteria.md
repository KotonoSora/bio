# Marketing Consent and Broadcasts - Acceptance Criteria (feature 48) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-16 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/48-marketing/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero marketing surface found (marketing tables/routes/jobs, preference-center strings, campaign UI); a hit blocks promotion by tooling |
| AC-02 | Classification drill | Sample sends across all channels | Each is classified | Exactly one correct class each; a misclassified send is caught by the review checklist with defined remediation |
| AC-03 | Proof drill | Any address with a consent history | History is exported | Grants, withdrawals, sources, copy versions complete in minutes; a complaint case resolves from history alone |
| AC-04 | Withdrawal drill | A marketing opt-in; one-click out | Next send evaluates | Withdrawal effective before the next send (immediate re-query); no pre-ticked boxes exist in any form (UI + API review) |
| AC-05 | Enforcement drill | A new sender integrated without the eligibility call; suppressed addresses under load | Review + load test run | Integration fails review; suppressed addresses receive nothing from any sender |
| AC-06 | Deletion drill | An account with all classes granted | Deletion runs | All classes withdrawn, address suppressed; only audit-minimal proof rows preserved per policy |
| AC-07 | Lifecycle drill | Hard bounce, three soft bounces, complaint, re-capture fixtures | Transitions run | Hard bounce → immediate suppression; three soft → suppression; complaint terminal; re-capture never loosens a strict state; history complete |
| AC-08 | Import drill | 10k-row CSV with duplicates/invalid/suppressed rows | Import runs | Clean import with exact row-level report; no suppressed resurrection; abuse budgets hold |
| AC-09 | Hygiene drill | Seeded stale addresses; bounce/complaint webhooks | Jobs + webhooks run | Stale addresses park per the 12-month rule; health report matches hand computation; webhook transitions within seconds |
| AC-10 | Export drill | Creator-scoped and platform-scoped exports | Each runs | Consent columns present; scoping respected; audited + rate-limited; suppressed full data blocked without a case |
| AC-11 | Silo drill | Creator form leads present | Platform broadcast eligibility probes | Empty result; creator export stays creator-scoped |
| AC-12 | Eligibility drill | Suppressed/unsubscribed/bounced/complained addresses hand-added to a send list | Send runs | All excluded by query; pre-send count matches delivered-to set exactly |
| AC-13 | Two-step drill | A composed campaign; a mid-batch pause | Send paths exercised | No single action sends; pause halts within seconds; resumed batch never double-sends |
| AC-14 | Stats drill | Seeded deliveries/opens/clicks/bounces/complaints | Events reconcile | Event-to-aggregate exact; bounce auto-suppresses; complaint auto-suppresses + pages |
| AC-15 | Reputation drill | A complaint-rate breach | Sends evaluate | Auto-pause + paging; transactional receipt delivery provably unaffected during the pause |
| AC-16 | Exclusion drill | Segment/schedule/A-B/scheduling attempts | Each is attempted | 404/422, never silent no-ops; UI has no control |
