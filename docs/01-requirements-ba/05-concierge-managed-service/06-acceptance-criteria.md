# Concierge Managed Service - Acceptance Criteria (feature 05)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/05-concierge-managed-service/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/05-concierge-managed-service/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Powers drill | An active contract with an assigned operator | The operator does normal setup work, then attempts a direct edit outside agency powers | Normal work flows through Act-as-user + step-up with audit; the backdoor attempt is denied and audited (BR-02) |
| AC-02 | Gate drill | A campaign/post-batch/automation task in `requested` | Dispatch is attempted before approval, then after a recorded approval | Unapproved dispatch is refused at the send/publish endpoint; approved dispatch proceeds; approval records cannot be edited, only superseded (BR-03, BR-04) |
| AC-03 | Pause/exit drill | An active contract with queued scheduled work | Pause is requested and confirmed, then exit is requested and confirmed | Pause halts work with content live; exit delivers the full handoff inventory, severs agency links, and leaves client artifacts intact (BR-10) |
| AC-04 | SLA drill | A task whose window elapses unfulfilled | The clock rolls past the tier window | The miss surfaces with the contract's credit-rule path; no silent remediation occurs (BR-06) |
| AC-05 | No-sale-before-staffing drill | A staffing register missing an operator or owner | Activation is attempted | `422 staffing_incomplete`; contract stays `draft`; nothing is invoiced (BR-08) |
| AC-06 | Boundaries drill | An active contract | Billing-instrument access, a policy-exception promise, client-customer contact, and prohibited content are each attempted | All four are refused with reasons; no billing instrument is touched; refusal is visible on the task (BR-07) |
| AC-07 | Visibility drill | Operator actions across a month | The client opens history and the board | Every action appears with timestamp + actor + work reference (`<= 5 min` freshness); monthly review entry exists (BR-05) |
| AC-08 | Refusal drill | SOW with volumes set | Off-menu and over-volume requests are made | Both refused with referral/SOW-update guidance; nothing executes (BR-01, BR-12) |
