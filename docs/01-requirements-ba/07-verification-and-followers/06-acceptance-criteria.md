# Verification Badge and Follower Display - Acceptance Criteria (feature 07)

Phase 1 BA · BDD form. Every AC runs in the drill suite (task T-12 of `docs/02-architecture-techlead/07-verification-and-followers/05-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Fake-badge probe | Bio/display-name containing a badge emoji or inline SVG markup | The public page and the dashboard preview render | The payload appears as plain text, never as the platform badge component |
| AC-02 | State machine | A profile walking `none -> pending -> verified -> rejected -> pending -> revoked` | Each transition happens | Badge visible only in `verified`; timeline copy exact per state; no illegal transition persists (service rejects, DB alphabet holds) |
| AC-03 | Rename re-review | Verified profile | Rename completes | Status becomes `pending` atomically with the rename; badge hidden until ops re-approves |
| AC-04 | Revocation SLA | Verified profile | Ops revokes with reason | Badge gone publicly within 2 min; audit row has actor + reason; evidence retained per retention |
| AC-05 | Self-report versioning | Existing self-reported totals | Count edited twice | Two history rows persist; total + breakdown recompute; labels show `self-reported` |
| AC-06 | Jump flag | Previous count 20,000 | Count raised to 200,000 | `follower_jump_review` recorded; count still stored and displayed; amber notice shown to owner |
| AC-07 | Refresh isolation | Production-shaped public traffic | Scheduled refresh runs across profiles | Public resolve p95 unchanged; no fetch happens during visitor load (queue/worker only) |
| AC-08 | Outage keeps last-good | Auto total 120,000 from one network | Source network unreachable during refresh | Page renders the last-good total with `stale`; no error surface; next successful refresh clears `stale` |
| AC-09 | RBAC and IDOR | Creator A and verified profile B | A writes verify/socials/refresh against B; direct DB as `app_role` with A's id | `403` from API; zero B rows from DB (RLS); no audit writes beyond A's own |
| AC-10 | Injection and scheme | Handle/URL/count fields with `javascript:` URIs, markup, negative counts | Each saved | Rejected with row-level codes; anchors render `rel="noopener ugc"`; CSP holds |
| AC-11 | Ops audit | Any approve/reject/revoke | Decision recorded | Append-only audit row with actor + reason; visible excerpt matches; retained 24 months |
| AC-12 | Near-match flag | Verified username `alice` exists | Claim `alice1` is checked | Advisory flag enters the ops queue; the claim itself is not auto-blocked |
| AC-13 | Contracts and DTO extension | Public resolve payload and error responses | Feature 10 reads badge/total; OpenAPI diff computed | Additive fields (`verificationStatus`, `followerTotal`, `followerBreakdown`, `followerStale`) present; diff additive-only; every code exact |
