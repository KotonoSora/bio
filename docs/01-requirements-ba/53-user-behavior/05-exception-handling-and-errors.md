# User Behavior (Journeys, Conversion, Retention) - Exception Handling and Errors (feature 53) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No buyer PII, cross-segment joins, or behavioral leakage beyond the caller's scope.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Declined-consent traffic | Pipeline | No journeys form; only unattributed aggregate-safe counts | None |
| Logged-out browsing | Stitching | Stays unattributed until login/checkout identification | None |
| Fingerprint/device-graph join attempted | Review gate | No such code path exists; review failure | Internal finding |
| Sub-floor segment read | API | Collapsed into "other" in every readout (floor 10) | "Segment too small to display." |
| Deletion request | Pipeline | Raw trails cleared within the documented window; aggregates stand | None (normal deletion flow) |
| Stale open trail | Sweep | Finalized as marked-inferred `abandoned`/`lapsed` within the window | Badge: "inferred" |
| Orphan journey past retention | System | Impossible by sweep construction; drill asserts zero orphans | Internal alert if ever observed |
| Coupon code leakage probe | System | Only outcome class stored/read — tried codes do not exist in the data | None |
| Raw gateway text probe | System | Never leaves the ops boundary; only mapped message analyzed | None |
| Real-time intervention attempted (popup, dynamic price) | Review gate | Forbidden by rule; release blocked | Internal finding |
| Behavioral data read by gating/pricing/fraud code | Review gate | Dependency + grep check fails the release | Internal finding |
| Causal claim without experiment | Review checklist | Causal language blocked; correlation label enforced | Analyst: "Label as correlation or link the experiment." |
| Personalized pricing attempt from sensitivity findings | Review gate | Blocked by 52's fairness floor; aggregate experiments only | Internal finding |
| Cancel flow with required reasons | UX review | One-click skip mandatory; dark-pattern maze fails review | "Cancel completes regardless." |
| Save offer without registration | Review gate | Only registered experiments may run save offers | "Register the experiment first." |
| Win-back to consent-declined buyer | Send path | Excluded by 48's classes at send time | None (server-side arbitration) |
| Off-platform behavioral export attempted | API | No route or surface exists | "Not available." |
| Non-ops role on behavior readouts | API | 403; RLS denies | "Not available." |
| Earner probes behavior data | API | 403 — scoped dashboards only | "Not found." |
| Risk band read by buyer-facing surface | Review gate | Internal-only; any buyer-visible health view requires a future amendment | Internal finding |
