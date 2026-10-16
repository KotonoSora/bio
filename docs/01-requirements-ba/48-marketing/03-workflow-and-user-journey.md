# Marketing Consent and Broadcasts - Workflow and User Journey (feature 48) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/48-marketing/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/48-marketing/03-api-contracts-and-payloads.md`.

## W-1 Capture → consent

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Lead | submits a form block / checkout with the marketing box (unchecked default) | consent captured with timestamp, source, copy version; append-only row | BR-02, BR-03 |
| 2 | System | address already known | dedupe by normalized email; re-capture updates history, never loosens a strict state | BR-06, BR-07 |
| 3 | System | strict state present (suppressed/complained) | new capture recorded but eligibility unchanged; explicit re-consent is the only path back | BR-05, BR-06 |

## W-2 Preference change / unsubscribe

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Lead | opens the preference center (signed token, no login) or clicks one-click unsubscribe | per-class toggles with honest consequences; one click opts out | BR-04 |
| 2 | System | save | effective immediately; confirmation receipt of the change itself; history appended | BR-04 |
| 3 | System | next send evaluates | withdrawn class excluded at send time (re-query proof) | BR-05 |

## W-3 Import → hygiene

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Operator | uploads a CSV | validated batch; exact row-level report (imported/duplicate/invalid/suppressed) | BR-07, BR-09 |
| 2 | System | duplicates or stricter states found | upsert never resurrects suppressed; strictest wins | BR-06, BR-07 |
| 3 | System | bounce/complaint webhook fires | state transitions within seconds; suppression syncs to the shared check | BR-08 |
| 4 | System | 12 months pass without engagement | address parks out of broadcast eligibility; history kept | BR-08, Q-03 |

## W-4 Campaign send

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Operator | composes (subject, preheader, sanitized body) and test-sends | test delivery to operator addresses only | BR-10 |
| 2 | Operator | reviews | exact pre-send count of eligible recipients; exclusions applied by query | BR-05, BR-10 |
| 3 | Operator | confirms send | queue worker dispatches with per-recipient idempotency; caps enforced | BR-10, BR-11 |
| 4 | System | complaint-rate breach mid-campaign | sends auto-pause; ops paged; transactional delivery unaffected | BR-11 |
| 5 | Operator | reads stats | delivered/open/click/bounce/complaint reconciled from idempotent events | BR-11 |

## W-5 Complaint → proof

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Recipient | files a complaint | address auto-suppressed; consent-history review case opened | BR-05, BR-08 |
| 2 | Investigator | opens the case | full history (grants, withdrawals, sources, copy versions) exportable in minutes | BR-03, BR-09 |
| 3 | System | case resolved | proof export audit-logged; no re-send possible to the address meanwhile | BR-05, BR-09 |

## W-6 Account deletion

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | User | deletes their account (feature 02 orchestration) | all classes withdrawn; address suppressed; lifecycle ends | BR-04 |
| 2 | System | retention policy applies | only audit-minimal proof rows preserved per policy; re-opt-in later starts clean history | BR-04 |
