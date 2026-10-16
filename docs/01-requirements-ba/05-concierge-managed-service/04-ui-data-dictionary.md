# Concierge Managed Service - UI Data Dictionary (feature 05)

Phase 1 BA · Client-facing surfaces for the service; operator surfaces live in the agency tooling (features 45-47). Contracts: `docs/02-architecture-techlead/05-concierge-managed-service/`. Rules: `docs/01-requirements-ba/05-concierge-managed-service/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Contract card | Panel | Auto | Status chip (draft/active/paused/exited); SLA tier label; activation/exit dates; no pricing math here (invoicing elsewhere) | Rendered per contract state; hidden until a draft exists |
| SOW panel | Read-only list | Auto | Menu items + volumes exactly as recorded; version noted | Lists the recorded SOW |
| Setup-plan approval | Button + dialog | Yes first | Approve records scope + channel; confirmation is immutable once committed | Disabled outside `draft`, or after recorded approval |
| Task board | Table | Auto | Rows: kind, status (requested/approved/in progress/done/refused), requested/approved/completed timestamps, work references | Sorted by due/requested; refused rows show reason + referral |
| Request item form | Form | No | Kind limited to SOW-picked items; notes optional; over-volume attempts blocked with SOW-update guidance | Hidden when no active contract |
| Approve item dialog | Dialog | Yes to approve | Approve once; scope field mandatory; recorded channel shown; no edit path exists after commit | Re-approve returns the existing record |
| Approval history | Read-only rows | Auto | Append-only list: scope, channel, approver, timestamp; no controls | Chronological |
| Work log rows | Read-only list | Auto | Every operator action with timestamp + work reference; freshness `<= 5 min`; no content copied, links only | Newest first |
| SLA status row | Chip + text | Auto | Shows the tier's windows and any missed window with the credit path; amount-free | "Within window" default state |
| Pause request dialog | Dialog | Yes to pause | Explains: work halts on confirm, content stays live; pending state visible to both parties | Confirm is default focus |
| Exit request dialog | Dialog | Yes to exit | States the handoff inventory; scheduled-work transfer-or-cancel choice captured at confirm | Confirm requires typed acknowledgment of handoff |
| Handoff inventory view | Checklist panel | Auto | Credentials inventory, ownership confirmation, scheduled-work decision, final report status; each item timestamped | Shown once exit is confirmed |
| Refusal notice | Banner | Auto | Reason + referral for off-menu or off-boundary asks; no blame copy | Appears on the refused task row |
| Team/operator note | Text row | No | Names visible to the client; actions still attributable per-actor in history | Optional per task |

Copy discipline: no promised outcomes, no SLA/credit numbers, no pricing - all such values come from contract config (Q-01) and features 32-40 respectively.
