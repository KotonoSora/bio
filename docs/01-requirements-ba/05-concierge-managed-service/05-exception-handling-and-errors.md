# Concierge Managed Service - Exception Handling and Errors (feature 05)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/05-concierge-managed-service/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Activation without staffing | API | `422 staffing_incomplete`; stays `draft`; no sale can proceed | Internal-facing: "Staffing register lacks {template/operator/owner} - activation refused." |
| Off-menu item requested | API | `422 task_off_menu`; refusal recorded with referral | "This isn't in your plan - here's what we can do instead:" + referral |
| Over-volume execution attempt | API | Refused; SOW update required first | "You've reached this item's monthly volume. We can adjust your plan." |
| Unapproved concierge-origin dispatch | Dispatch endpoint | Refused at the endpoint (not operator discipline); audited | Operator-facing: "This work needs a recorded approval before it can be sent." |
| Approval on an inactive contract | API | `409 transition_conflict`; nothing recorded | "This contract is paused or exited - approvals are closed." |
| Signed-email confirmation expired/replayed | API | Rejected; fresh signed link required | "This confirmation link is no longer valid - a new one has been sent." |
| Pause/exit awaiting counterpart | API | Pending state visible to both; no error | Banner: "Waiting for the other party to confirm." |
| Unassigned operator acts on a task | API | `403 task_forbidden`; audited | Operator-facing: "You aren't assigned to this contract." |
| Removal of the last agency owner | API | `409 last_owner_forbidden`; no change | Internal-facing: "Assign another owner before removing the last one." |
| Missed SLA window | System | Surfaced on the board with the contract's credit path; no silent remediation | "We missed the {response/completion} window - your contract's credit path applies." |
| Boundary request (billing instrument, policy promise, client-customer contact, prohibited content) | API/Process | Refused; reason recorded; referral where applicable | "That's outside what we can do - here's the boundary and a referral path." |
| Backdoor attempt (direct edit outside agency powers) | API | Denied + audited; no state change | Operator-facing: "Use the assigned workflow - direct edits are disabled." |
| Client cancels a pending pause/exit | API | `204`; pending cleared; nothing changed | "Request canceled." |
| Invoicing consumer down at activation | System | Activation stands; event retried; invoice reconcilable from the event | Client sees activation; billing catches up (no blocking) |
| Exit with scheduled work pending | API | Transfer-or-cancel decision required in the handoff dialog | "Choose what happens to your scheduled work before we close out." |
