# Agency Roster - UI Data Dictionary (feature 45)

Phase 1 BA · Surfaces: Users tab, Managers tab, org switcher, invite dialogs, client link flows, Act-as-user presence/banner + history, client revoke page. Tabs are views; roles are the truth.

## Roster surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `orgSwitcher` | Dropdown | Yes | Lists memberships; sets `active_org_id` | Roster views always state the active org |
| `usersTab` | Table | On load | Members + clients: name, username, role chip, status (`active/invited/suspended/removed`) | Filter role/status; sorted owner-first |
| `managersTab` | Table + actions | On load | Role assignment surface; owner row immutable to managers | Matrix-driven action visibility (server re-checks) |
| `inviteDialog` | Dialog | Yes to act | Username + role (manager/member/client) + client scope; staff invites check subscription | Unknown username → invite-to-register note |
| `inviteState` | Chip | Yes | `pending/accepted/declined/revoked/expired` | Expiry countdown; re-invite rate-limited |
| `roleAction` | Menu | Per matrix | Owner/manager options only; never owner-role by manager | Server 403 backstop with audit-flag |
| `suspendAction` | Button + confirm | Yes to act | Freezes powers instantly | Reversible (`reinstate`) |
| `removeAction` | Button + confirm | Yes to act | Names the consequence (reporting + act-as lost) | Audited |
| `clientLinkAction` | Button | Yes to act | Staff-initiated → `pending` until owner accepts | Ownership never moves (copy states it) |
| `unlinkAction` | Button + confirm | Either side | Immediate sever of reporting + act-as | Audited |
| `assignAction` | Member picker + save | Owner/manager | Assigns staff to linked profiles (`client_assignments`) | Unassigned members hold no powers; audited |
| `orgDeleteAction` | Typed-confirm dialog | Yes to act | Typing the org name required; grace-window note | Reversible within 30 days; client profiles untouched |

## Act-as-user surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `actStartAction` | Button | On assigned profiles | Step-up prompt first (fresh ≤ 10 min) | Gates checked server-side; refusal names the condition |
| `presenceBanner` | Banner (client side) | While active | "Staff is editing your profile" | Visible to the client during sessions |
| `actBanner` | Banner (staff side) | While active | Names actor, org, profile; countdown to TTL/hard stop | Distinct visual class from normal sessions |
| `actEndAction` | Button | Yes while active | Ends session with reason `user` | Token dead immediately |
| `actHistory` | Table | On load | Who, when, what changed (before/after links) | Client + owner + support views agree |
| `revokeAgencyAction` | Button + confirm | Client, any time | One click; kills live sessions + unlinks | Copy: "staff lose access immediately" |
| `complaintAction` | Button + form | Client | Files complaint → implicated staffer frozen pending review | Read surfaces continue |

## Validation and behavior notes

- Every action button is a view over the server matrix; hidden buttons never substitute for server checks (drill-verified).
- Step-up prompts use the existing account-service step-up flow; stale step-up is refused with `step_up_required`.
- Presence lock copy: "Another staff member is editing this profile — try again when they finish."
- Empty states: no orgs → create CTA (self-serve); no clients → invite CTA; no act history → "No staff edits yet".
