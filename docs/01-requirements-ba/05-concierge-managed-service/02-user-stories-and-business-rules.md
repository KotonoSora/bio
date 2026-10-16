# Concierge Managed Service - User Stories and Business Rules (feature 05)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/05-concierge-managed-service/`. Scope: `docs/01-requirements-ba/05-concierge-managed-service/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Concierge client | approve a setup plan and receive scheduled work plus reports with a clean handoff on exit | I buy outcomes with visible work logs under one SLA | Low |
| US-02 | Concierge operator | execute setup/campaign/post/email/automation/report tasks inside scoped agency powers with every action logged | the work is visible and auditable | Low |
| US-03 | Agency owner | staff, review quality, and pause or offboard clients | the service stays staffed and quality-controlled | Low |
| US-04 | Concierge client | see the credit path when a window is missed | SLA failures are disclosed, never silent | Low |

## Business rules

- **BR-01 Closed menu.** The SOW selects from the fixed menu (profile/page setup, broadcast campaigns, publish scheduling, lifecycle/marketing emails, IG automations, periodic reporting) with volumes; off-menu asks are refused with a referral path; the SOW states this before payment.
- **BR-02 No backdoor, ever.** Operators act only through agency-gated endpoints (assignment + Act-as-user + step-up + audit from features 45-47); direct edits of client content outside those powers are impossible and attempted ones are denied and audited.
- **BR-03 Approval gates (endpoint-verified).** Setup plan approved before build; each campaign/post-batch/automation approved per item before send/publish; approval records are immutable. Work enqueued by concierge carries `origin=concierge` + `approvalRef`; dispatch endpoints refuse concierge-origin work without a valid approval - operator discipline is never the gate.
- **BR-04 Approval integrity.** Approve actions are client-authenticated (dashboard) or signed-email confirmed; scope (what exactly) is recorded; an approval can be superseded only by a new recorded approval, never edited; unapproved or revoked-approval work cannot dispatch.
- **BR-05 Work log visibility.** Every operator action appears in the client's history; the shared task board shows requested -> approved -> in progress -> done/refused with timestamps and work references.
- **BR-06 SLA clock.** The clock runs on task timestamps from `requested` (response window) and from `approved` (completion window) per the contract's SLA tier; a miss surfaces the credit-rule path per contract - service credits only, never silent remediation.
- **BR-07 Boundaries (hard).** Operators never touch billing instruments beyond the SOW retainer; never promise platform-policy exceptions (spam/review outcomes, badges, deliverability); never contact the client's customers outside approved surfaces; the prohibited-content bar matches broadcasts/ads - refuse the task, keep the client.
- **BR-08 Staffing presence gate.** Activation is refused unless the staffing register holds >= 1 active SOW template, >= 1 active operator, and >= 1 agency owner; the check is a presence query at the activation endpoint, not copy in a runbook.
- **BR-09 Retainer billing only.** Concierge is a service product invoiced through the standard receipt pipeline (features 32-40); no subscription seats, no money math in this feature; activation/state events feed invoicing.
- **BR-10 Pause and exit (dual-confirmed).** Pause halts work (access powers suspend, content stays live); exit runs the handoff inventory: credentials inventory, content ownership confirmation, scheduled-work transfer-or-cancel at client choice, final report; agency links sever on exit - no hostage; the client's pages, lists, and history remain intact.
- **BR-11 Offboarding data.** Post-exit, contracts/tasks/approvals are retained per audit policy (minimized: ids, states, timestamps; no client content); work references remain valid links to the client's own artifacts.
- **BR-12 Scope discipline.** Tasks outside the SOW's picked items/volumes are refused with referral (`refused` state + reason); volume overage requires a recorded SOW update before execution.

## Constraints carried

- Act-as-user, step-up, and audit contracts are consumed from features 01/45-47; no second mechanism is invented.
- Events follow the platform rule (after-commit, at-least-once, idempotent consumers; lost events self-heal via reads).
- No pricing, SLA, or credit numbers appear here; they are staffing/finance config per Q-01.
